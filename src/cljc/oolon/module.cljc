(ns oolon.module)

(defn normalise-spec [spec]
  (->> spec
       (partition-by keyword)
       (partition 2)
       (map (fn [[[k] v]]
              [k v]))
       (into {})))

(defn module [n spec]
  (if-not (map? spec)
    (module n (normalise-spec spec))
    (let [{:keys [state rules]} spec
          state (into {} (map (fn [s]
                                [(:name s) s])
                              state))]
      {:name n
       :state state
       :rules rules})))
