(ns s-exp.hirundo.websocket.routing
  (:require [s-exp.hirundo.options :as options]
            [s-exp.hirundo.websocket.listener :as l])
  (:import (io.helidon.webserver WebServerConfig$Builder)
           (io.helidon.webserver.websocket WsRouting WsRouting$Builder)
           (io.helidon.websocket WsListener)
           (java.util.function Supplier)))

(set! *warn-on-reflection* true)

(defn set-websocket-endpoints! ^WebServerConfig$Builder
  [^WebServerConfig$Builder builder endpoints _options]
  (doto builder
    (.addRouting
     ^WsRouting$Builder
     (reduce (fn [^WsRouting$Builder builder [path listener]]
               (.endpoint builder
                          ^String path
                          (reify Supplier
                            (get [_]
                              (cond
                                (map? listener) (l/listener listener)
                                (ifn? listener) (l/listener (listener))
                                (instance? WsListener listener) listener
                                :else
                                (throw
                                 (ex-info (format "Invalid listener type: %s"
                                                  (type listener))
                                          {:type :s-exp.hirundo.websocket.routing/invalid-listener})))))))
             (WsRouting/builder)
             endpoints))))

(defmethod options/set-server-option! :websocket-endpoints
  [^WebServerConfig$Builder builder _ endpoints options]
  (set-websocket-endpoints! builder endpoints options))
