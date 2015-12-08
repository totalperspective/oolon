(ns oolon.db.datascript
  (:require [datascript.core :as d]
            [datascript.db :as ddb]
            [oolon.db :as db]))

(def init-schema {:db/ident {:db/unique :db.unique/identity}
                  :db.install/attribute {:db/valueType :db.type/ref
                                         :db/cardinality :db.cardinality/many}})

(def init-tx-data [{:db/id -1
                    :db/ident :db.part/db}])

(defn map->schema [m]
  (mapv (fn [[id sch]]
          (-> sch
              (assoc :db/ident id)
              (assoc :db/id (d/tempid :db.part/tx))
              (assoc :db.install/_attribute :db.part/db)))
        m))

(defn schema->map [tx]
  (into {}
        (map (fn [sch]
               (let [ident (:db/ident sch)
                     sch (dissoc sch :db/ident :db/id :db.install/_attribute)]
                 [ident sch]))
             tx)))

(defrecord Db [db]
  db/Db
  (-query [_ {:keys [query args]}]
    (apply d/q query db args))
  (-with [db tx-data]
    (->Db (d/with db tx-data)))
  (-resolve-tempid [_ tempids tempid]
    (d/resolve-tempid db tempids tempid))
  db/HasDb
  (-db [this]
    this))

(defn refs->ident [conn tx]
  (let [schema (ddb/-schema @conn)]
    (if (map? tx)
      (->> tx
           (map (fn [[a v]]
                  (let [ref (if (ddb/reverse-ref? a)
                              (ddb/reverse-ref a)
                              a)
                        type (get-in schema [ref :db/valueType])]
                    (if (and (keyword? v) (= :db.type/ref type))
                      [a [:db/ident v]]
                      [a v]))))
           (into {}))
      tx)))

(defrecord Conn [conn]
  db/Conn
  (-conn [_]
    conn)
  (-transact [_ tx-data]
    (->> tx-data
         (map (partial refs->ident conn))
         (d/transact conn)))
  (-tempid [_ part]
    (d/tempid part))
  (-tempid [_ part n]
    (d/tempid part n))
  (-add-attributes [this tx-data]
    (let [tx-data (map (partial refs->ident conn) tx-data)
          db (:db-after (d/with (d/db conn) tx-data))
          attrs (->> db
                     (d/q '[:find (pull ?e [*])
                            :in $ ?d
                            :where [[:db/ident :db.part/db] :db.install/attribute ?e]])
                     (map first)
                     schema->map)
          {:keys [schema rschema]} (ddb/empty-db (merge init-schema attrs))]
      (swap! conn (fn [db]
                    (-> db
                        (assoc :rschema rschema)
                        (assoc :schema schema))))
      (d/transact! conn tx-data)
      this))
  db/HasDb
  (-db [_]
    (->Db (d/db conn))))

(defn create-conn [schema]
  (let [schema (map->schema schema)
        conn (->Conn (d/create-conn init-schema))]
    (db/transact conn init-tx-data)
    (db/add-attributes conn schema)
    conn))
