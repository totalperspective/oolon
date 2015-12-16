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
    :db/index true
    :db/valueType :db.type/ref
    :db.install/_attribute :db.part/db}
   {:db/ident :oolon.lineage/parent
    :db/index true
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db.install/_attribute :db.part/db}])

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

(defn -fact [sys fact]
  (when (started? sys)
    (let [rec (fact->record sys fact)]
      (when rec
        (update-in sys [:facts :retractions] conj rec)))))

(defn table->facts [sys table]
  (let [{:keys [conn modules name]} sys
        db (db/db conn)
        rel (t/rel table)
        eavt (apply d/rel->eavt rel)
        lvars (into [] (d/lvars eavt))
        q {:find lvars :where eavt}]
    (->> q
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

(defn run-rule [db tables id-map rule]
  (let [{:keys [head-form head body dep-lvars]} rule
        table (get tables (first head-form))
        defer (:deferred rule)
        mta (select-keys rule [:assert :retract])
        channel? (:channel table)
        lvars (into dep-lvars (d/lvars head))
        q {:find lvars :where body}]
    [defer (->> q
                (db/q db)
                (map (partial zipmap lvars))
                distinct
                (map (fn [fact]
                       (let [lineage (map (fn [eid]
                                            (if-let [tempid (get id-map eid)]
                                              tempid
                                              eid))
                                          (lineage dep-lvars fact))]
                         (if channel?
                           (let [rel (d/bind-form head-form fact)]
                             (if (:loopback table)
                               (with-meta rel {:loopback true
                                               :lineage #{lineage}})
                               (with-meta rel {:lineage #{lineage}})))
                           (with-meta
                             (t/add-id table (d/bind-form head fact))
                             (assoc mta :lineage #{lineage})))))))]))

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
     (run-rules! db sys #{} #{} 999 strata {})))
  ([db sys tx-acc deferred max strata id-map]
   (when (pos? max)
     (if (empty? strata)
       [tx-acc deferred]
       (let [rules (first strata)
             txes (map (partial run-rule db (tables sys) id-map) rules)
             [tx-data defer] (reduce (fn [[now next] [defer? tx]]
                                       (if defer?
                                         [now (into next tx)]
                                         [(into now tx) next]))
                                     [[] []]
                                     txes)
             tx-meta (into {}
                           (keep (fn [entity]
                                   (when (tx-acc entity)
                                     [entity (meta entity)]))
                                 tx-data))
             tx-acc (into #{} (map (fn [entity]
                                     (if-let [new (tx-meta entity)]
                                       (let [m (meta entity)
                                             lineage (:lineage new)
                                             new-m (update m :lineage into lineage)]
                                         (with-meta entity new-m))
                                       entity)))
                          (into tx-acc tx-data))
             deferred (into deferred defer)
             db (db/with db tx-data)
             last-tx (db/last-tx db)
             tx-data (:tx-data last-tx)
             id-map (->> tx-data
                         (keep (fn [[e a v]]
                                 (when (= "$id" (name a))
                                   [e v])))
                         (into id-map))]
         (if (empty? tx-data)
           (recur db sys tx-acc deferred (dec max) (rest strata) id-map)
           (recur db sys tx-acc deferred (dec max) strata id-map)))))))

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

(defn assert? [rel]
  (let [m (meta rel)]
    (:assert m)))

(defn retract? [rel]
  (let [m (meta rel)]
    (:retract m)))

(defn apply-lineage [conn tx]
  (let [tx (map (fn [e]
                  (assoc e :db/id (db/tempid conn :db.part/user)))
                tx)
        get-fid (fn [e]
                  (first (keep (fn [[k v]]
                                 (when (= "$id" (name k))
                                   v))
                               e)))
        id-map (->> tx
                    (map (juxt get-fid :db/id))
                    (into {}))]
    (mapcat (fn [fact]
              (if-let [lineage (-> fact meta :lineage)]
                (let [lineage-entities (map (fn [lineage]
                                              (let [id (:db/id fact)
                                                    lineage (map (fn [id]
                                                                   (if-let [tempid (id-map id)]
                                                                     tempid
                                                                     id))
                                                                 lineage)
                                                    lineage-entity {:db/id (db/tempid conn :db.part/user)
                                                                    :oolon.lineage/child id
                                                                    :oolon.lineage/parent lineage}]
                                                lineage-entity))
                                            lineage)]
                  (conj lineage-entities fact))
                [fact]))
            tx)))

(defn retract->tx [e]
  (first (keep (fn [[k v]]
                 (when (= "$id" (name k))
                   [:db/retract [k v] k v]))
               e)))

(defn retract-tx [db retractions]
  (if (empty? retractions)
    []
    (loop [db db
           retractions retractions
           tx (keep retract->tx retractions)]
      (let [next-db (db/with db tx)
            {:keys [tx-data]} (db/last-tx next-db)
            eids (map first tx-data)]
        (if (empty? eids)
          (keep retract->tx retractions)
          (let [next-eids (db/q next-db
                                '[:find ?c
                                  :in $ [?p ...]
                                  :where
                                  [?l :oolon.lineage/parent ?p]
                                  [?l :oolon.lineage/child ?c]]
                                eids)
                next-facts (map (fn [[eid]]
                                  (db/pull next-db '[*] eid))
                                next-eids)
                tx (keep retract->tx next-facts)
                retractions (into retractions next-facts)]
            (recur next-db retractions tx)))))))

(defn tick! [sys]
  (when (started? sys)
    (clean-scratch! sys)
    (let [{:keys [facts name conn]} sys
          sys (assoc sys :facts empty-facts)
          {:keys [assertions retractions]} facts
          retract-tx (retract-tx (db/db conn) retractions)
          {:keys [tx-data]} @(db/transact conn retract-tx)
          tx-data (into tx-data (:tx-data @(db/transact conn (seq assertions))))]
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
                              (filter assert?)
                              (apply-lineage conn)
                              (into #{}))
              retractions (->> tx-next
                               (filter map?)
                               (filter retract?)
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
              (assoc-in [:facts :retractions] retractions)
              (assoc-in [:facts :out] chan-out)))))))

(defn fact-parents [db child]
  (let [parents (->> child
                     (db/q db '[:find ?p
                                :in $ ?c
                                :where
                                [?l :oolon.lineage/child ?c]
                                [?l :oolon.lineage/parent ?p]])
                     (map first)
                     (into #{}))]
    (when-not (empty? parents)
      (into parents (mapcat (partial fact-parents db)
                            parents)))))

(defn entity->fact [db eid]
  (->> eid
       (db/pull db '[*])
       t/entity->fact))

(defn derived-from [sys fact]
  (let [conn (:conn sys)
        db (db/db conn)
        eavt (apply d/rel->eavt fact)
        id-sym (ffirst eavt)
        q {:find [id-sym]
           :where eavt}
        child (ffirst (db/q db q))
        parents (fact-parents db child)
        facts (map (partial entity->fact db) parents)]
    (apply hash-set facts)))
