(ns s-exp.nima
  (:require [clojure.string :as str])
  (:import (io.helidon.common.http Http$HeaderValue
                                   Http$Status)
           (io.helidon.nima.webserver WebServer WebServer$Builder)
           (io.helidon.nima.webserver.http HttpRouting$Builder
                                           Handler
                                           Handler
                                           ServerResponse
                                           ServerRequest)))

(set! *warn-on-reflection* true)

(def default-server-options {:port 8080})

(defn set-server-response-headers! [^ServerResponse server-response headers]
  (run! (fn [[k v]]
          (.header server-response
                   (name k)
                   ^"[Ljava.lang.String;"
                   (into-array String
                               (if (coll? v)
                                 (map str v)
                                 [(str v)]))))
        headers))

(defn server-request->ring-headers
  [^ServerRequest server-request]
  (-> (reduce (fn [m ^Http$HeaderValue h]
                (assoc! m
                        (keyword (str/lower-case (.name h)))
                        (.values h)))
              (transient {})
              (some-> server-request .headers))
      persistent!))

(defn send-response! [^ServerResponse server-response ring-response]
  (set-server-response-headers! server-response (:headers ring-response))
  (doto server-response
    (.status (Http$Status/create (:status ring-response 200)))
    (.send (:body ring-response))))

(defn server-request->ring-request [^ServerRequest server-request]
  (let [headers (server-request->ring-headers server-request)
        prologue (.prologue server-request)
        remote-peer (.remotePeer server-request)
        local-peer (.localPeer server-request)
        content (.content server-request)]
    {:body (when-not (.consumed content) (.inputStream content))
     :server-port (.port local-peer)
     :server-name (.host local-peer)
     :remote-addr (.address remote-peer)
     :uri (.rawPath (.path server-request))
     :query-string (.query server-request)
     :scheme (.protocol prologue)
     :protocol (.protocolVersion prologue)
     ;; :ssl-client-cert (some-> request .remotePeer .tlsCertificates)
     :request-method (keyword (str/lower-case (.name (.method prologue))))
     :headers headers}))

(defmulti set-server-option! (fn [_builder k _v] k))

(defmethod set-server-option! :default [builder _ _]
  builder)

(defmethod set-server-option! :port
  [^WebServer$Builder builder _ port]
  (.port builder (int port)))

(defn server-builder [options]
  (reduce (fn [builder [k v]]
            (set-server-option! builder k v))
          (WebServer/builder)
          options))

(defn set-handler! ^WebServer$Builder [^WebServer$Builder builder opts handler]
  (doto builder
    (.routing
     (reify java.util.function.Consumer
       (accept [_ route]
         (.any ^HttpRouting$Builder route
               (reify Handler
                 (handle [_ server-request server-response]
                   (let [ring-request (server-request->ring-request server-request)
                         ring-response (handler ring-request)]
                     (send-response! server-response ring-response))))))))))

(defn make-server [opts handler]
  (let [opts (merge default-server-options opts)]
    (-> (server-builder opts)
        (set-handler! opts handler)
        (.start))))

(defn stop! [^WebServer server]
  (.stop server))

(def s (make-server {}
                    (fn [req]
                      (prn :req req)
                      {:status 200 :body (str (System/currentTimeMillis)) :headers {"stuff" "lolo"}})))

;; (stop! s)


