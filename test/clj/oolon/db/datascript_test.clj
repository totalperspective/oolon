(ns oolon.db.datascript-test
  (:require [oolon.db :as db]
            [oolon.db.datascript :refer :all]
            [midje.sweet :refer :all]))

(def schema {:aka {:db/cardinality :db.cardinality/many}})

(facts "About datascript schemas"
       (let [tx [{:db/id :db/current-tx
                  :db/ident :aka
                  :db/cardinality :db.cardinality/many
                  :db.install/_attribute :db.part/db}]]
         (fact "We can convert a datascript schema to a datomic one"
               (map->schema schema) => tx)
         (fact "We can convert a datomic schema to a datascript one"
               (schema->map tx) => schema)))

(facts "About datascript"
       (let [conn (create-conn schema)
             tx-data [{:db/id (db/tempid conn :db.part/user)
                       :name  "Maksim"
                       :age   45
                       :aka   ["Maks Otto von Stirlitz", "Jack Ryan"]}]]
         (db/transact conn tx-data)
         (let [db (db/db conn)]
           (fact "We can run a simple query"
                 (db/q db
                       '[:find  ?n ?a
                         :where [?e :aka "Maks Otto von Stirlitz"]
                         [?e :name ?n]
                         [?e :age  ?a]])
                 => #{ ["Maksim" 45] })
           (fact "Destructuring, function call, predicate call, query over collection"
                 (db/q db '[:find  ?k ?x
                            :in    $ [[?k [?min ?max]] ...] ?range
                            :where [(?range ?min ?max) [?x ...]]
                            [(even? ?x)] ]
                       { :a [1 7], :b [2 4] }
                       range)
                 =>
                 #{ [:a 2] [:a 4] [:a 6] [:b 2]}))))
