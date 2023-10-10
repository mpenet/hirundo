(ns s-exp.mina.websocket
  (:import (io.helidon.common.buffers BufferData)
           (io.helidon.websocket WsSession)))

(set! *warn-on-reflection* true)

(defprotocol ToBufferData
  (buffer-data [x]))

(extend-protocol ToBufferData

  (Class/forName "[B")
  (buffer-data [ba]
    (BufferData/create ^"[B" ba))

  String
  (buffer-data [s]
    (BufferData/create s))

  clojure.lang.Sequential
  (buffer-data [s]
    (BufferData/create ^java.util.List s))

  BufferData
  (buffer-data [bd]
    bd)

  nil
  (buffer-data [x]
    (BufferData/empty)))

(defn send!
  [^WsSession ws-session msg last?]
  (if (string? msg)
    (.send ws-session ^String msg
           (boolean last?))
    (.send ws-session
           ^BufferData (buffer-data msg)
           (boolean last?))))

(defn ping!
  [^WsSession ws-session data]
  (.ping ws-session (buffer-data data)))

(defn pong!
  [^WsSession ws-session data]
  (.pong ws-session (buffer-data data)))

(defn close!
  [^WsSession ws-session code reason]
  (.close ws-session (int code) (str reason)))

(defn terminate!
  [^WsSession ws-session]
  (.terminate ws-session))
