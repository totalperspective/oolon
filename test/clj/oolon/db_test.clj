(ns oolon.db-test
  (:require [oolon.db :refer :all]
            [midje.sweet :refer :all]))

(defrecord Stub []
  oolon.db/Conn
  (-conn [this] :conn)
  (-transact [this arg1] :tx)
  (-tempid [this arg1] :tid1)
  (-tempid [this arg1 arg2] :tid2)
  oolon.db/HasDb
  (-db [this] :db)
  oolon.db/Db
  (-query [this arg1] :q)
  (-with [this arg1] :with)
  (-resolve-tempid [this arg1 arg2] :resolve))

(def stub (->Stub))

(def ac0 [])

(def ac1 [1])

(def ac2 [1 2])

(def ac3 [1 2 3])

(facts "About db protocols"
       (tabular
        (fact "Each fn dispatches to the correct place"
              (apply ?fn stub ?args) => ?res)
        ?fn            ?args ?res
        conn           ac0   :conn
        transact       ac1   :tx
        tempid         ac1   :tid1
        tempid         ac2   :tid2
        db             ac0   :db
        query          ac1   :q
        q              ac2   :q
        q              ac3   :q
        with           ac1   :with
        resolve-tempid ac2   :resolve)
       (tabular
        (fact "Each fn only dispatches with the correct number of args"
              (apply ?fn stub ?args) => (throws clojure.lang.ArityException))
        ?fn            ?args
        conn           ac1
        transact       ac0
        transact       ac2
        tempid         ac0
        tempid         ac3
        db             ac1
        query          ac0
        query          ac2
        q              ac0
        with           ac0
        with           ac2
        resolve-tempid ac1
        resolve-tempid ac3))
