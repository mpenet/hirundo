(ns s-exp.mina.request
  (:require [clojure.string :as str]
            [strojure.zmap.core :as zmap])
  (:import (clojure.lang
            IEditableCollection
            IFn
            IKVReduce
            IPersistentMap
            MapEntry
            MapEquivalence
            PersistentHashMap
            Util)
           (io.helidon.http
            Headers
            Header
            HeaderName
            HeaderNames)
           (io.helidon.webserver.http ServerRequest ServerResponse)
           (java.util Map)))

(defn header-name ^HeaderName
  [s]
  (HeaderNames/createFromLowercase s))

(defn header->value*
  ([^Headers header header-name]
   (header->value* header header-name nil))
  ([^Headers header
    ^HeaderName header-name not-found]
   (-> header
       (.value header-name)
       (.orElse not-found))))

(defn header->value
  ([^Headers h k]
   (header->value h k nil))
  ([^Headers h k not-found]
   (header->value* h
                   (header-name k)
                   not-found)))

(defn ring-headers*
  [^Headers headers]
  (-> (reduce (fn [m ^Header h]
                (assoc! m
                        (.lowerCase (.headerName h))
                        (.value h)))
              (transient {})
              headers)
      persistent!))

(defprotocol RingHeaders
  (^clojure.lang.APersistentMap ring-headers [_]))

(defn ring-method
  [^ServerRequest server-request]
  (let [method (-> server-request
                   .prologue
                   .method
                   .text)]
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

(defn ring-protocol
  [^ServerRequest server-request]
  (case (-> server-request
            .prologue
            .protocolVersion)
    "1.0" "HTTP/1.0"
    "1.1" "HTTP/1.1"
    "2.0" "HTTP/2"))

;; inspired by ring-undertow
(deftype HeaderMapProxy [^Headers headers
                         ^:volatile-mutable persistent-copy]
  Map
  (size [_]
    (.size headers))

  (get [_ k]
    (header->value headers k))

  MapEquivalence

  IFn
  (invoke [_ k]
    (header->value headers k))

  (invoke [_this k not-found]
    (header->value headers k not-found))

  IPersistentMap
  (valAt [_ k]
    (header->value headers k))

  (valAt [_ k not-found]
    (header->value headers k not-found))

  (entryAt [_ k]
    (let [hn (header-name k)]
      (when-let [v (header->value* headers hn)]
        (MapEntry. (.lowerCase hn) v))))

  (containsKey [_ k]
    (.contains headers (header-name k)))

  (assoc [this k v]
    (-> (ring-headers this)
        (.assoc k v)))

  (assocEx [this k v]
    (if (.containsKey this k)
      (throw (Util/runtimeException "Key already present"))
      (.assoc this k v)))

  (cons [this o]
    (-> (ring-headers this)
        (.cons o)))

  (without [this k]
    (-> (ring-headers this)
        (.without k)))

  (empty [_]
    {})

  (count [_]
    (.size headers))

  (seq [this]
    (.seq (ring-headers this)))

  (equiv [this o]
    (= o (ring-headers this)))

  (iterator [_]
    (->> headers
         .iterator
         (eduction (map (fn [^Header header]
                          (MapEntry. (.lowerCase (.headerName header))
                                     (.value header)))))))

  IKVReduce
  (kvreduce [this f init]
    (.kvreduce ^IKVReduce
     (ring-headers this)
               f
               init))

  IEditableCollection
  (asTransient [this]
    (transient (ring-headers this)))

  RingHeaders
  (ring-headers
    [_]
    (or persistent-copy
        (set! persistent-copy (ring-headers* headers))))

  Object
  (toString [_]
    (.toString headers)))

(defn ring-request
  [^ServerRequest server-request
   ^ServerResponse server-response]
  (let [qs (let [query (.rawValue (.query server-request))]
             (when (not= "" query) query))
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
                         (.assoc :uri (.rawPath (.path server-request)))
                         (.assoc :scheme (if (.isSecure server-request) :https :http))
                         (.assoc :protocol (ring-protocol server-request))
                         (.assoc :request-method (ring-method server-request))
                         (.assoc :headers (->HeaderMapProxy (.headers server-request) nil))
                         (.assoc ::server-request server-request)
                         (.assoc ::server-response server-response))
        ;; optional
        ring-request (cond-> ring-request
                       qs (.assoc :query-string qs)
                       body (.assoc :body body))]
    (zmap/wrap (.persistent ring-request))))
