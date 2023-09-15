(ns s-exp.mina.options
  (:import (io.helidon.common.socket SocketOptions$Builder)
           (io.helidon.webserver ;; ListenerConfiguration$Builder
            ServerListener
            WebServerConfig$Builder)
           (java.time Duration)))

;; (ServerListener/config)

(set! *warn-on-reflection* true)
(defmulti set-server-option! (fn [_builder k _v _options] k))

(defmethod set-server-option! :default [builder _ _ _]
  builder)

(defmethod set-server-option! :host
  [^WebServerConfig$Builder builder _ host _]
  (.host builder host))
;; io.helidon.webserver.WebServerConfig$BuilderBaseio.helidon.webserver.WebServerConfig$BuilderBase
(defmethod set-server-option! :port
  [^WebServerConfig$Builder builder _ port _]
  (.port builder (int port)))

#_(defn- set-connection-options!
    [^SocketOptions$Builder socket-options-builder
     {:keys [socket-receive-buffer-size socket-send-buffer-size
             socket-reuse-address socket-keep-alive tcp-no-delay
             read-timeout connect-timeout]}]
    (when socket-receive-buffer-size
      (.socketReceiveBufferSize socket-options-builder
                                (int socket-receive-buffer-size)))

    (when socket-send-buffer-size
      (.socketSendBufferSize socket-options-builder
                             (int socket-send-buffer-size)))

    (when socket-reuse-address
      (.socketReuseAddress socket-options-builder
                           (boolean socket-reuse-address)))

    (when socket-keep-alive
      (.socketKeepAlive socket-options-builder
                        (boolean socket-keep-alive)))
    (when tcp-no-delay
      (.tcpNoDelay socket-options-builder
                   (boolean tcp-no-delay)))

    (when read-timeout
      (.readTimeout socket-options-builder
                    (Duration/ofMillis read-timeout)))
    (when connect-timeout
      (.connectTimeout socket-options-builder
                       (Duration/ofMillis connect-timeout))))

#_(defn- set-listener-configuration!
    [^ListenerConfiguration$Builder listener-configuration-builder
     {:keys [write-queue-length backlog max-payload-size receive-buffer-size
             connection-options]}]
    (when backlog
      (.backlog listener-configuration-builder
                (int backlog)))

    (when max-payload-size
      (.maxPayloadSize listener-configuration-builder
                       (long max-payload-size)))

    (when write-queue-length
      (.writeQueueLength listener-configuration-builder
                         (int write-queue-length)))

    (when receive-buffer-size
      (.receiveBufferSize listener-configuration-builder
                          (int receive-buffer-size)))

    #_(when (seq connection-options)
        (.connectionOptions listener-configuration-builder
                            (reify java.util.function.Consumer
                              (accept [_ socket-options-builder]
                                (set-connection-options! socket-options-builder
                                                         connection-options))))))

#_(defmethod set-server-option! :default-socket
    [^WebServerConfig$Builder builder _ default-socket _]
    (doto builder
      (.defaultSocket
       (reify java.util.function.Consumer
         (accept [_ listener-configuration-builder]
           (set-listener-configuration! listener-configuration-builder
                                        default-socket))))))

(defmethod set-server-option! :tls
  [^WebServerConfig$Builder builder _ tls _]
  (doto builder (.tls tls)))


