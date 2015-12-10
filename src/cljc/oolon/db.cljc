(ns oolon.db)

;; ## Protocols

(defprotocol Conn
  (-conn [conn] "Get the underlying connection")
  (-transact [conn tx-data] "Submits a transaction to the database for writing.")
  (-tempid [conn part] [conn part n] "Generate a tempid in the specified
partition. Within the scope of a single transaction, tempids map
consistently to permanent ids. Values of n from -1 to -1000000,
inclusive, are reserved for user-created tempids.")
  (-add-attributes [conn tx-data]))

(defprotocol HasDb
  (-db [conn] "Retrieves a value of the database for reading."))

(defprotocol Db
  (-query [db query-map] "Executes a query against inputs.")
  (-with [db tx-data] "Applies tx-data to the database.")
  (-resolve-tempid [db tempids tempid] "Resolve a tempid to the actual
id assigned in a database. The tempids object must come from
the :tempids member returned through transact or with"))

;; ## Test functions

(defn conn? [x]
  (satisfies? Conn x))

(defn conn-fn [f]
  (fn [r & args]
    {:pre [(conn? r)]}
    (apply f r args)))

(defn has-db? [x]
  (satisfies? HasDb x))

(defn has-db-fn [f]
  (fn [r & args]
    {:pre [(has-db? r)]}
    (apply f r args)))

(defn db? [x]
  (satisfies? Db x))

(defn db-fn [f]
  (fn [r & args]
    {:pre [(db? r)]}
    (apply f r args)))

;; ## Wraped entry points

;; ### Conn

(def conn (conn-fn -conn))

(def transact (conn-fn -transact))

(def tempid (conn-fn -tempid))

(def add-attributes (conn-fn -add-attributes))

;; ## HasDb
(def db (has-db-fn -db))

;; ## Db

(def query (db-fn -query))

(defn q [db q & inputs]
  (query db {:query q
             :args inputs}))

(def with (db-fn -with))

(def resolve-tempid (db-fn -resolve-tempid))
