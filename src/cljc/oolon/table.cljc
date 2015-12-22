(ns oolon.table)

(def opt? #{:scratch
            :channel
            :loopback
            :input
            :output})

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

(defn input
  ([name keys]
   (scratch name keys {}))
  ([name keys vals]
   (table name keys vals :scratch :input)))

(defn output
  ([name keys]
   (scratch name keys {}))
  ([name keys vals]
   (table name keys vals :scratch :output)))

(defn channel
  ([name keys]
   (channel name keys {}))
  ([name keys vals]
   (table name keys vals :scratch :channel)))

(defn loopback
  ([name keys]
   (loopback name keys {}))
  ([name keys vals]
   (table name keys vals :scratch :channel :loopback)))

(defn hash-fn [key]
  (let [hash-val (hash (into {} key))]
    hash-val))

(defn fattr->eattr [table fattr]
  (keyword (:name table)
           fattr))

(defn rel [table]
  (let [{:keys [keys vals]} table
        attrs (->> (merge keys vals)
                   (map (fn [[k _]]
                          [k (symbol (str "?" (name k)))]))
                   (into {}))]
    [(:name table) attrs]))

(defn entity->fact [entity]
  (let [table (first (keep (fn [[k v]]
                             (when (= "$id" (name k))
                               (namespace k)))
                           entity))
        attrs (into {} (keep (fn [[k v]]
                               (when (and (= table (namespace k))
                                          (not (= "$id" (name k))))
                                 [(keyword (name k)) v]))
                               entity))]
    [(keyword table) attrs]))

(defn add-id [table record]
  (if table
    (let [{:keys [name keys]} table
          name (clojure.core/name name)
          keys (map (fn [k]
                      (keyword name (clojure.core/name k)))
                    (clojure.core/keys keys))
          key (select-keys record keys)
          id-name (keyword name "$id")
          id (hash-fn key)]
      (assoc record id-name id))
    record))

(defn record [table row]
  (let [{:keys [name keys]} table
        name (clojure.core/name name)]
    (->> row
         (map (fn [[k v]]
                (let [key-name (keyword name
                                        (clojure.core/name k))]
                  [key-name v])))
         (into {})
         (add-id table))))
