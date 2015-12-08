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
        :vals {:cost :long}
        :type :table}

       (table :link
              {:src :keyword :dst :keyword}
              {:cost :long}
              :scratch)
       =>
       {:name :link
        :keys {:src :keyword
               :dst :keyword}
        :vals {:cost :long}
        :type :scratch}

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
             key {:src :foo :dst :bar}
             id (hash key)]
         (record link row)
         =>
         {:link/$id id
          :link/src :foo
          :link/dst :bar
          :link/cost 1}))
