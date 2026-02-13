(ns s-exp.hirundo.sse
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [s-exp.hirundo.compression.brotli :as brotli]
            [s-exp.hirundo.http.response :as response])
  (:import (io.helidon.webserver.http ServerResponse)
           (java.io OutputStream)
           (java.nio.charset StandardCharsets)))

(set! *warn-on-reflection* true)

(defn- format-event
  "Formats SSE event data. `msg` can be a string or a map with keys :event,
  :data, :id, :retry."
  ^String [msg]
  (let [sb (StringBuilder.)]
    (if (map? msg)
      (let [{:keys [event data id retry]} msg]
        (when event (StringBuilder/.append sb (str "event: " event "\n")))
        (when id (StringBuilder/.append sb (str "id: " id "\n")))
        (when retry (StringBuilder/.append sb (str "retry: " retry "\n")))
        (when data
          (run! (fn [line]
                  (StringBuilder/.append sb (str "data: " line "\n")))
                (str/split (str data) #"\n"))))
      (StringBuilder/.append sb (str "data: " msg "\n")))
    (StringBuilder/.append sb "\n")
    (StringBuilder/.toString sb)))

(defn- monitor-connection!
  "Periodically puts heartbeat bytes (SSE comment) onto `input-ch` to detect
  client disconnect via write failure. Stops when `input-ch` is closed."
  [input-ch heartbeat-ms]
  (async/go-loop []
    (async/<! (async/timeout heartbeat-ms))
    (when (async/>! input-ch ":\n")
      (recur))))

(defn response!
  "Sets up an SSE connection from within a ring handler. Takes the ring request
  map and returns a map with:
    :input-ch - a core.async channel to send messages to. Messages can be
                strings (sent as SSE data fields), maps with keys :event,
                :data, :id, :retry, or byte arrays (written raw). Closing the
                channel will close the SSE connection.

  Options map accepts:
    :headers       - extra headers to merge into the response
    :buffer-size   - core.async channel buffer size (default 16)
    :compress      - when truthy, enables brotli compression. Can be `true` for
                     defaults or a map with :quality (0-11) and :window-size (10-24)
    :input-ch      - user-provided core.async channel to use instead of creating one
    :heartbeat-ms  - interval in ms for heartbeat SSE comments to detect client
                     disconnect (default 1500)

  The ring handler should return nil after calling this, as the response is
  handled directly."
  ([request & {:keys [headers buffer-size compress input-ch heartbeat-ms]
               :or {buffer-size 16
                    heartbeat-ms 1500}}]
   (let [^ServerResponse server-response (:s-exp.hirundo.http.request/server-response request)
         compress-opts (when compress
                         (if (map? compress) compress {}))
         input-ch (or input-ch (async/chan buffer-size))]
     (response/set-headers! server-response
                            (cond-> {"content-type" "text/event-stream"
                                     "cache-control" "no-cache"
                                     "connection" "keep-alive"}
                              compress-opts
                              (assoc "content-encoding" "br")
                              :then
                              (into headers)))
     (response/set-status! server-response 200)
     (try
       (with-open [^OutputStream os
                   (cond-> (.outputStream server-response)
                     compress-opts
                     (brotli/output-stream compress-opts))]
         (monitor-connection! input-ch heartbeat-ms)
         (loop []
           (when-let [val (async/<!! input-ch)]
             (let [^bytes bs (.getBytes (format-event val)
                                        StandardCharsets/UTF_8)]
               (.write os bs)
               (.flush os)
               (recur)))))
       (catch Exception _e
         ;; FIXME check for specific failures, ex failure to write & co
         )
       (finally
         (async/close! input-ch))))))
