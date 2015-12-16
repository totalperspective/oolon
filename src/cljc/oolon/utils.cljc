(ns oolon.utils)

(defn now []
  #? (:clj (java.util.Date.)
           :cljs (js/Date.)))
