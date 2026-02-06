(ns s-exp.hirundo.websocket.routing
  (:require [s-exp.hirundo.options :as options]
            [s-exp.hirundo.websocket.listener :as l])
  (:import
   (io.helidon.webserver WebServerConfig$Builder)
   (io.helidon.webserver.websocket WsRouting WsRouting$Builder)
   (java.util.function Supplier)))

(set! *warn-on-reflection* true)

(defn- normalize-listener [listener-or-fn]
  (cond
    (fn? listener-or-fn) (listener-or-fn)
    (map? listener-or-fn) listener-or-fn
    :else (throw  (ex-info (str "Unexpected listener format given: "
                                (type listener-or-fn))
                           {}))))

(defn set-websocket-endpoints! ^WebServerConfig$Builder
  [^WebServerConfig$Builder builder endpoints _options]
  (doto builder
    (.addRouting
     ^WsRouting$Builder
     (reduce (fn [^WsRouting$Builder builder [path listener-or-fn]]
               (.endpoint builder ^String path
                          (reify Supplier
                            (get [_]
                              (-> listener-or-fn
                                  normalize-listener
                                  l/make-listener)))))
             (WsRouting/builder)
             endpoints))))

(defmethod options/set-server-option! :websocket-endpoints
  [^WebServerConfig$Builder builder _ endpoints options]
  (set-websocket-endpoints! builder endpoints options))
