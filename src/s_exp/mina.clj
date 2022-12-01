(ns s-exp.mina
  (:require [s-exp.mina.options :as options]
            [s-exp.mina.request :as request]
            [s-exp.mina.response :as response])
  (:import (io.helidon.nima.webserver WebServer WebServer$Builder)
           (io.helidon.nima.webserver.http Handler Handler HttpRouting$Builder)))

(set! *warn-on-reflection* true)

(defn set-ring1-handler! ^WebServer$Builder
  [^WebServer$Builder builder handler _options]
  (doto builder
    (.routing
     (reify java.util.function.Consumer
       (accept [_ route]
         (.any ^HttpRouting$Builder route
               (reify Handler
                 (handle [_ server-request server-response]
                   (->> (request/ring-request server-request server-response)
                        handler
                        (response/set-response! server-response))))))))))

(defmethod options/set-server-option! :handler
  [^WebServer$Builder builder _ handler options]
  (set-ring1-handler! builder handler options))

(defn server-builder
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
   (-> (server-builder options)
       (.start))))

(defn stop!
  "Stops server, noop if already stopped"
  [^WebServer server]
  (.stop server))

;; (def r {:status 200 :body (java.io.ByteArrayInputStream. (.getBytes "bar")) :headers {:foo [1 2] :bar ["bay"]}})
;; (def r {:status 200 :body ["foo\n" "bar"] :headers {:foo [1 2] :bar ["bay"]}})
;; (def r {:status 200 :body nil})
;; (def s (start! (fn [req] r) {:host "0.0.0.0" :port 8080}))

;; (stop! s)

;; https://api.github.com/repos/mpenet/mina/commits/main?per_page=1


