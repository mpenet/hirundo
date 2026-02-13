(ns s-exp.hirundo.sse
  (:require [clojure.core.async :as async]
            [s-exp.hirundo.compression.brotli :as brotli]
            [s-exp.hirundo.http.response :as response])
  (:import (io.helidon.webserver.http ServerResponse)
           (java.io OutputStream)
           (java.nio.charset StandardCharsets)))

(set! *warn-on-reflection* true)

(defn- append-op!
  [^StringBuilder sb op val]
  (doto sb
    (.append (name op))
    (.append ": ")
    (.append val)
    (.append "\n")))

(defn- append-comment!
  [^StringBuilder sb comment]
  (.append sb ":")
  (when comment (.append sb comment))
  (.append sb "\n"))

(defn event
  "Formats SSE event data. `msg` can be a string or a map with keys :event,
  :data, :id, :retry."
  ^String
  [& {:as msg}]
  (let [sb (StringBuilder.)]
    (let [{:keys [event data id retry comment]} msg]
      (when (contains? msg :comment)
        (append-comment! sb comment))
      (when event (append-op! sb :event event))
      (when id (append-op! sb :id id))
      (when retry (append-op! sb :retry retry))
      (run! #(append-op! sb :data %) data))
    (.append sb "\n")
    (.toString sb)))

(defn- connection-heartbeat!
  "Periodically puts heartbeat bytes (SSE comment) onto `input-ch` to detect
  client disconnect via write failure. Stops when `input-ch` is closed."
  [input-ch close-ch heartbeat-ms]
  (async/go-loop []
    (async/<! (async/timeout heartbeat-ms))
    (if (async/>! input-ch {:comment nil})
      (recur)
      (async/close! close-ch))))

(defn- brotli-request?
  [request]
  (some->> (get-in request [:headers "accept-encoding"])
           (re-find #"(?i)\bbr\b")))

(defn stream!
  ([request & {:keys [headers compression heartbeat-ms close-ch input-ch]
               :or {heartbeat-ms 1500
                    compression {:type :brotli :quality 4 :window-size 18}}}]
   (let [^ServerResponse server-response (:s-exp.hirundo.http.request/server-response request)
         close-ch (or close-ch (async/promise-chan))
         input-ch (or input-ch (async/chan 10))
         brotli-compression (and (= :brotli (:type compression))
                                 (brotli-request? request))]
     (response/set-headers! server-response
                            (cond-> {"content-type" "text/event-stream"
                                     "cache-control" "no-cache"
                                     "connection" "keep-alive"}
                              brotli-compression
                              (assoc "content-encoding" "br")
                              :then
                              (into headers)))
     (response/set-status! server-response 200)
     (connection-heartbeat! input-ch close-ch heartbeat-ms)
     (async/io-thread
      (try
        (with-open [^OutputStream raw-os (.outputStream server-response)
                    ^OutputStream os (cond-> raw-os
                                       brotli-compression
                                       (brotli/output-stream compression))]

          (loop []
            (let [[val ch] (async/alts!! [input-ch close-ch])]
              (when (and val (= input-ch ch))
                (let [^bytes bs (.getBytes (event val)
                                           StandardCharsets/UTF_8)]
                  (.write os bs)
                  (.flush os)
                  (when brotli-compression
                    (.flush raw-os))
                  (recur))))))
        ;; these will trigger on-close, causing jump out of the loop triggering
        ;; closing of both channels
        (catch java.net.SocketException _e)
        (catch java.io.UncheckedIOException _e)
        (catch io.helidon.webserver.ServerConnectionException _e)
        (finally
          (async/close! input-ch)
          (async/close! close-ch))))
     {:input-ch input-ch :close-ch close-ch})))
