(ns oolon.agent
  (:refer-clojure :exclude [agent])
  (:require [oolon.table :as t]
            [oolon.datalog :as d]
            [oolon.module :as m]
            [oolon.schema :as s]
            [oolon.db :as db]))

(def empty-facts
  {:assertions #{}
   :retractions #{}})

(def init-schema
  [{:db/ident :oolon.lineage/child
    :db/valueType :db.type/ref}
   {:db/ident :oolon.lineage/parent
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}])

(defn all-modules [modules]
  (->> modules
       (mapcat m/imports)
       (into modules)
       distinct
       vec))

(defn agent [name conn modules]
  {:pre [(db/conn? conn)]}
  {:name name
   :conn conn
   :modules (all-modules modules)
   :facts empty-facts})

(defn started? [sys]
  (if sys
    (let [{:keys [conn modules name]} sys
          db (db/db conn)
          time (ffirst (db/q db
                             {:find '[?t]
                              :where
                              (d/rel->eavt :agent
                                           {:name name
                                            :timestep :?t})}))]
      (pos? (or time 0)))
    false))

(def system-table (t/table :agent {:name :keyword} {:timestep :long}))

(defn tables [sys]
  (let [{:keys [modules]} sys]
    (into {:agent system-table} (map :state modules))))

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
          schema-tx (into init-schema (mapcat s/schema tables))
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

(defn lineage [dep-lvars fact]
  (let [dep-lvar? (apply hash-set dep-lvars)
        lineage (->> fact
                     (keep (fn [[k v]]
                                   (when (dep-lvar? k)
                                     v)))
                     distinct)]
    lineage))

(defn run-rule [db tables rule]
  (let [{:keys [head-form head body dep-lvars]} rule
        table (get tables (first head-form))
        defer (:deferred rule)
        channel? (:channel table)
        lvars (into dep-lvars (d/lvars head))
        q {:find lvars :where body}]
    [defer (->> q
                (db/q db)
                (map (partial zipmap lvars))
                distinct
                (map (fn [fact]
                       (let [lineage (lineage dep-lvars fact)]
                         (if channel?
                           (let [rel (d/bind-form head-form fact)]
                             (if (:loopback table)
                               (with-meta rel {:loopback true
                                               :lineage lineage})
                               (with-meta rel {:lineage lineage})))
                           (with-meta
                             (t/add-id table (d/bind-form head fact))
                             {:lineage lineage}))))))]))

(defn depends? [rules rule]
  (some (partial d/depends-on? rule) rules))

(defn stratify [rules]
  (let [deps (map (fn [rule]
                    [rule (depends? rules rule)])
                  rules)
        no-deps (map first (remove second deps))
        has-deps (map first (filter second deps))]
    (if (empty? has-deps)
      [rules]
      (let [strata (stratify has-deps)]
        (into [no-deps] strata)))))

(defn run-rules!
  ([db sys]
   (let [rules (rules sys)
         strata (stratify rules)]
     (run-rules! db sys #{} #{} 999 strata)))
  ([db sys tx-acc deferred max strata]
   (when (pos? max)
     (if (empty? strata)
       [tx-acc deferred]
       (let [rules (first strata)
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
           (recur db sys tx-acc deferred (dec max) (rest strata))
           (recur db sys tx-acc deferred (dec max) strata)))))))

(defn clean-type! [sys type]
  (let [{:keys [conn]} sys
        db (db/db conn)
        tx (mapcat (fn [{:keys [name]}]
                        (let [[[e a v]] (d/rel->eavt name {})]
                          (map (fn [[e v]]
                                 [:db/retract e a v])
                               (db/q db {:find [e v]
                                         :where [[e a v]]}))))
                      (filter type (vals (tables sys))))]
    @(db/transact conn tx)))

(defn clean-scratch! [sys]
  (clean-type! sys :scratch))

(defn clean-channel! [sys]
  (clean-type! sys :channel))

(defn loopback? [rel]
  (let [m (meta rel)]
    (:loopback m)))

(defn get-fact-id [fact]
  (->> fact
       (filter (fn [[k v]]
                 (= "$id" (name k))))
       first
       val))

(defn apply-lineage [conn tx]
  (mapcat (fn [fact]
            (if-let [lineage (-> fact meta :lineage)]
              (let [id (get-fact-id fact)
                    lineage-entity {:db/id (db/tempid conn :db.part/user)
                                    :oolon.lineage/child id
                                    :oolon.lineage/parent lineage}]
                [fact lineage-entity])
              [fact]))
          tx))

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
              tx (into [next-t] (apply-lineage conn tx-now))
              assertions (->> tx-next
                              (filter map?)
                              (apply-lineage conn)
                              (into #{}))
              chan-out (->> tx-next
                            (remove map?)
                            (remove loopback?)
                            (into #{}))
              assertions (->> tx-next
                              (remove map?)
                              (filter loopback?)
                              (map (partial fact->record sys))
                              (into assertions))]
          @(db/transact conn tx)
          (clean-channel! sys)
          (-> sys
              (assoc-in [:facts :assertions] assertions)
              (assoc-in [:facts :out] chan-out)))))))
