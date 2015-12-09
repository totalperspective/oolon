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

(defn run-rule [db tables rule]
  (let [{:keys [head-form head body]} rule
        table (get tables (first head-form))
        lvars (into [] (d/lvars head))]
    (->> {:find lvars :where body}
         (db/q db)
         (map (partial zipmap lvars))
         (map (partial d/bind-form head))
         (map (partial t/add-id table)))))

(defn run-rules!
  ([db sys]
   (run-rules! db sys #{} 999))
  ([db sys tx-acc max]
   (when (pos? max)
     (let [rules (rules sys)
           tx-data (mapcat (partial run-rule db (tables sys)) rules)
           tx-acc (into tx-acc tx-data)
           db (db/with db tx-data)
           tx-data (get-in db [:last-tx :tx-data])]
       (if (empty? tx-data)
         tx-acc
         (recur db sys tx-acc (dec max)))))))

(defn run! [sys]
  (when (started? sys)
    (let [{:keys [facts name conn]} sys
          {:keys [assertions]} facts
          {:keys [tx-data]} @(db/transact conn assertions)
          sys (assoc sys :facts empty-facts)]
      (if (empty? tx-data)
        sys
        (let [db (db/db conn)
              [[_ ts-attrs]] (table->facts sys system-table)
              timestep (:timestep ts-attrs)
              next-t (t/record system-table {:name name :timestep (inc timestep)})
              tx (into [next-t] (run-rules! db sys))]
          @(db/transact conn tx)
          sys)))))
