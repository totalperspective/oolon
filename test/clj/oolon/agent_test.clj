(ns oolon.agent-test
  (:refer-clojure :exclude [agent])
  (:require [oolon.agent :refer :all]
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
  (t/input :add-sym {:name :keyword}))

(def sym-added-table
  (t/output :sym-added {:name :keyword}))

(def send-table
  (t/scratch :send {:name :keyword}))

(def recv-table
  (t/table :recv {:name :keyword}))

(def chan-in
  (t/channel :chan-in {:msg :keyword}))

(def chan-out
  (t/channel :chan-out {:msg :keyword}))

(def loop-send
  (t/scratch :loop-send {:msg :keyword}))

(def loop-chan
  (t/loopback :loop {:msg :keyword}))

(def loop-recv
  (t/scratch :loop-recv {:msg :keyword}))

(def module-tables (into internal-tables
                         {:sym sym-table
                          :perm perm-table
                          :add-sym add-sym-table
                          :sym-added sym-added-table
                          :send send-table
                          :recv recv-table
                          :chan-in chan-in
                          :chan-out chan-out
                          :loop-send loop-send
                          :loop loop-chan
                          :loop-recv loop-recv}))

(def module
  (m/module
   :permutations
   {:state (vals module-tables)
    :rules [(d/rule [:perm {:x :?x :y :?y}]
                    [[:sym#1 {:name :?x}]
                     [:sym#2 {:name :?y}]
                     '[(!= ?x ?y)]])
            (d/rule [:sym {:name :?n}]
                    [[:add-sym {:name :?n}]])
            (d/rule [:sym-added {:name :?n}]
                    [[:sym {:name :?n}]])
            (d/rule+ [:recv {:name :?n}]
                     [[:send {:name :?n}]])
            (d/rule> [:chan-out {:msg :?m}]
                     [[:chan-in {:msg :?m}]])
            (d/rule> [:loop {:msg :?m}]
                     [[:loop-send {:msg :?m}]])
            (d/rule [:loop-recv {:msg :?m}]
                    [[:loop {:msg :?m}]])]}))

(def importing-module
  (m/module
   :importer
   [:state
    (t/channel :sym-chan {:name :keyword})
    :import
    module
    :rules
    (d/rule [:add-sym {:name :?n}]
            [[:sym-chan {:name :?n}]])
    (d/rule> [:sym-chan {:name :?n}]
             [[:sym-added {:name :?n}]])]))

(facts "About a new system"
       (let [conn (ds/create-conn {})
             sys (agent :test conn [module])]
         (fact "We can ennumerate all the tables"
               (tables sys) => module-tables)
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
             agnt (start! (agent :test conn [module]))]
         (fact "The system is started"
               (started? agnt) => true)
         (fact "Starting twice is a noop"
               (start! agnt) => agnt)
         (fact "The state contains only the initial timestep"
               (state agnt) => #{[:agent {:name :test :timestep 1}]})
         (fact "Running the system is a noop"
               (tick! agnt) => agnt)))

(facts "About asserting a fact that triggers no rules"
       (let [conn (ds/create-conn {})
             agnt  (-> :test
                      (agent conn [module])
                      start!
                      (+fact [:perm {:x :a :y :b}]))]
         (fact "Before we run nothing has changed"
               (state agnt) => #{[:agent {:name :test :timestep 1}]})
         (let [agnt (tick! agnt)]
           (fact "Running the system moves to the next timestep and adds the fact"
                 (state agnt) => #{[:agent {:name :test :timestep 2}]
                                   [:perm {:x :a :y :b}]})
           (fact "Running again does nothing"
                 (state (tick! agnt)) => #{[:agent {:name :test :timestep 2}]
                                           [:perm {:x :a :y :b}]}))))

(facts "About asserting a facts that trigger rules"
       (let [conn (ds/create-conn {})
             sys  (-> :test
                      (agent conn [module])
                      start!
                      (+fact [:sym {:name :a}])
                      (+fact [:sym {:name :b}])
                      tick!)]
         (fact "Running the system moves to the next timestep and adds the new facts"
               (let [s (state sys)]
                 (count s) => 7
                 (tabular
                  (fact "We have all the facts we expect"
                        (s ?fact) => ?fact)
                  ?fact
                  [:agent {:name :test :timestep 2}]
                  [:sym {:name :a}]
                  [:sym {:name :b}]
                  [:sym-added {:name :a}]
                  [:sym-added {:name :b}]
                  [:perm {:x :a :y :b}]
                  [:perm {:x :b :y :a}])))
         (fact "Running again does nothing"
               (let [s (state (tick! sys))]
                 (count s) => 5
                 (tabular
                  (fact "We have all the facts we expect"
                        (s ?fact) => ?fact)
                  ?fact
                  [:agent {:name :test :timestep 2}]
                  [:sym {:name :a}]
                  [:sym {:name :b}]
                  [:perm {:x :a :y :b}]
                  [:perm {:x :b :y :a}])))))

(facts "About scratch tables"
       (let [conn (ds/create-conn {})
             sys  (-> :test
                      (agent conn [module])
                      start!
                      (+fact [:add-sym {:name :a}])
                      tick!)]
         (fact "Running the system moves to the next timestep and adds the new facts"
               (let [s (state sys)]
                 (count s) => 4
                 (tabular
                  (fact "We have all the facts we expect"
                        (s ?fact) => ?fact)
                  ?fact
                  [:agent {:name :test :timestep 2}]
                  [:add-sym {:name :a}]
                  [:sym {:name :a}]
                  [:sym-added {:name :a}])))
         (fact "Running again does nothing but the scratch table is empty"
               (let [s (state (tick! sys))]
                 (count s) => 2
                 (tabular
                  (fact "We have all the facts we expect"
                        (s ?fact) => ?fact)
                  ?fact
                  [:agent {:name :test :timestep 2}]
                  [:sym {:name :a}])))))

(facts "About deferred inserts"
       (let [conn (ds/create-conn {})
             sys  (-> :test
                      (agent conn [module])
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
                  [:agent {:name :test :timestep 2}]
                  [:send {:name :a}])))
         (fact "Running the system again moves to the next timestep, removes the scratch and adds the deffered fact"
               (let [sys (tick! sys)
                     s (state sys)]
                 (count s) => 2
                 (tabular
                  (fact "We have all the facts we expect"
                        (s ?fact) => ?fact)
                  ?fact
                  [:agent {:name :test :timestep 3}]
                  [:recv {:name :a}])
                 (fact "Running again does nothing"
                       (let [s (state (tick! sys))]
                         (count s) => 2
                         (tabular
                          (fact "We have all the facts we expect"
                                (s ?fact) => ?fact)
                          ?fact
                          [:agent {:name :test :timestep 3}]
                          [:recv {:name :a}])))))))

(facts "About channels"
       (let [conn (ds/create-conn {})
             sys  (-> :test
                      (agent conn [module])
                      start!
                      (+fact [:chan-in {:msg :foo}])
                      tick!)]
         (fact "Running the system moves to the next timestep and send the out msg"
               (let [s (state sys)]
                 (count s) => 1
                 (tabular
                  (fact "We have all the facts we expect"
                        (s ?fact) => ?fact)
                  ?fact
                  [:agent {:name :test :timestep 2}])
                 (fact "We also have the msg in the out buffer"
                       (out sys) => #{[:chan-out {:msg :foo}]})))
         (let [sys (tick! sys)]
           (fact "Running again empties the scratch table and move the timestep"
                 (let [s (state sys)]
                   (count s) => 1
                   (tabular
                    (fact "We have all the facts we expect"
                          (s ?fact) => ?fact)
                    ?fact
                    [:agent {:name :test :timestep 3}])))
           (let [sys (tick! sys)]
             (fact "Nothing happens"
                   (state sys) => #{[:agent {:name :test :timestep 3}]})))))

(facts "About loopback channels"
       (let [conn (ds/create-conn {})
             agnt  (-> :test
                       (agent conn [module])
                       start!
                       (+fact [:loop-send {:msg :foo}])
                       tick!)]
         (fact "Running the system moves to the next timestep and adds the new fact"
               (let [s (state agnt)]
                 (tabular
                  (fact "We have all the facts we expect"
                        (s ?fact) => ?fact)
                  ?fact
                  [:agent {:name :test :timestep 2}]
                  [:loop-send {:msg :foo}])))
         (fact "Running again asserts the looped msg"
               (let [agnt (tick! agnt)
                     s (state agnt)]
                 (tabular
                  (fact "We have all the facts we expect"
                        (s ?fact) => ?fact)
                  ?fact
                  [:agent {:name :test :timestep 3}]
                  [:loop-recv {:msg :foo}])
                 (fact "Running again empties the sratch table but does not advance"
                       (let [s (state (tick! agnt))]
                         (tabular
                          (fact "We have all the facts we expect"
                                (s ?fact) => ?fact)
                          ?fact
                          [:agent {:name :test :timestep 3}])))))))

(facts "About module importing"
       (let [conn (ds/create-conn {})
             agnt  (-> :test
                       (agent conn [importing-module])
                       start!
                       (+fact [:sym-chan {:name :a}])
                       tick!)
             modules (->> agnt
                          :modules
                          (map :name)
                          (into #{}))
             s (state agnt)]
         (fact "We have both modules loaded"
               modules => #{:permutations :importer})
         (fact "We have our symbol back on the output"
               (out agnt) => #{[:sym-chan {:name :a}]})
         (tabular
          (fact "We have all the facts we expect"
                (s ?fact) => ?fact)
          ?fact
          [:sym {:name :a}]
          [:sym-added {:name :a}])))

(def double-neg-module
  (m/module
   :nm-module
   [:state
    (t/table :person {:name :keyword})
    (t/table :likes {:person :keyword :likes :keyword})
    (t/table :everyone-likes {:name :keyword})
    (t/table :is-not-liked {:name :keyword})
    :rules
    (d/rule [:is-not-liked {:name :?y}]
            [[:person#x {:name :?x}]
             [:person#y {:name :?y}]
             '(not-join [?x ?y]
                        [:likes {:person :?x :likes :?y}])])
    (d/rule [:everyone-likes {:name :?x}]
            [[:person {:name :?x}]
             '(not-join [?x]
                        [:is-not-liked {:name :?x}])])]))

(def data
  [[:person {:name :a}]
   [:person {:name :b}]
   [:person {:name :c}]
   [:person {:name :d}]
   [:likes {:person :a :likes :a}]
   [:likes {:person :b :likes :a}]
   [:likes {:person :c :likes :a}]
   [:likes {:person :d :likes :a}]])

(defn add-data [agnt data]
  (let [agnt (reduce +fact agnt data)]
    agnt))

(facts "About stratification"
       (let [conn (ds/create-conn {})
             agnt  (-> :test
                       (agent conn [double-neg-module])
                       start!
                       (add-data data)
                       tick!)]
         (fact "Everyone likes a"
               (let [s (state agnt)]
                 (count s) => 9
                 (tabular
                  (fact "We have all the facts we expect"
                        (s ?fact) => ?fact)
                  ?fact
                  [:is-not-liked {:name :d}]
                  [:agent {:name :test, :timestep 2}]
                  [:person {:name :d}]
                  [:everyone-likes {:name :a}]
                  [:is-not-liked {:name :c}]
                  [:person {:name :b}]
                  [:person {:name :c}]
                  [:is-not-liked {:name :b}]
                  [:person {:name :a}]
                  [:agent {:name :test :timestep 2}])))))

(def link-module
  (m/module
   :link
   [:state
    (t/table :link {:src :long :dst :long})
    (t/table :path {:src :long :dst :long})
    (t/input :drop-link {:src :long :dst :long})
    :rules
    (d/rule [:path {:src :?src :dst :?dst}]
            [[:link {:src :?src :dst :?dst}]])
    (d/rule [:path {:src :?src :dst :?dst}]
            [[:path {:src :?src :dst :?via}]
             [:link {:src :?via :dst :?dst}]])
    (d/rule- [:link {:src :?src :dst :?dst}]
             [[:drop-link {:src :?src :dst :?dst}]])]))

(def link-data
  [[:link {:src 1 :dst 2}]
   [:link {:src 2 :dst 3}]
   [:link {:src 3 :dst 4}]
   [:link {:src 2 :dst 5}]
   [:link {:src 5 :dst 4}]])

(facts "About lineage"
       (let [conn (ds/create-conn {})
             agnt  (-> :test
                       (agent conn [link-module])
                       start!
                       (add-data link-data)
                       tick!)]
         (fact "Everyone all the paths are found"
               (let [s (state agnt)]
                 (count s) => 15
                 (tabular
                  (fact "We have all the facts we expect"
                        (s ?fact) => ?fact)
                  ?fact
                  [:link {:src 1 :dst 2}]
                  [:link {:src 2 :dst 3}]
                  [:link {:src 3 :dst 4}]
                  [:link {:src 2 :dst 5}]
                  [:link {:src 5 :dst 4}]
                  ;; order 1 paths
                  [:path {:src 1 :dst 2}]
                  [:path {:src 2 :dst 3}]
                  [:path {:src 3 :dst 4}]
                  [:path {:src 2 :dst 5}]
                  [:path {:src 5 :dst 4}]
                  ;; order 2 paths
                  [:path {:src 1 :dst 3}]
                  [:path {:src 1 :dst 5}]
                  [:path {:src 2 :dst 4}]
                  ;; order 3 paths
                  [:path {:src 1 :dst 4}]
                  ;; ts
                  [:agent {:name :test :timestep 2}])))

         (fact "We know how a fact was derived"
               (let [drv (derived-from agnt [:path {:src 2 :dst 4}])]
                 (count drv) => 6
                 (tabular
                  (fact "We have all the facts we expect"
                        (drv ?fact) => ?fact)
                  ?fact
                  ;; 1st proof
                  [:link {:src 2 :dst 3}]
                  [:path {:src 2 :dst 3}]
                  [:link {:src 3 :dst 4}]
                  ;; 2nd proof
                  [:link {:src 2 :dst 5}]
                  [:path {:src 2 :dst 5}]
                  [:link {:src 5 :dst 4}])))))

(facts "About retractions"
       (let [conn (ds/create-conn {})
             agnt  (-> :test
                       (agent conn [link-module])
                       start!
                       (add-data link-data)
                       tick!
                       (-fact [:link {:src 3 :dst 4}])
                       tick!)]
         (fact "All the paths are found"
               (let [s (state agnt)]
                 (count s) => 13
                 (tabular
                  (fact "We have all the facts we expect"
                        (s ?fact) => ?fact)
                  ?fact
                  [:link {:src 1 :dst 2}]
                  [:link {:src 2 :dst 3}]
                  [:link {:src 2 :dst 5}]
                  [:link {:src 5 :dst 4}]
                  ;; order 1 paths
                  [:path {:src 1 :dst 2}]
                  [:path {:src 2 :dst 3}]
                  [:path {:src 2 :dst 5}]
                  [:path {:src 5 :dst 4}]
                  ;; order 2 paths
                  [:path {:src 1 :dst 3}]
                  [:path {:src 1 :dst 5}]
                  [:path {:src 2 :dst 4}]
                  ;; order 3 paths
                  [:path {:src 1 :dst 4}]
                  ;; ts
                  [:agent {:name :test :timestep 3}])))
         (fact "We now only have 1 derivation"
               (let [drv (derived-from agnt [:path {:src 2 :dst 4}])]
                 (count drv) => 3
                 (tabular
                  (fact "We have all the facts we expect"
                        (drv ?fact) => ?fact)
                  ?fact
                  [:link {:src 2 :dst 5}]
                  [:path {:src 2 :dst 5}]
                  [:link {:src 5 :dst 4}])))
         (facts "About removing the other dependancy"
                (let [agnt2 (-> agnt
                                (-fact [:link {:src 2 :dst 5}])
                                tick!)]
                  (fact "The derived paths are removed"
                        (let [s (state agnt2)]
                          (count s) => 8
                          (tabular
                           (fact "We have all the facts we expect"
                                 (s ?fact) => ?fact)
                           ?fact
                           [:link {:src 1 :dst 2}]
                           [:link {:src 2 :dst 3}]
                           [:link {:src 5 :dst 4}]
                           ;; order 1 paths
                           [:path {:src 1 :dst 2}]
                           [:path {:src 2 :dst 3}]
                           [:path {:src 5 :dst 4}]
                           ;; order 2 paths
                           [:path {:src 1 :dst 3}]
                           ;; ts
                           [:agent {:name :test :timestep 4}])))))))
(facts "About retraction rules"
       (let [conn (ds/create-conn {})
             agnt  (-> :test
                       (agent conn [link-module])
                       start!
                       (add-data link-data)
                       tick!
                       (+fact [:drop-link {:src 3 :dst 4}])
                       tick!
                       tick!)]
         (fact "All the paths are found"
               (let [s (state agnt)]
                 (count s) => 13
                 (tabular
                  (fact "We have all the facts we expect"
                        (s ?fact) => ?fact)
                  ?fact
                  [:link {:src 1 :dst 2}]
                  [:link {:src 2 :dst 3}]
                  [:link {:src 2 :dst 5}]
                  [:link {:src 5 :dst 4}]
                  ;; order 1 paths
                  [:path {:src 1 :dst 2}]
                  [:path {:src 2 :dst 3}]
                  [:path {:src 2 :dst 5}]
                  [:path {:src 5 :dst 4}]
                  ;; order 2 paths
                  [:path {:src 1 :dst 3}]
                  [:path {:src 1 :dst 5}]
                  [:path {:src 2 :dst 4}]
                  ;; order 3 paths
                  [:path {:src 1 :dst 4}]
                  ;; ts
                  [:agent {:name :test :timestep 4}])))
         (fact "We now only have 1 derivation"
               (let [drv (derived-from agnt [:path {:src 2 :dst 4}])]
                 (count drv) => 3
                 (tabular
                  (fact "We have all the facts we expect"
                        (drv ?fact) => ?fact)
                  ?fact
                  [:link {:src 2 :dst 5}]
                  [:path {:src 2 :dst 5}]
                  [:link {:src 5 :dst 4}])))))

(def upsert-module
  (m/module
   :upsert
   [:state
    (t/table :test {:key :keyword} {:val :long})
    (t/input :upsert {:key :keyword :val :long})
    :rules
    (d/rule+- [:test {:key :?key :val :?val}]
              [[:upsert {:key :?key :val :?val}]])]))

(facts "About upserts"
       (let [conn (ds/create-conn {})
             agnt  (-> :test
                       (agent conn [upsert-module])
                       start!
                       (+fact [:test {:key :foo :val 1}])
                       tick!)]
         (fact "The initial state has the original value"
               (state agnt) => (contains #{[:test {:key :foo :val 1}]}))
         (let [agnt (-> agnt
                        (+fact [:upsert {:key :foo :val 2}])
                        tick!
                        tick!)]
           (fact "The after upserting we get a new value"
                 (state agnt) => (contains #{[:test {:key :foo :val 2}]}))
           (fact "The after upserting no longet have our old value"
                 (state agnt) =not=> (contains #{[:test {:key :foo :val 1}]})))))

(def halt-module
  (m/module
   :halter
   [:state
    (t/table :fact {:key :keyword} {:val :boolean})]))

(facts "About halting"
       (let [conn (ds/create-conn {})
             agnt  (-> :test
                       (agent conn [halt-module])
                       start!
                       (+fact [:fact {:key :foo :val true}])
                       tick!
                       (+fact [:halt {:kill true}])
                       tick!
                       (+-fact [:fact {:key :foo :val false}])
                       tick!)
             s (state agnt)]
         (fact "The initial state has the original value and the timestep did not move"
               (count s) => 3
               (tabular
                (fact "We have all the facts we expect"
                      (s ?fact) => ?fact)
                ?fact
                [:halt {:kill true}]
                [:fact {:key :foo :val true}]
                [:agent {:name :test :timestep 3}]))))

(def clock-module
  (m/module
   :clock
   [:state
    (t/input :tick {:id :keyword})
    (t/channel :time {:id :keyword} {:time :instant})
    :rules
    (d/rule> [:time {:id :?id :time :?t}]
             [[:tick {:id :?id}]
              [:clock {:now :?t}]])]))

(facts "About the cock"
       (let [conn (ds/create-conn {})
             agnt  (-> :test
                       (agent conn [clock-module])
                       start!
                       (+fact [:tick {:id :foo}])
                       tick!)
             s (state agnt)
             time (first (out agnt))]
         (fact "We see the input tick and the timestep move"
               (count s) => 2
               (tabular
                (fact "We have all the facts we expect"
                      (s ?fact) => ?fact)
                ?fact
                [:tick {:id :foo}]
                [:agent {:name :test :timestep 2}]))
         (fact "we have the time"
               time => (just [:time (contains {:id :foo :time anything})])
               (class (:time (second time))) => java.util.Date)))

(def vote-module
  (m/module
   :vote
   [:state
    (t/table :vote {:master :keyword :peer :keyword :ident :keyword} {:response :keyword})
    (t/scratch :vote-cnt {:ident :keyword :response :keyword :count :long})
    :rules
    (d/rule [:vote-cnt {:ident :?id :response :?r :count :?c}]
            [[:vote {:ident :?id :response :?r :peer :?p}]]
            [:?id :?r] {:?c '(count :?id)})]))

(defn vote [master peer ident response]
  [:vote {:master master :peer peer :ident ident :response response}])

(def vote-data
  [(vote :a :b :foo :yes)
   (vote :a :a :foo :yes)
   (vote :a :c :foo :no)])

(facts "About votes"
       (let [conn (ds/create-conn {})
             agnt  (-> :test
                       (agent conn [vote-module])
                       start!
                       (add-data vote-data)
                       tick!)
             s (state agnt)]
         (prn s)
         (fact "We get our vote results"
               (count s) => 6
               (tabular
                (fact "We have all the facts we expect"
                      (s ?fact) => ?fact)
                ?fact
                [:vote-cnt {:ident :foo :response :yes :count 2}]
                [:vote-cnt {:ident :foo :response :no :count 1}]
                [:agent {:name :test :timestep 2}]))))
