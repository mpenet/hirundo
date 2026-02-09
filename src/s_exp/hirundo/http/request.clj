(ns s-exp.hirundo.http.request
  (:require [clojure.string :as str])
  (:import (io.helidon.common.uri UriQuery UriPath)
           (io.helidon.http HttpPrologue Headers Header)
           (io.helidon.webserver.http ServerRequest ServerResponse)))

(set! *warn-on-reflection* true)

(defn ring-headers
  [^Headers headers]
  (-> (reduce (fn [m ^Header h]
                (assoc! m
                        (.lowerCase (.headerName h))
                        (.value h)))
              (transient {})
              headers)
      persistent!))

(defn ring-method [^HttpPrologue prologue]
  (let [method (-> prologue .method .text)]
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

(defn ring-protocol
  [^HttpPrologue prologue]
  (case (.protocolVersion prologue)
    "1.0" "HTTP/1.0"
    "1.1" "HTTP/1.1"
    "2.0" "HTTP/2"))

(defn ring-query
  [^UriQuery query]
  (let [query (.rawValue query)]
    (when (not= "" query) query)))

(defn ring-path [^UriPath path]
  (.rawPath path))

(defn ring-request
  [^ServerRequest server-request
   ^ServerResponse server-response]
  (let [qs (ring-query (.query server-request))
        body (let [content (.content server-request)]
               (when-not (.consumed content) (.inputStream content)))

        ring-request (transient
                      {:server-port (.port (.localPeer server-request))
                       :server-name (.host (.localPeer server-request))
                       :remote-addr (let [address ^java.net.InetSocketAddress (.address (.remotePeer server-request))]
                                      (-> address .getAddress .getHostAddress))
                       :ssl-client-cert (some-> server-request .remotePeer .tlsCertificates (.orElse nil) first)
                       :uri (ring-path (.path server-request))
                       :scheme (if (.isSecure server-request) :https :http)
                       :protocol (ring-protocol (.prologue server-request))
                       :request-method (ring-method (.prologue server-request))
                       :headers (ring-headers (.headers server-request))
                       :authority (.authority server-request)
                       ::server-request server-request
                       ::server-response server-response})

        ring-request (cond-> ring-request
                       qs (assoc! :query-string (ring-query (.query server-request)))
                       body (assoc! :body body))]

    (persistent! ring-request)))
