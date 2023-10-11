(ns dev.playground
  (:require
   [gniazdo.core :as ws]
   [s-exp.mina :as m]
   [s-exp.mina.websocket :as mws]))

(def r {:status 200})
(def h (fn [req] r))

(def s (m/start!
        {:host "0.0.0.0" :port 8080
         :http-hander #'h
         :websocket-endpoints {"/foo"
                               {;; :subprotocols ["chat"]
                                :error (fn [session e]
                                         (prn :err e))
                                :open (fn [session]
                                        ;; (prn :open)
                                        )
                                :close (fn [session status data]
                                         (prn :close status))
                                :message (fn [session data last]
                                           (prn :message data last)
                                           (mws/send! session data true))}}
         :write-queue-length 10240
         :connection-options {:socket-send-buffer-size 1024}}))

(def socket
  (ws/connect
   "ws://localhost:8080/foo"
    :on-receive #(prn 'received %)
   ;; :headers {"foo" "bar"}
   ;; :subprotocols ["chat, foo"]
    ))
(ws/send-msg socket "hello")

(ws/close socket)

