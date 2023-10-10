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

  * `:websocket-endpoints` - websocket endpoints (map-of string-endpoint handler-fns-map), where handler if can be of `:message`, `:ping`, `:pong`, `:close`, `:error`, `:open`, `:http-upgrade`.
  
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

;; (def r {:status 200})
;; (def h (fn [req]
;;          {:body (str (counted? (:headers req)))}))
;; (def h (fn [_]
;;          ;; (prn :aasdf ((:headers _) "accept"))
;;          ;; (prn (:headers _))
;;          r))
;; (stop! s)
;; (require 's-exp.mina.websocket)
;; (stop! s)
;; (do
;;   (when (resolve 's) (eval `(stop! s)))
;;   (def s (start!
;;           {:host "0.0.0.0" :port 8080
;;            :websocket-endpoints {"/foo"
;;                                  {;; :subprotocols ["chat"]
;;                                   :error (fn [session e]
;;                                            (prn :err e))
;;                                   :ping (fn [session data]
;;                                           (prn :ping))
;;                                   :pong (fn [session data]
;;                                           (prn :pong))
;;                                   :open (fn [session]
;;                                          ;; (prn :open)
;;                                           (prn :open)
;;                                           (prn session)
;;                                           ;; (s-exp.mina.websocket/send! session "open" true)
;;                                           ;; (prn (.subProtocol session))
;;                                          ;; ;; (prn session)
;;                                           ;; (prn :sent)
;;                                           ;; (s-exp.mina.websocket/close! session 0 "asdf")
;;                                           )
;;                                   :close (fn [session status data]
;;                                            (prn :close status))
;;                                  ;; :http-upgrade
;;                                  ;; (fn [p h]
;;                                  ;;   (prn :http-upgrade p h)
;;                                  ;;   (java.util.Optional/of h))
;;                                   :message (fn [session data last?]
;;                                              (prn :message data last?)
;;                                              (s-exp.mina.websocket/send! session data true))}}
;;            :write-queue-length 10240
;;            :connection-options {:socket-send-buffer-size 1024}})))

;; (require '[gniazdo.core :as ws])

;; (when (resolve 'socket) (eval `(ws/close socket)))
;; (def socket
;;   (ws/connect
;;    "ws://localhost:8080/foo"
;;    :on-receive #(prn 'received %)
;;    ;; :headers {"foo" "bar"}
;;    ;; :subprotocols ["chat, foo"]
;;    ))
;; (ws/send-msg socket "hello")

;; (ws/close socket)

;; https://api.github.com/repos/mpenet/mina/commits/main?per_page=1

