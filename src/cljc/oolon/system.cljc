(ns oolon.system
  (:refer-clojure :exclude [run!])
  (:require [oolon.table :as t]
            [oolon.datalog :as d]
            [oolon.module :as m]
            [oolon.schema :as s]
            [oolon.db :as db]))

(defn system [name conn modules]
  {:name name
   :conn conn
   :modules modules})

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

(defn start! [sys]
  (if (started? sys)
    sys
    (let [{:keys [conn modules name]} sys
          db (db/db conn)
          tables [system-table]
          schema-tx (mapcat s/schema tables)
          sys-ts (t/record system-table {:name name :timestep 1})]
      (db/add-attributes conn schema-tx)
      (db/transact conn [sys-ts])
      sys)))

(defn assert! [sys fact]
  (when (started? sys)))

(defn state [sys]
  (when (started? sys)
    (let [{:keys [conn modules name]} sys
          db (db/db conn)
          tables [system-table]]
      (->> tables
           (mapcat (fn [table]
                     (let [rel (t/rel table)
                           eavt (apply d/rel->eavt rel)
                           lvars (into [] (d/lvars eavt))]
                       (->> {:find lvars :where eavt}
                            (db/q db)
                            (map (partial zipmap lvars))
                            (map (partial d/bind-form rel))))))
           (into #{})))))

(defn run! [sys]
  (when (started? sys)
    sys))
