(ns s-exp.nima-test
  (:require [clojure.test :refer :all]
            [s-exp.nima :as nima]))

(defmacro with-server [opts & body]

  `(let [server# (nima/start! ~opts)]
     (try
       ~@body
       (finally (nima/stop! server#)))))

