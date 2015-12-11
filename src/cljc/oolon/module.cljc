(ns oolon.module)

(defn normalise-spec [spec]
  (if (map? spec)
    spec
    (->> spec
         (partition-by keyword)
         (partition 2)
         (map (fn [[[k] v]]
                [k v]))
         (into {}))))

(defn module [n spec]
  (let [{:keys [state rules import]} (normalise-spec spec)
        state (into {} (map (fn [s]
                              [(:name s) s])
                            state))]
    {:name n
     :state state
     :rules rules
     :import import}))

(defn imports [module]
  (let [modules (:import module)
        sub-modules (mapcat imports modules)]
    (distinct (into modules sub-modules))))
