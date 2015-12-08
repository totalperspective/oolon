(ns oolon.db.datascript
  (:require [datascript.core :as d]
            [oolon.db :as db]))

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

(defrecord Conn [conn]
  db/Conn
  (-conn [_]
    conn)
  (-transact [_ tx-data]
    (d/transact conn tx-data))
  (-tempid [_ part]
    (d/tempid part))
  (-tempid [_ part n]
    (d/tempid part n))
  db/HasDb
  (-db [_]
    (->Db (d/db conn))))

(defn create-conn [init-schema]
  (let [conn (d/create-conn init-schema)]
    (->Conn conn)))
