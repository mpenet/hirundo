(ns s-exp.hirundo.grpc
  (:require [clojure.core.async :as async])
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

   * `:on-next` — `(fn [msg])` — called for each incoming message
   * `:on-error` — `(fn [throwable])` — called on stream error
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

;;; core.async handler wrappers ------------------------------------------------

(defn- drain-to-observer!
  "Reads msgs from `out-ch` and forwards to `observer` until the channel
  closes, then completes the stream. Runs on an io-thread."
  [^StreamObserver observer out-ch]
  (async/io-thread
   (try
     (loop []
       (when-some [msg (async/<!! out-ch)]
         (send! observer msg)
         (recur)))
     (complete! observer)
     (catch Throwable t
       (error! observer t)))))

(defn unary-async-handler
  "Wraps `(fn [request out-ch])` as a `:unary` handler.

  The user fn receives the request and a `core.async` channel. Put exactly
  one response message onto `out-ch` then close it. Closing `out-ch`
  completes the stream.

  Options:
  * `:out-ch-fn` — zero-arg fn returning the outgoing channel (default: `#(async/chan 1)`)"
  [f & {:keys [out-ch-fn] :or {out-ch-fn #(async/chan 1)}}]
  (fn [req ^StreamObserver observer]
    (let [out-ch (out-ch-fn)]
      (drain-to-observer! observer out-ch)
      (f req out-ch))))

(defn server-stream-async-handler
  "Wraps `(fn [request out-ch])` as a `:server-stream` handler.

  The user fn receives the request and a `core.async` channel. Put any
  number of response messages onto `out-ch` then close it to end the stream.

  Options:
  * `:out-ch-fn` — zero-arg fn returning the outgoing channel (default: `#(async/chan 16)`)"
  [f & {:keys [out-ch-fn] :or {out-ch-fn #(async/chan 16)}}]
  (fn [req ^StreamObserver observer]
    (let [out-ch (out-ch-fn)]
      (drain-to-observer! observer out-ch)
      (f req out-ch))))

(defn- input-observer
  "Returns a StreamObserver that feeds incoming messages into `in-ch` and
  closes it on completion or error."
  [in-ch]
  (stream-observer
   {:on-next #(async/>!! in-ch %)
    :on-error (fn [_] (async/close! in-ch))
    :on-completed (fn [] (async/close! in-ch))}))

(defn client-stream-async-handler
  "Wraps `(fn [in-ch out-ch])` as a `:client-stream` handler.

  Incoming client messages arrive on `in-ch`; it is closed when the client
  signals completion or an error occurs. Put response messages onto `out-ch`
  and close it to end the stream.

  Options:
  * `:in-ch-fn`  — zero-arg fn returning the incoming channel (default: `#(async/chan 16)`)
  * `:out-ch-fn` — zero-arg fn returning the outgoing channel (default: `#(async/chan 1)`)"
  [f & {:keys [in-ch-fn out-ch-fn]
        :or {in-ch-fn #(async/chan 16)
             out-ch-fn #(async/chan 1)}}]
  (fn [^StreamObserver observer]
    (let [in-ch (in-ch-fn)
          out-ch (out-ch-fn)]
      (drain-to-observer! observer out-ch)
      (f in-ch out-ch)
      (input-observer in-ch))))

(defn bidi-async-handler
  "Wraps `(fn [in-ch out-ch])` as a `:bidi` handler.

  Incoming client messages arrive on `in-ch`; it is closed when the client
  signals completion or an error occurs. Put response messages onto `out-ch`
  and close it to end the stream.

  Options:
  * `:in-ch-fn`  — zero-arg fn returning the incoming channel (default: `#(async/chan 16)`)
  * `:out-ch-fn` — zero-arg fn returning the outgoing channel (default: `#(async/chan 16)`)"
  [f & {:keys [in-ch-fn out-ch-fn]
        :or {in-ch-fn #(async/chan 16)
             out-ch-fn #(async/chan 16)}}]
  (fn [^StreamObserver observer]
    (let [in-ch (in-ch-fn)
          out-ch (out-ch-fn)]
      (drain-to-observer! observer out-ch)
      (f in-ch out-ch)
      (input-observer in-ch))))
