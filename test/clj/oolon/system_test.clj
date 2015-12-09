(ns oolon.system-test
  (:refer-clojure :exclude [run!])
  (:require [oolon.system :refer :all]
            [midje.sweet :refer :all]
            [oolon.table :as t]
            [oolon.datalog :as d]
            [oolon.module :as m]
            [oolon.db.datascript :as ds]))

(def module
  (m/module
   :permutations
   [:state
    (t/table :sym {:name :keyword})
    (t/table :perm {:x :keyword :y :keyword})
    :rules
    (d/rule [:perm {:x :?x :y :?y}]
            [[:sym {:name :?x}]
             [:sym {:name :?y}]
             '(!= ?x ?y)])]))

(facts "About a new system"
       (let [conn (ds/create-conn {})
             sys (system :test conn [module])]
         (fact "The system is not started"
               (started? sys) => false)
         (fact "We cannot assert anything yet"
               (assert! sys [:foo {:bar :baz}]) => nil)
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
