(ns oolon.table)

(def opt? #{:scratch :deferred})

(defn table
  ([name keys]
   (table name keys {} :table))
  ([name keys vals]
   (table name keys vals :table))
  ([name keys vals & opts]
   (let [base (->> opts
                   (filter opt?)
                   (map (fn [o]
                          [o true]))
                   (into {}))]
     (merge base {:name name
                  :keys keys
                  :vals vals}))))

(defn scratch
  ([name keys]
   (scratch name keys {}))
  ([name keys vals]
   (table name keys vals :scratch)))

(defn defer [table]
  (assoc table :deferred true))

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

(defn rel [table]
  (let [{:keys [keys vals]} table
        attrs (->> (merge keys vals)
                   (map (fn [[k _]]
                          [k (symbol (str "?" (name k)))]))
                   (into {}))]
    [(:name table) attrs]))

(defn add-id [table record]
  (if table
    (let [{:keys [name keys]} table
          name (clojure.core/name name)
          keys (map (fn [k]
                      (keyword name (clojure.core/name k)))
                    (clojure.core/keys keys))
          key (select-keys record keys)
          id-name (keyword name "$id")
          id (hash key)]
      (assoc record id-name id))
    record))
