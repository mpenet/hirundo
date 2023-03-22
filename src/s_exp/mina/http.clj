(ns s-exp.mina.http
  (:require [s-exp.mina.http.handler]
            [s-exp.mina.http.options :as options])
  (:import (io.helidon.nima.webserver WebServer WebServer$Builder)))

(set! *warn-on-reflection* true)

(def default-options {:connection-provider false})

(defn- server-builder
  ^WebServer$Builder
  [options]
  (reduce (fn [builder [k v]]
            (options/set-server-option! builder k v options))
          (WebServer/builder)
          options))

(defn start!
  "Starts a new server.
  
  `options` can contain:

  * `:host` - host of the default socket
  
  * `:port` - port the server listens to, default to 8080

  * `:default-socket` - map-of :write-queue-length :backlog :max-payload-size :receive-buffer-size `:connection-options`(map-of `:socket-receive-buffer-size` `:socket-send-buffer-size` `:socket-reuse-address` `:socket-keep-alive` `:tcp-no-delay` `:read-timeout` `:connect-timeout`)

  * `:tls` - a `io.helidon.nima.common.tls.Tls` instance"
  ([handler options]
   (start! (assoc options :handler handler)))
  ([options]
   (-> (server-builder (merge default-options options))
       (.start))))

(defn stop!
  "Stops server, noop if already stopped"
  [^WebServer server]
  (.stop server))

;; (def r {:status 200})
;; ;; (def h (fn [req]
;; ;;          (prn (counted? (:headers req)))
;; ;;          r))
;; ;; (def h (fn [_]
;; ;;          ;; (prn :aasdf ((:headers _) "accept"))
;; ;;          ;; (prn (:headers _))
;; ;;          r))
;; (def s (start!
;;         r
;;         {:host "0.0.0.0" :port 8080 :default-socket {:write-queue-length 10240}}))

;; (stop! s)

;; https://api.github.com/repos/mpenet/mina/commits/main?per_page=1


