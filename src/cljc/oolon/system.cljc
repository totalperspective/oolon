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

(defn fact->record [sys fact]
  (let [tables (tables sys)
        [name attrs] fact
        table (get tables name)]
    (when table
      (t/record table attrs))))

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

(defn run! [sys]
  (when (started? sys)
    (let [{:keys [facts name conn]} sys
          {:keys [assertions]} facts
          {:keys [tx-data] :as tx} @(db/transact conn assertions)
          sys (assoc sys :facts empty-facts)]
      (if (empty? tx-data)
        sys
        (let [[[_ ts-attrs]] (table->facts sys system-table)
              timestep (:timestep ts-attrs)
              next-t (t/record system-table {:name name :timestep (inc timestep)})]
          @(db/transact conn [next-t])
          sys)))))
