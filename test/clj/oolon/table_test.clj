(ns oolon.table-test
  (:require [oolon.table :refer :all]
            [midje.sweet :refer :all]))

(unfinished table scratch)

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
