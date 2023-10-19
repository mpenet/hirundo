(ns s-exp.hirundo.http.request
  (:require [clojure.string :as str]
            [s-exp.hirundo.http.header :as h]
            [strojure.zmap.core :as zmap])
  (:import (clojure.lang PersistentHashMap)
           (io.helidon.common.uri UriQuery UriPath)
           (io.helidon.http HttpPrologue Headers)
           (io.helidon.webserver.http ServerRequest ServerResponse)))

(defn ring-headers
  [^Headers headers]
  (h/->HeaderMapProxy headers nil))

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
        ring-request (-> (.asTransient PersistentHashMap/EMPTY)
                         ;; delayed
                         (.assoc :server-port (zmap/delay (.port (.localPeer server-request))))
                         (.assoc :server-name (zmap/delay (.host (.localPeer server-request))))
                         (.assoc :remote-addr (zmap/delay
                                                (let [address ^java.net.InetSocketAddress (.address (.remotePeer server-request))]
                                                  (-> address .getAddress .getHostAddress))))
                         (.assoc :ssl-client-cert (zmap/delay (some-> server-request .remotePeer .tlsCertificates (.orElse nil) first)))
                         ;; realized
                         (.assoc :uri (ring-path (.path server-request)))
                         (.assoc :scheme (if (.isSecure server-request) :https :http))
                         (.assoc :protocol (ring-protocol (.prologue server-request)))
                         (.assoc :request-method (ring-method (.prologue server-request)))
                         (.assoc :headers (ring-headers (.headers server-request)))
                         (.assoc ::server-request server-request)
                         (.assoc ::server-response server-response))
        ;; optional
        ring-request (cond-> ring-request
                       qs (.assoc :query-string (ring-query (.query server-request)))
                       body (.assoc :body body))]
    (zmap/wrap (.persistent ring-request))))
