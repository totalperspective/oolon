(ns oolon.schema-test
  (:require [oolon.schema :refer :all]
            [oolon.table :refer [table]]
            [midje.sweet :refer :all]))

(facts "About schema"
       (let [t (table :link
                      {:src :keyword :dst :keyword}
                      {:cost :long})
             s (schema t)]
         (fact "Given a table we get a datomic schema"
               s => (has every? (contains {:db/ident keyword?
                                           :db/valueType keyword?
                                           :db/index true})))
         (fact "One of the attributes is the id"
               s => (contains {:db/ident :link/$id
                               :db/valueType :db.type/long
                               :db/index true
                               :db/unique :db.unique/identity}))))
