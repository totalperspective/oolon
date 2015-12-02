(ns oolon.datalog-test
  (:require [oolon.datalog :refer :all]
            [midje.sweet :refer :all]))

(unfinished)

(facts "About val->sym"
       (tabular
        (fact "Basic types are un touched"
              (val->sym ?val) => ?val)
        ?val
        1
        "two"
        :three
        'four
        #inst "2010")
       (tabular
        (fact "Special keywords are turned into symbols"
              (val->sym ?val) => ?sym)
        ?val  ?sym
        :_    '_
        :?s   '?s))

(defn rel->eavt
  ([rel attrs]
   (rel->eavt rel attrs nil))
  ([rel attrs tx]
   (let [rel-name (name rel)
         eid (symbol (str "?" rel-name))
         tx (val->sym tx)]
     (when-not (empty? attrs)
       (mapv (fn [[k v]]
               (let [attr (val->sym k)
                     attr (if (keyword? attr)
                            (keyword rel-name (name attr))
                            attr)
                     datom [eid attr (val->sym v)]]
                 (if tx
                   (conj datom tx)
                   datom)))
             attrs)))))

(facts "About rel->eavt"
       (rel->eavt :link nil) => nil
       (rel->eavt :link {}) => nil
       (rel->eavt :link {:src 1}) => '[[?link :link/src 1]]
       (rel->eavt :link {:src :?src}) => '[[?link :link/src ?src]]
       (rel->eavt :link {:src :src :_ :dst}) => '[[?link :link/src :src]
                                                  [?link _ :dst]]
       (rel->eavt :link {:src :?src :dst :?dst} 1) =>  '[[?link :link/src ?src 1]
                                                         [?link :link/dst ?dst 1]]
       (rel->eavt :link {:src :?src :dst :dst} :?tx) =>  '[[?link :link/src ?src ?tx]
                                                            [?link :link/dst :dst ?tx]])
