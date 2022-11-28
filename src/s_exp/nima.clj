(ns s-exp.nima
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (io.helidon.common.http Http$HeaderValue Http$Status)
           (io.helidon.nima.webserver ListenerConfiguration$Builder WebServer WebServer$Builder)
           (io.helidon.nima.webserver.http
            Handler
            Handler
            HttpRouting$Builder
            ServerRequest
            ServerResponse)
           (java.io FileInputStream InputStream OutputStream)))

;; TODO http2 ? 

(set! *warn-on-reflection* true)

(def default-server-options
  {:port 8080
   :default-socket {:write-queue-length 1
                    :backlog 1024
                    :max-payload-size -1}})

(defprotocol BodyWriter
  (write-body! [x server-response]))

(extend-protocol BodyWriter

  clojure.lang.Sequential
  (write-body! [xs ^ServerResponse server-response]
    (with-open [os ^OutputStream (.outputStream server-response)]
      (run! (fn [^String chunk] (.write os (.getBytes chunk)))
            xs)))

  java.io.InputStream
  (write-body! [is ^ServerResponse server-response]
    (with-open [^InputStream is is
                os ^OutputStream (.outputStream server-response)]
      (.transferTo is os)))

  java.io.File
  (write-body! [file ^ServerResponse server-response]
    (with-open [os ^OutputStream (.outputStream server-response)
                is (FileInputStream. file)]
      (.transferTo is os)))

  nil
  (write-body! [_ server-response]
    (.send ^ServerResponse server-response))

  Object
  (write-body! [o server-response]
    (.send ^ServerResponse server-response o)))

(defn- header-val-array ^"[Ljava.lang.String;" [x]
  (if (coll? x)
    (if (counted? x)
      (let [len (count x)
            a ^"[Ljava.lang.String;" (make-array String len)]
        (dotimes [i len] (aset a i (str (nth x i))))
        a)
      (into-array String (map str x)))
    (doto ^"[Ljava.lang.String;" (make-array String 1)
      (aset 0 (str x)))))

(defn set-server-response-headers!
  [^ServerResponse server-response headers]
  (run! (fn [[k v]]
          (-> server-response
              (.header (name k)
                       (header-val-array v))))
        headers))

;; Try to decode headers against a static table first, and fallback to
;; `str/lower-case` if there are no matches
(def header-key->ring-header-key
  (eval `(fn [k#]
           (case k#
             ~@(mapcat (juxt identity str/lower-case)
                       (->> "headers.txt"
                            io/resource
                            io/reader
                            line-seq))
             (str/lower-case k#)))))

(defn server-request->ring-headers
  [^ServerRequest server-request]
  (-> (reduce (fn [m ^Http$HeaderValue h]
                (assoc! m
                        (header-key->ring-header-key (.name h))
                        (.values h)))
              (transient {})
              (some-> server-request .headers))
      persistent!))

(defn send-response!
  [^ServerResponse server-response {:as _ring-response
                                    :keys [body headers status]
                                    :or {status 200}}]
  (set-server-response-headers! server-response headers)
  (.status server-response (Http$Status/create status))
  (write-body! body server-response)
  server-response)

(defn server-request->ring-method [^ServerRequest server-request]
  (let [method (-> server-request
                   .prologue
                   .method
                   .name)]
    ;; mess with the string as a last resort, try to match against static values
    ;; first
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
     ;; TODO add missing ssl keys
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

(defn start!
  "Starts a new server.
  See `default-server-options` to see supported options.  Requires at the very
  least a :handler key, to be used as ring handler"
  [opts]
  (let [opts (merge-with merge default-server-options opts)]
    (-> (server-builder opts)
        (.start))))

(defn stop!
  "Stops server, noop if already stopped"
  [^WebServer server]
  (.stop server))

;; (def r {:status 200 :body (java.io.ByteArrayInputStream. (.getBytes "bar")) :headers {:foo [1 2] :bar ["bay"]}})
;; (def r {:status 200 :body ["foo\n" "bar"] :headers {:foo [1 2] :bar ["bay"]}})
(def r {:status 200 :body (io/file "deps.edn") :headers {:foo [1 2] :bar ["bay"]}})
(def s (start!
        {:default-socket
         {:write-queue-length 100
          :backlog 3000}
         :handler (fn [req]
                    (prn req)
                    r)}))

;; (stop! s)

