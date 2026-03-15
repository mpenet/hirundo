(ns s-exp.hirundo.build-test
  (:require [clojure.tools.build.api :as b]))

(defn compile-java [_]
  (b/javac {:src-dirs  ["test/java"]
            :class-dir "test/classes"
            :basis     (b/create-basis {:aliases [:test]})}))
