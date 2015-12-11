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

(def sub-module-1 (module :sub-1 {}))

(def sub-module-2 (module :sub-2 {}))

(def sub-module-3 (module :sub-3 {:import [sub-module-1 sub-module-2]}))

(def import-module (module :imported {:import [sub-module-3]}))

(def map-module (module :permutations
                        {:state [sym perm]
                         :rules [make-perm]
                         :import [import-module]}))

(def seq-module (module :permutations
                        [:state
                         sym
                         perm
                         :rules
                         make-perm
                         :import
                         import-module]))

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
             (:rules map-module) => (contains make-perm))
       (fact "The import module is imported"
             (:import map-module) => (contains import-module))
       (fact "We can get all the imported modules"
             (imports map-module) => (just #{import-module
                                             sub-module-1
                                             sub-module-2
                                             sub-module-3})))
