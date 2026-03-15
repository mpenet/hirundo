(ns s-exp.hirundo.grpc
  (:import (io.grpc.stub StreamObserver)))

(set! *warn-on-reflection* true)

(defn send!
  "Sends `msg` to the client via `observer`."
  [^StreamObserver observer msg]
  (.onNext observer msg))

(defn complete!
  "Signals successful stream completion to the client. With 2 args, sends
  `msg` first then completes — convenience for unary and single-response
  server-streaming handlers."
  ([^StreamObserver observer]
   (.onCompleted observer))
  ([^StreamObserver observer msg]
   (.onNext observer msg)
   (.onCompleted observer)))

(defn error!
  "Signals an error to the client via `observer`. `throwable` must be a
  `java.lang.Throwable`."
  [^StreamObserver observer ^Throwable throwable]
  (.onError observer throwable))
