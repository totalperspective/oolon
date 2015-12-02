(ns oolon.datalog)

(defn val->sym [val]
  (cond
    (= :_ val) '_
    (and (keyword? val)
         (= \? (second (str val)))) (symbol (name val))
    :else val))
