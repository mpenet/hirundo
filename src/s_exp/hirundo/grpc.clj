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

(defn stream-observer
  "Returns a `StreamObserver` backed by callback fns supplied as a map:

   * `:on-next`      — `(fn [msg])` — called for each incoming message
   * `:on-error`     — `(fn [throwable])` — called on stream error
   * `:on-completed` — `(fn [])` — called when the client signals completion

  All keys are optional; unset callbacks are no-ops."
  ^StreamObserver [{:keys [on-next on-error on-completed]}]
  (reify StreamObserver
    (onNext [_ msg]
      (when on-next (on-next msg)))
    (onError [_ t]
      (when on-error (on-error t)))
    (onCompleted [_]
      (when on-completed (on-completed)))))
