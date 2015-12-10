(ns oolon.system-test
  (:refer-clojure :exclude [run!])
  (:require [oolon.system :refer :all]
            [midje.sweet :refer :all]
            [oolon.table :as t]
            [oolon.datalog :as d]
            [oolon.module :as m]
            [oolon.db.datascript :as ds]))

(def sym-table
  (t/table :sym {:name :keyword}))

(def perm-table
  (t/table :perm {:x :keyword :y :keyword}))

(def add-sym-table
  (t/scratch :add-sym {:name :keyword}))

(def send-table
  (t/scratch :send {:name :keyword}))

(def recv-table
  (t/table :recv {:name :keyword}))

(def module
  (m/module
   :permutations
   [:state
    sym-table
    perm-table
    add-sym-table
    send-table
    recv-table
    :rules
    (d/rule [:perm {:x :?x :y :?y}]
            [[:sym#1 {:name :?x}]
             [:sym#2 {:name :?y}]
             '[(!= ?x ?y)]])
    (d/rule [:sym {:name :?n}]
            [[:add-sym {:name :?n}]])
    (d/rule+ [:recv {:name :?n}]
             [[:send {:name :?n}]])]))

(facts "About a new system"
       (let [conn (ds/create-conn {})
             sys (system :test conn [module])]
         (fact "We can ennumerate all the tables"
               (tables sys) => {:system system-table
                                :sym sym-table
                                :perm perm-table
                                :add-sym add-sym-table
                                :send send-table
                                :recv recv-table})
         (fact "The system is not started"
               (started? sys) => false)
         (fact "We cannot assert anything yet"
               (+fact sys [:foo {:bar :baz}]) => nil)
         (fact "We cannot run anything yet"
               (tick! sys) => nil)
         (fact "We have no state"
               (state sys) => nil)))

(facts "About a started system"
       (let [conn (ds/create-conn {})
             sys (start! (system :test conn [module]))]
         (fact "The system is started"
               (started? sys) => true)
         (fact "Starting twice is a noop"
               (start! sys) => sys)
         (fact "The state contains only the initial timestep"
               (state sys) => #{[:system {:name :test :timestep 1}]})
         (fact "Running the system is a noop"
               (tick! sys) => sys)))

(facts "About asserting a fact that triggers no rules"
          (let [conn (ds/create-conn {})
                sys  (-> :test
                         (system conn [module])
                         start!
                         (+fact [:sym {:name :a}]))]
            (fact "Before we run nothing has changed"
                  (state sys) => #{[:system {:name :test :timestep 1}]})
            (fact "Running the system moves to the next timestep and adds the fact"
                  (state (tick! sys)) => #{[:system {:name :test :timestep 2}]
                                          [:sym {:name :a}]})
            (fact "Running again does nothing"
                  (state (tick! sys)) => #{[:system {:name :test :timestep 2}]
                                          [:sym {:name :a}]})))

(facts "About asserting a facts that trigger rules"
       (let [conn (ds/create-conn {})
             sys  (-> :test
                      (system conn [module])
                      start!
                      (+fact [:sym {:name :a}])
                      (+fact [:sym {:name :b}])
                      tick!)]
         (fact "Running the system moves to the next timestep and adds the new facts"
               (let [s (state sys)]
                 (count s) => 5
                 (tabular
                  (fact "We have all the facts we expect"
                        (s ?fact) => ?fact)
                  ?fact
                  [:system {:name :test :timestep 2}]
                  [:sym {:name :a}]
                  [:sym {:name :b}]
                  [:perm {:x :a :y :b}]
                  [:perm {:x :b :y :a}])))
         (fact "Running again does nothing"
               (let [s (state (tick! sys))]
                 (count s) => 5
                 (tabular
                  (fact "We have all the facts we expect"
                        (s ?fact) => ?fact)
                  ?fact
                  [:system {:name :test :timestep 2}]
                  [:sym {:name :a}]
                  [:sym {:name :b}]
                  [:perm {:x :a :y :b}]
                  [:perm {:x :b :y :a}])))))

(facts "About scratch tables"
       (let [conn (ds/create-conn {})
             sys  (-> :test
                      (system conn [module])
                      start!
                      (+fact [:add-sym {:name :a}])
                      tick!)]
         (fact "Running the system moves to the next timestep and adds the new facts"
               (let [s (state sys)]
                 (count s) => 3
                 (tabular
                  (fact "We have all the facts we expect"
                        (s ?fact) => ?fact)
                  ?fact
                  [:system {:name :test :timestep 2}]
                  [:add-sym {:name :a}]
                  [:sym {:name :a}])))
         (fact "Running again does nothing but the scratch table is empty"
               (let [s (state (tick! sys))]
                 (count s) => 2
                 (tabular
                  (fact "We have all the facts we expect"
                        (s ?fact) => ?fact)
                  ?fact
                  [:system {:name :test :timestep 2}]
                  [:sym {:name :a}])))))

(facts "About deferred inserts"
       (let [conn (ds/create-conn {})
             sys  (-> :test
                      (system conn [module])
                      start!
                      (+fact [:send {:name :a}])
                      tick!)]
         (fact "Running the system moves to the next timestep and adds the new fact"
               (let [s (state sys)]
                 (count s) => 2
                 (tabular
                  (fact "We have all the facts we expect"
                        (s ?fact) => ?fact)
                  ?fact
                  [:system {:name :test :timestep 2}]
                  [:send {:name :a}])))
         (fact "Running the system again moves to the next timestep, removes the scratch and adds the deffered fact"
               (let [s (state (tick! sys))]
                 (count s) => 2
                 (tabular
                  (fact "We have all the facts we expect"
                        (s ?fact) => ?fact)
                  ?fact
                  [:system {:name :test :timestep 3}]
                  [:recv {:name :a}])))
         (fact "Running again does nothing"
               (let [s (state (tick! sys))]
                 (count s) => 2
                 (tabular
                  (fact "We have all the facts we expect"
                        (s ?fact) => ?fact)
                  ?fact
                  [:system {:name :test :timestep 3}]
                  [:recv {:name :a}])))))
