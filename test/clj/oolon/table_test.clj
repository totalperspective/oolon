(ns oolon.table-test
  (:require [oolon.table :refer :all]
            [midje.sweet :refer :all]))

(facts "About defineing tables"
       (table :link
              {:src :keyword :dst :keyword}
              {:cost :long})
       =>
       {:name :link
        :keys {:src :keyword
               :dst :keyword}
        :vals {:cost :long}}

       (table :link
              {:src :keyword :dst :keyword}
              {:cost :long}
              :scratch)
       =>
       {:name :link
        :keys {:src :keyword
               :dst :keyword}
        :vals {:cost :long}
        :scratch true}

       (scratch :link
                {:src :keyword :dst :keyword}
                {:cost :long})
       =>
       (table :link
              {:src :keyword :dst :keyword}
              {:cost :long}
              :scratch))

(facts "About records"
       (let [link (table :link
                         {:src :keyword :dst :keyword}
                         {:cost :long})
             row {:src :foo :dst :bar :cost 1}
             key {:link/src :foo :link/dst :bar}
             id (hash key)]
         (fact "We can generate a record from a row"
               (record link row)
               =>
               {:link/$id id
                :link/src :foo
                :link/dst :bar
                :link/cost 1})
         (fact "We can an empty relation"
               (rel link) => '[:link {:src ?src :dst ?dst :cost ?cost}])))
