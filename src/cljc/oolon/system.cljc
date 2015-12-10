(ns oolon.system
  (:refer-clojure :exclude [run!])
  (:require [oolon.table :as t]
            [oolon.datalog :as d]
            [oolon.module :as m]
            [oolon.schema :as s]
            [oolon.db :as db]))

(def empty-facts
  {:assertions #{}
   :retractions #{}})

(defn system [name conn modules]
  {:pre [(db/conn? conn)]}
  {:name name
   :conn conn
   :modules modules
   :facts empty-facts})

(defn started? [sys]
  (if sys
    (let [{:keys [conn modules name]} sys
          db (db/db conn)
          time (ffirst (db/q db
                             {:find '[?t]
                              :where
                              (d/rel->eavt :system
                                           {:name name
                                            :timestep :?t})}))]
      (pos? (or time 0)))
    false))

(def system-table (t/table :system {:name :keyword} {:timestep :long}))

(defn tables [sys]
  (let [{:keys [modules]} sys]
    (into {:system system-table} (map :state modules))))

(defn rules [sys]
  (let [{:keys [modules]} sys]
    (mapcat :rules modules)))

(defn fact->record [sys fact]
  (let [tables (tables sys)
        [name attrs] fact
        table (get tables name)]
    (when table
      (t/record table attrs))))

(defn start! [sys]
  (if (started? sys)
    sys
    (let [{:keys [conn modules name]} sys
          db (db/db conn)
          tables (vals (tables sys))
          schema-tx (mapcat s/schema tables)
          sys-ts (t/record system-table {:name name :timestep 1})]
      (db/add-attributes conn schema-tx)
      (db/transact conn [sys-ts])
      sys)))

(defn +fact [sys fact]
  (when (started? sys)
    (let [rec (fact->record sys fact)]
      (when rec
        (update-in sys [:facts :assertions] conj rec)))))

(defn table->facts [sys table]
  (let [{:keys [conn modules name]} sys
        db (db/db conn)
        rel (t/rel table)
        eavt (apply d/rel->eavt rel)
        lvars (into [] (d/lvars eavt))]
    (->> {:find lvars :where eavt}
         (db/q db)
         (map (partial zipmap lvars))
         (map (partial d/bind-form rel)))))

(defn state [sys]
  (when (started? sys)
    (let [tables (vals (tables sys))]
      (->> tables
           (mapcat (partial table->facts sys))
           (into #{})))))

(defn out [sys]
  (when (started? sys)
    (get-in sys [:facts :out])))

(defn run-rule [db tables rule]
  (let [{:keys [head-form head body]} rule
        table (get tables (first head-form))
        defer (:deferred rule)
        channel? (:channel table)
        lvars (into [] (d/lvars head))]
    [defer (->> {:find lvars :where body}
                (db/q db)
                (map (partial zipmap lvars))
                (map (fn [fact]
                       (if channel?
                         (d/bind-form head-form fact)
                         (t/add-id table (d/bind-form head fact))))))]))

(defn run-rules!
  ([db sys]
   (run-rules! db sys #{} #{} 999))
  ([db sys tx-acc deferred max]
   (when (pos? max)
     (let [rules (rules sys)
           txes (map (partial run-rule db (tables sys)) rules)
           [tx-data defer] (reduce (fn [[now next] [defer? tx]]
                                     (if defer?
                                       [now (into next tx)]
                                       [(into now tx) next]))
                                   [[] []]
                                   txes)
           tx-acc (into tx-acc tx-data)
           deferred (into deferred defer)
           db (db/with db tx-data)
           tx-data (get-in db [:last-tx :tx-data])]
       (if (empty? tx-data)
         [tx-acc deferred]
         (recur db sys tx-acc deferred (dec max)))))))

(defn clean-scratch! [sys]
  (let [{:keys [conn]} sys
        db (db/db conn)
        tx (mapcat (fn [{:keys [name]}]
                        (let [[[e a v]] (d/rel->eavt name {})]
                          (map (fn [[e v]]
                                 [:db/retract e a v])
                               (db/q db {:find [e v]
                                         :where [[e a v]]}))))
                      (filter :scratch (vals (tables sys))))]
    @(db/transact conn tx)))

(defn tick! [sys]
  (when (started? sys)
    (clean-scratch! sys)
    (let [{:keys [facts name conn]} sys
          {:keys [assertions]} facts
          {:keys [tx-data]} @(db/transact conn (seq assertions))
          sys (assoc sys :facts empty-facts)]
      (if (empty? tx-data)
        sys
        (let [db (db/db conn)
              [[_ ts-attrs]] (table->facts sys system-table)
              timestep (:timestep ts-attrs)
              next-t (t/record system-table {:name name :timestep (inc timestep)})
              [tx-now tx-next] (run-rules! db sys)
              tx (into [next-t] tx-now)
              assertions (->> tx-next
                              (filter map?)
                              (into #{}))
              chan-out (->> tx-next
                            (remove map?)
                            (into #{}))]
          @(db/transact conn tx)
          (-> sys
              (assoc-in [:facts :assertions] assertions)
              (assoc-in [:facts :out] chan-out)))))))
