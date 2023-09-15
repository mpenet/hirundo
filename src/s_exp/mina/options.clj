(ns s-exp.mina.options
  (:import (io.helidon.common.socket SocketOptions$Builder)
           (io.helidon.common.tls Tls)
           (io.helidon.webserver WebServerConfig$Builder)
           (java.time Duration)))

(set! *warn-on-reflection* true)

(defmulti set-server-option! (fn [_builder k _v _options] k))

(defmethod set-server-option! :default [builder _ _ _]
  builder)

(defmethod set-server-option! :host
  [^WebServerConfig$Builder builder _ host _]
  (.host builder host))

(defmethod set-server-option! :port
  [^WebServerConfig$Builder builder _ port _]
  (.port builder (int port)))

(defmethod set-server-option! :backlog
  [^WebServerConfig$Builder builder _ backlog _]
  (.backlog builder (int backlog)))

(defmethod set-server-option! :max-payload-size
  [^WebServerConfig$Builder builder _ max-payload-size _]
  (.maxPayloadSize builder (long max-payload-size)))

(defmethod set-server-option! :write-queue-length
  [^WebServerConfig$Builder builder _ write-queue-length _]
  (.writeQueueLength builder (long write-queue-length)))

(defmethod set-server-option! :receive-buffer-size
  [^WebServerConfig$Builder builder _ receive-buffer-size _]
  (.receiveBufferSize builder (int receive-buffer-size)))

(defn- set-connection-options!
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

(defmethod set-server-option! :connection-options
  [^WebServerConfig$Builder builder _ connection-options _]
  (.connectionOptions builder
                      (reify java.util.function.Consumer
                        (accept [_ socket-options-builder]
                          (set-connection-options! socket-options-builder
                                                   connection-options)))))

(defmethod set-server-option! :tls
  [^WebServerConfig$Builder builder _ tls-config _]
  (doto builder (.tls ^Tls tls-config)))


