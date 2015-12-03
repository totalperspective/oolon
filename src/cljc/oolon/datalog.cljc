(ns oolon.datalog
  (:require [clojure.core.match :refer [match]]))

(defn val->sym [val]
  (cond
    (= :_ val) '_
    (and (keyword? val)
         (= \? (second (str val)))) (symbol (name val))
    :else val))

(defn rel->eavt
  ([rel attrs]
   (rel->eavt rel attrs nil))
  ([rel attrs tx]
   (let [[rel-name rel-id] (clojure.string/split (name rel) #"#")
         eid (symbol (str "?" rel-name rel-id))
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

(defn query* [rels]
  (when (sequential? rels)
    (->> rels
         (mapcat (fn [rel]
                   (cond
                     (list? rel) [(apply list (query* rel))]
                     (and (sequential? rel) (keyword? (first rel))) (apply rel->eavt rel)
                     :else [rel])))
         (into []))))

(defn query [& rels]
  (query* rels))

(defn lvar? [x]
  (and (symbol? x)
       (= \? (first (name x)))))

(defn flatten-all [form]
  (->> form
       (clojure.walk/prewalk #(if (coll? %)
                                (seq %)
                                %))
       flatten))

(defn lvars [form]
  (->> form
       flatten-all
       (filter lvar?)
       (into #{})))

(defn bind [smap form]
  (clojure.walk/prewalk-replace smap form))

(defn negation? [term]
  (match [term]
         [(['not & r] :seq)] true
         [(['not-join & r] :seq)] true
         :else false))

(defn positive [form]
  (remove negation? form))

(defn negative [form]
  (filter negation? form))

(defn safe? [lhs rhs]
  (let [lhs-lvars (lvars lhs)
        pos-lvars (-> rhs positive lvars)
        neg-lvars (-> rhs negative lvars)
        lhs-diff (clojure.set/difference lhs-lvars pos-lvars)
        neg-diff (clojure.set/difference neg-lvars pos-lvars)]
    (every? empty? [lhs-diff neg-diff])))
