(ns oolon.datalog)

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

(defn query [& rels]
  (when (seq rels)
    (->> rels
         (mapcat (fn [rel]
                   (if (keyword? (first rel))
                     (apply rel->eavt rel)
                     [rel])))
         (into []))))
