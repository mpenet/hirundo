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

(defn set-response-headers! [^ServerResponse server-response headers]
  (run! (fn [[k v]]
          (.header server-response
                   (name k)
                   ^"[Ljava.lang.String;"
                   (into-array String
                               (if (coll? v)
                                 (map str v)
                                 [(str v)]))))
        headers))

(defn http-request-headers->ring-headers
  [^ServerRequest http-request]
  (-> (reduce (fn [m ^Http$HeaderValue h]
                (assoc! m
                        (keyword (str/lower-case (.name h)))
                        (.values h)))
              (transient {})
              (some-> http-request .headers))
      persistent!))

(defn http-response! [^ServerResponse http-response ring-response]
  (set-response-headers! http-response (:headers ring-response))
  (doto http-response
    (.status (Http$Status/create (:status ring-response 200)))
    (.send (:body ring-response))))

(defn http-request->ring-request [^ServerRequest request]
  (let [headers (http-request-headers->ring-headers request)
        prologue (.prologue request)
        remote-peer (.remotePeer request)
        local-peer (.localPeer request)
        content (.content request)]
    {:body (when-not (.consumed content) (.inputStream content))
     :server-port (.port local-peer)
     :server-name (.host local-peer)
     :remote-addr (.address remote-peer)
     :uri (.rawPath (.path request))
     :query-string (.query request)
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
                 (handle [_ http-request http-response]
                   (let [ring-request (http-request->ring-request http-request)
                         ring-response (handler ring-request)]
                     (http-response! http-response ring-response))))))))))

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


