(ns s-exp.hirundo.websocket.listener
  (:require [clojure.string :as str]
            [s-exp.hirundo.http.request :as r])
  (:import (io.helidon.common.buffers BufferData)
           (io.helidon.http HeaderValues Headers)
           (io.helidon.http HttpPrologue)
           (io.helidon.http WritableHeaders)
           (io.helidon.websocket WsListener WsSession WsUpgradeException)
           (java.util Optional)))

(set! *warn-on-reflection* true)

;; Client must send Sec-WebSocket-Version and Sec-WebSocket-Key.
;; Server must confirm the protocol by returning Sec-WebSocket-Accept.
;; Client may send a list of application subprotocols via Sec-WebSocket-Protocol.
;; Server must select one of the advertised subprotocols and return it via
;; Sec-WebSocket-Protocol. If the server does not support any, then the
;; connection is aborted.
;; Client may send a list of protocol extensions in Sec-WebSocket-Extensions.
;; Server may confirm one or more selected extensions via
;; Sec-WebSocket-Extensions. If no extensions are provided, then the connection
;; proceeds without them.
;; Finally, once the preceding handshake is complete, and if the handshake is
;; successful, the connection can now be used as a two-way communication channel
;; for exchanging WebSocket messages. From here on, there is no other explicit
;; HTTP communication between the client and server, and the WebSocket protocol
;; takes over.

(defn- split-header-value
  [header-value]
  (->> (str/split header-value #",")
       (map str/trim)))

(defn- header-negotiate
  [headers allowed-values header-name]
  (when (seq allowed-values)
    (if-let [selected-value (reduce (fn [_ x]
                                      (when (contains? allowed-values x)
                                        (reduced x)))
                                    nil
                                    (some-> (get headers header-name)
                                            split-header-value))]
      {header-name selected-value}
      (throw (WsUpgradeException. (format "Failed negotiation for %s"
                                          header-name))))))

(defn negotiate-subprotocols!
  [headers allowed-sub-protocols]
  (header-negotiate headers
                    allowed-sub-protocols
                    "sec-websocket-protocol"))

(defn negotiate-extensions!
  [headers allowed-extensions]
  (header-negotiate headers
                    allowed-extensions
                    "sec-websocket-extensions"))

(defn http-upgrade-default
  [{:as ring-request
    ::keys [allowed-subprotocols
            allowed-extensions]}]
  (merge (negotiate-subprotocols! (:headers ring-request)
                                  allowed-subprotocols)
         (negotiate-extensions! (:headers ring-request)
                                allowed-extensions)))

(defn headers-response [headers-map]
  (let [wh (WritableHeaders/create)]
    (run! (fn [[k v]]
            (.set wh
                  (if (sequential? v)
                    (HeaderValues/create (name k)
                                         ^java.util.Collection v)
                    (HeaderValues/create (name k)
                                         (str v)))))
          headers-map)
    (Optional/of wh)))

(defn make-listener
  ^WsListener [{:as _listener
                :keys [message ping pong close error open http-upgrade
                       subprotocols extensions]
                :or {message (constantly nil)
                     ping (constantly nil)
                     pong (constantly nil)
                     close (constantly nil)
                     error (constantly nil)
                     open (constantly nil)}}]
  (let [subprotocols (-> subprotocols not-empty set)
        extensions (-> extensions not-empty set)]
    (reify WsListener
      (^void onMessage [_ ^WsSession session ^String data ^boolean last]
        (message session data last))
      (^void onMessage [_ ^WsSession session ^BufferData data ^boolean last]
        (message session data last))
      (^void onPing [_ ^WsSession session ^BufferData data]
        (ping session data))
      (^void onPong [_ ^WsSession session ^BufferData data]
        (pong session data))
      (^void onClose [_ ^WsSession session ^int status ^String reason]
        (close session status reason))
      (^void onError [_ ^WsSession session ^Throwable e]
        (error session e))
      (^void onOpen [_ ^WsSession session]
        (open session))
      (^Optional onHttpUpgrade [_ ^HttpPrologue http-prologue ^Headers headers]
        (let [ring-request {:method (r/ring-method http-prologue)
                            :protocol (r/ring-protocol http-prologue)
                            :headers (r/ring-headers headers)
                            ::allowed-subprotocols subprotocols
                            ::allowed-extensions extensions
                            ::http-prologue http-prologue
                            ::headers headers}]
          (headers-response
           (if http-upgrade
             (http-upgrade ring-request)
             (http-upgrade-default ring-request))))))))
