(ns oolon.module-test
  (:require [oolon.module :refer :all]
            [oolon.table :as t]
            [oolon.datalog :as d]
            [midje.sweet :refer :all]))

(def sym (t/table :sym {:name :keyword}))

(def perm (t/table :perm {:x :keyword :y :keyword}))

(def make-perm (d/rule [:perm {:x :?x :y :?y}]
                       [[:sym {:name :?x}]
                        [:sym {:name :?y}]
                        '(!= ?x ?y)]))

(def map-module (module :permutations
                        {:state [sym perm]
                         :rules [make-perm]}))

(def seq-module (module :permutations
                        [:state
                         sym
                         perm
                         :rules
                         make-perm]))

(facts "About modules"
       (fact "Both creation methods yeld the same module"
             map-module => seq-module)
       (fact "We can get the modules name"
             (:name map-module) => :permutations)
       (fact "The module has a sym table"
             (get-in map-module [:state :sym]) => sym)
       (fact "The module has a sym table"
             (get-in map-module [:state :perm]) => perm)
       (fact "The modules rules contains the make-perm rule"
             (:rules map-module) => (contains make-perm)))
