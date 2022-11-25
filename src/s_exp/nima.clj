(ns s-exp.nima
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (io.helidon.common.http Http$HeaderValue
                                   Http$Status)
           (io.helidon.nima.webserver ListenerConfiguration$Builder
                                      WebServer
                                      WebServer$Builder)
           (io.helidon.nima.webserver.http Handler
                                           Handler
                                           HttpRouting$Builder
                                           ServerRequest
                                           ServerResponse)))

(set! *warn-on-reflection* true)

(def default-server-options
  {:port 8080
   :default-socket {:write-queue-length 1
                    :backlog 1024
                    :max-payload-size -1}})

(defn set-server-response-headers! [^ServerResponse server-response headers]
  (run! (fn [[k v]]
          (.header server-response
                   (name k)
                   (if (coll? v)
                     ^"[Ljava.lang.String;" (into-array String (eduction (map str) v))
                     (doto ^"[Ljava.lang.String;" (make-array String 1)
                       (aset 0 (cond-> v (not (string? v)) str))))))
        headers))

(declare header-key->ring-header-key)
(eval
 `(defn ~'header-key->ring-header-key
    [k#]
    (case k#
      ~@(mapcat (juxt identity str/lower-case)
                (->> "headers.txt"
                     io/resource
                     io/reader
                     line-seq))
      (str/lower-case k#))))

(defn server-request->ring-headers
  [^ServerRequest server-request]
  (-> (reduce (fn [m ^Http$HeaderValue h]
                (assoc! m
                        (header-key->ring-header-key (.name h))
                        (.values h)))
              (transient {})
              (some-> server-request .headers))
      persistent!))

(defn send-response! [^ServerResponse server-response ring-response]
  (set-server-response-headers! server-response (:headers ring-response))
  (doto server-response
    (.status (Http$Status/create (:status ring-response 200)))
    (.send (:body ring-response))))

(defn server-request->ring-method [^ServerRequest server-request]
  (let [method (-> server-request
                   .prologue
                   .method
                   .name)]
    (case method
      "GET" :get
      "POST" :post
      "PUT" :put
      "DELETE" :delete
      "HEAD" :head
      "OPTIONS" :options
      "TRACE" :trace
      "PATCH" :patch
      (keyword (str/lower-case method)))))

(defn server-request->ring-protocol [^ServerRequest server-request]
  (let [prologue (.prologue server-request)]
    (str (.protocol prologue) "/" (.protocolVersion prologue))))

(defn server-request->ring-request [^ServerRequest server-request
                                    ^ServerResponse server-response]
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
     :scheme (case (.protocol prologue)
               "HTTP" :http
               "HTTPS" :https)
     :protocol (server-request->ring-protocol server-request)
     ;; :ssl-client-cert (some-> request .remotePeer .tlsCertificates)
     :request-method (server-request->ring-method server-request)
     :headers headers
     ::server-request server-request
     ::server-response server-response}))

(defn set-ring1-handler! ^WebServer$Builder
  [^WebServer$Builder builder handler _opts]
  (doto builder
    (.routing
     (reify java.util.function.Consumer
       (accept [_ route]
         (.any ^HttpRouting$Builder route
               (reify Handler
                 (handle [_ server-request server-response]
                   (let [ring-request (server-request->ring-request
                                       server-request
                                       server-response)
                         ring-response (handler ring-request)]
                     (send-response! server-response ring-response))))))))))

(defmulti set-server-option! (fn [_builder k _v _opts] k))

(defmethod set-server-option! :default [builder _ _ _]
  builder)

(defmethod set-server-option! :port
  [^WebServer$Builder builder _ port _]
  (.port builder (int port)))

(defmethod set-server-option! :default-socket
  [^WebServer$Builder builder _
   {:as cfg :keys [write-queue-length backlog max-payload-size receive-buffer-size]} _]
  (doto builder
    (.defaultSocket
     (reify java.util.function.Consumer
       (accept [_ listener-configuration-builder]
         (let [listener (doto ^ListenerConfiguration$Builder listener-configuration-builder
                          (.writeQueueLength (int write-queue-length))
                          (.backlog (int backlog))
                          (.maxPayloadSize (long max-payload-size)))]
           (when receive-buffer-size
             (.receiveBufferSize listener receive-buffer-size))))))))

(defmethod set-server-option! :handler
  [^WebServer$Builder builder _ handler opts]
  (set-ring1-handler! builder handler opts))

(defn server-builder
  ^WebServer$Builder
  [options]
  (reduce (fn [builder [k v]]
            (set-server-option! builder k v options))
          (WebServer/builder)
          options))

(defn start! [opts]
  (let [opts (merge-with merge default-server-options opts)]
    (-> (server-builder opts)
        (.start))))

(defn stop! [^WebServer server]
  (.stop server))

(comment
  (def r {:status 200 :body "" :headers {:foo [1 2] :bar "bay"}})
  (def s (start!
          {:default-socket
           {:write-queue-length 100
            :backlog 3000}
           :handler (fn [req] r)}))

  (stop! s))

