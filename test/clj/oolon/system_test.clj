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

(def module
  (m/module
   :permutations
   [:state
    sym-table
    perm-table
    :rules
    (d/rule [:perm {:x :?x :y :?y}]
            [[:sym {:name :?x}]
             [:sym {:name :?y}]
             '(!= ?x ?y)])]))

(facts "About a new system"
       (let [conn (ds/create-conn {})
             sys (system :test conn [module])]
         (fact "We can ennumerate all the tables"
               (tables sys) => {:system system-table
                                :sym sym-table
                                :perm perm-table})
         (fact "The system is not started"
               (started? sys) => false)
         (fact "We cannot assert anything yet"
               (+fact sys [:foo {:bar :baz}]) => nil)
         (fact "We cannot run anything yet"
               (run! sys) => nil)
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
               (run! sys) => sys)))

(facts "About asserting a fact that triggers no rules"
          (let [conn (ds/create-conn {})
                sys  (-> :test
                         (system conn [module])
                         start!
                         (+fact [:sym {:name :a}]))]
            (fact "Before we run nothing has changed"
                  (state sys) => #{[:system {:name :test :timestep 1}]})
            (fact "Running the system moves to the next timestep and adds the fact"
                  (state (run! sys)) => #{[:system {:name :test :timestep 2}]
                                          [:sym {:name :a}]})
            (fact "Running again does nothing"
                  (state (run! sys)) => #{[:system {:name :test :timestep 2}]
                                          [:sym {:name :a}]})))
