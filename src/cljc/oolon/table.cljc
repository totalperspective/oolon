(ns oolon.table)

(defn table
  ([name keys vals]
   (table name keys vals :table))
  ([name keys vals type]
   {:name name
    :keys keys
    :vals vals
    :type type}))

(defn scratch [name keys vals]
  (table name keys vals :scratch))

(defn record [table row]
  (let [{:keys [name keys]} table
        name (clojure.core/name name)
        keys (clojure.core/keys keys)
        key (select-keys row keys)
        id-name (keyword name "$id")
        id (hash key)]
    (->> row
         (map (fn [[k v]]
                  (let [key-name (keyword name
                                          (clojure.core/name k))]
                    [key-name v])))
         (into {id-name id}))))
