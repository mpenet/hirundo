(ns s-exp.mina-test-runner
  (:gen-class)
  (:require [clojure.test]
            eftest.report.pretty
            [eftest.runner :as ef]))

(def default-opts
  {:dir "test"
   :capture-output? true
   :fail-fast? true
   :multithread? :namespaces
   :reporters [eftest.report.pretty/report]})

(defn- ret->exit-code
  [{:as _ret :keys [error fail]}]
  (System/exit
   (cond
     (and (pos? fail) (pos? error)) 30
     (pos? fail) 20
     (pos? error) 10
     :else 0)))

(defn combined-reporter
  "Combines the reporters by running first one directly,
  and others with clojure.test/*report-counters* bound to nil."
  [[report & rst]]
  (fn [m]
    (report m)
    (doseq [report rst]
      (binding [clojure.test/*report-counters* nil]
        (report m)))))

(defn run
  [opts]
  (let [opts (merge default-opts opts)]
    (-> (ef/find-tests (:dir opts))
        (ef/run-tests opts)
        ret->exit-code)))
