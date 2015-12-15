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
   (when attrs
     (let [[rel-name rel-id] (clojure.string/split (name rel) #"#")
           eid (symbol (str "?" rel-name rel-id))
           tx (val->sym tx)
           id-attr (vector eid
                           (keyword rel-name "$id")
                           (symbol (str (name eid) "$id")))]
       (if (empty? attrs)
         [id-attr]
         (conj (mapv (fn [[k v]]
                       (let [attr (val->sym k)
                             attr (if (keyword? attr)
                                    (keyword rel-name (name attr))
                                    attr)
                             datom [eid attr (val->sym v)]]
                         (if tx
                           (conj datom tx)
                           datom)))
                     attrs)
               id-attr))))))

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

(defn bind-form [form smap]
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

(defn neg-lvars [form]
  (->> form
       negative
       (mapcat (fn [neg-form]
                 (let [type (first neg-form)]
                   (if 'not-join
                     (second neg-form)
                     (lvars neg-form)))))
       (into #{})))

(defn safe? [lhs rhs]
  (let [lhs-lvars (lvars lhs)
        pos-lvars (-> rhs positive lvars)
        neg-lvars (neg-lvars rhs)
        lhs-diff (clojure.set/difference lhs-lvars pos-lvars)
        neg-diff (clojure.set/difference neg-lvars pos-lvars)]
    (every? empty? [lhs-diff neg-diff])))

(defn expand-form [form]
  (clojure.walk/prewalk val->sym form))

(defn rel->map [rel]
  (if (sequential? rel)
    (let [[rel-name attrs] rel]
      (->> attrs
           (map (fn [[k v]]
                  (if (keyword? k)
                    (let [k (keyword (name rel-name) (name k))]
                      [k (rel->map v)])
                    [(val->sym k) (val->sym v)])))
           (into {})))
    (expand-form rel)))

(def default-rule {:insert true
                   :monotone true})

(defn form-atrs [form]
  (->> form
       (mapcat (fn [clause]
                 (filter vector? clause)))
       (keep (fn [[e a v]]
               (when (keyword? a)
                 a)))
       (remove (fn [attr]
                 (let [n (name attr)]
                   (= "$id" n))))
       (into #{})))

(defn dep-lvars [form]
  (->> form
       (mapcat (fn [clause]
                 (filter vector? clause)))
       (filter (fn [[e a v]]
                 (when (and  (symbol? e) (keyword? a))
                   (let [n (name a)]
                     (= "$id" n)))))
       (map first)
       distinct
       (into [])))

(defn rule [head-form body]
  (let [head (rel->map head-form)
        body (query* body)
        safe? (safe? head body)
        body-neg (negative body)
        body-pos (positive body)
        neg? (not (empty? body-neg))
        neg-attrs (form-atrs body-neg)
        pos-attrs (form-atrs [body-pos])
        dep-lvars (dep-lvars [body-pos])
        head-attrs (apply hash-set (keys head))]
    (-> default-rule
        (assoc :head-form (expand-form head-form))
        (assoc :head head)
        (assoc :body body)
        (assoc :safe? safe?)
        (assoc :neg? neg?)
        (assoc :neg-attrs neg-attrs)
        (assoc :pos-attrs pos-attrs)
        (assoc :head-attrs head-attrs)
        (assoc :dep-lvars dep-lvars))))

(defn rule+ [head-form body]
  (-> (rule head-form body)
      (assoc :deferred true)))

(defn rule> [head-form body]
  (-> (rule+ head-form body)
      (assoc :async true)
      (assoc :monotone false)))

(defn depends-on? [dependant dependancy]
  (let [dependant-attrs (:neg-attrs dependant)
        dependancy-attrs (:head-attrs dependancy)
        intersection (clojure.set/intersection dependant-attrs dependancy-attrs)]
    (not (empty? intersection))))
