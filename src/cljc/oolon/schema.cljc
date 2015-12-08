(ns oolon.schema)

(def type-map
  {:keyword  :db.type/keyword
   :string   :db.type/string
   :boolean  :db.type/boolean
   :long     :db.type/long
   :bigint   :db.type/bigint
   :float    :db.type/float
   :double   :db.type/double
   :bigdec   :db.type/bigdec
   :ref      :db.type/ref
   :instant  :db.type/instant
   :uuid     :db.type/uuid
   :uri      :db.type/uri
   :bytes    :db.type/bytes})

(defn type->valueType [type]
  {:pre [(contains? type-map type)]}
  (get type-map type))

(defn schema [table]
  (let [{t-name :name t-keys :keys t-vals :vals} table
        ns-name (name t-name)
        ident (fn [k]
                (keyword ns-name (name k)))
        attrs (into t-keys t-vals)
        key-names (into #{} (keys t-keys))
        id-attr (keyword ns-name "$id")]
    (conj
     (mapv (fn [[k t]]
             {:db/ident (ident k)
              :db/valueType (type->valueType t)
              :db/index true})
           attrs)
     {:db/ident id-attr
      :db/valueType :db.type/long
      :db/index true
      :db/unique :db.unique/identity})))
