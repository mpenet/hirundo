(ns s-exp.mina
  (:require [s-exp.mina.http.routing]
            [s-exp.mina.options :as options]
            [s-exp.mina.websocket]
            [s-exp.mina.websocket.routing])
  (:import (io.helidon.webserver WebServer WebServerConfig WebServerConfig$Builder)))

(set! *warn-on-reflection* true)

(def default-options {:connection-provider false})

(defn- server-builder
  ^WebServerConfig$Builder
  [options]
  (reduce (fn [builder [k v]]
            (options/set-server-option! builder k v options))
          (WebServerConfig/builder)
          options))

(defn start!
  "Starts a new server.
  
  `options` can contain:

  * `:http-handler` - ring http handler function

  * `:websocket-endpoints` - websocket endpoints (map-of string-endpoint handler-fns-map), where handler if can be of `:message`, `:ping`, `:pong`, `:close`, `:error`, `:open`, `:http-upgrade`. `handler-fns-map` can also contain 2 extra keys, `:extensions`, `:subprotocols`, which are sets of exts/subprotos acceptable by the server.
  
  * `:host` - host of the default socket
  
  * `:port` - port the server listens to, default to 8080

  * `:default-socket` - map-of :write-queue-length :backlog :max-payload-size :receive-buffer-size `:connection-options`(map-of `:socket-receive-buffer-size` `:socket-send-buffer-size` `:socket-reuse-address` `:socket-keep-alive` `:tcp-no-delay` `:read-timeout` `:connect-timeout`)

  * `:tls` - a `io.helidon.nima.common.tls.Tls` instance"
  ([http-handler options]
   (start! (assoc options :http-handler http-handler)))
  ([options]
   (-> (server-builder (merge default-options options))
       .build
       (.start))))

(defn stop!
  "Stops server, noop if already stopped"
  [^WebServer server]
  (.stop server))
