(ns oolon.core
  (:require [datomic.api :as d]
            [datomic-schema.schema :as s]))

(def db-uri-base "datomic:mem://")

(defn scratch-conn
  "Create a connection to an anonymous, in-memory database."
  []
  (let [uri (str db-uri-base (d/squuid))]
    (d/delete-database uri)
    (d/create-database uri)
    (d/connect uri)))

(def schema-parts [(s/part "fact")
                   (s/part "rule")])

(def base-schema [(s/schema relation
                            (s/fields
                             [name :keyword :unique-identity]))
                  (s/schema arg
                            (s/fields
                             [relation :ref]
                             [position :long]
                             [binding :keyword]))
                  (s/schema entity
                            (s/fields
                             [id :uuid :unique-identity]
                             [name :keyword :unique-identity]))
                  (s/schema fact
                            (s/fields
                             [relation :ref]
                             [source :ref :many]))
                  (s/schema rule
                            (s/fields
                             [head :ref :component]
                             [tail :ref :component :many]))
                  (s/schema statement
                            (s/fields
                             [relation :ref]
                             [args :ref :component :many]))])

(defn create-context!
  []
  (let [conn (scratch-conn)]
    @(d/transact conn
                 (concat
                  (s/generate-parts d/tempid schema-parts)
                  (s/generate-schema d/tempid base-schema)))
    conn))
