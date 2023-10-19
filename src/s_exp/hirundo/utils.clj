(ns s-exp.hirundo.utils
  (:require [clojure.string :as str]))

(defn camel->dashed
  [s]
  (-> s
      (str/replace #"^[A-Z]+" str/lower-case)
      (str/replace #"_?([A-Z]+)"
                   (comp (partial str "-")
                         str/lower-case second))
      (str/replace #"-|_" "-")))

(defn format-key
  [k ns]
  (->> k
       camel->dashed
       (keyword (some-> ns name))))

(defn enum->map
  ([enum ns]
   (reduce (fn [m hd]
             (assoc m (format-key (.name ^Enum hd)
                                  ns)
                    hd))
           {}
           (java.util.EnumSet/allOf enum)))
  ([enum]
   (enum->map enum nil)))


