(ns s-exp.mina.websocket.routing
  (:require [s-exp.mina.options :as options]
            [s-exp.mina.websocket.listener :as l])
  (:import (io.helidon.webserver WebServerConfig$Builder)
           (io.helidon.webserver.websocket WsRouting WsRouting$Builder)))

(set! *warn-on-reflection* true)

(defn set-websocket-endpoints! ^WebServerConfig$Builder
  [^WebServerConfig$Builder builder endpoints _options]
  (doto builder
    (.addRouting
     (.build ^WsRouting$Builder
      (reduce (fn [^WsRouting$Builder builder [path listener]]
                (.endpoint builder ^String path (l/make-listener listener)))
              (WsRouting/builder)
              endpoints)))))

(defmethod options/set-server-option! :websocket-endpoints
  [^WebServerConfig$Builder builder _ endpoints options]
  (set-websocket-endpoints! builder endpoints options))
