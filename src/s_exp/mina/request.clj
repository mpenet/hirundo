(ns s-exp.mina.request
  (:require [clojure.string :as str]
            [strojure.zmap.core :as zmap])
  (:import (clojure.lang IEditableCollection IFn IKVReduce
                         IPersistentMap MapEntry MapEquivalence Util)
           (clojure.lang PersistentHashMap)
           (io.helidon.common.http Http$HeaderValue
                                   Http$Header
                                   Http$HeaderName ServerRequestHeaders)
           (io.helidon.nima.webserver.http ServerRequest ServerResponse)
           (java.util Map)))

(defn header-name ^Http$HeaderName
  [s]
  (Http$Header/createFromLowercase s))

(defn header-value
  ([^Http$Header h hn]
   (header-value h hn nil))
  ([^Http$Header h hn not-found]
   (-> h
       (.value hn)
       (.orElse not-found))))

(defn get-header
  ([^Http$Header h k]
   (get-header h k nil))
  ([^Http$Header h k not-found]
   (header-value h
                 (header-name k)
                 not-found)))

(defn ring-headers*
  [^ServerRequestHeaders headers]
  (-> (reduce (fn [m ^Http$HeaderValue h]
                (assoc! m
                        (.lowerCase (.headerName h))
                        (.value h)))
              (transient {})
              headers)
      persistent!))

(defprotocol RingHeaders
  (ring-headers [_]))

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
(deftype HeaderMapProxy [^ServerRequestHeaders headers
                         ^:volatile-mutable persistent-copy]
  Map
  (size [_]
    (.size headers))

  (get [this k]
    (.valAt this k))

  MapEquivalence

  IFn
  (invoke [this k]
    (.valAt this k))

  (invoke [_this k not-found]
    (get-header headers k not-found))

  IPersistentMap
  (valAt [_ k]
    (get-header headers k))

  (valAt [_ k not-found]
    (get-header headers k not-found))

  (entryAt [_ k]
    (let [hn (header-name k)]
      (when-let [v (header-value headers hn)]
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
    (.iterator
     (eduction (map (fn [header]
                      (MapEntry. (.name header)
                                 (.value header))))

               headers)))
  IKVReduce
  (kvreduce [this f init]
    (.kvreduce ^IKVReduce (ring-headers this) f init))

  IEditableCollection
  (asTransient [this]
    (transient (ring-headers this)))

  RingHeaders
  (ring-headers
    [_]
    (or persistent-copy
        (set! persistent-copy (ring-headers* headers))))

  Object
  (toString [this]
    (.toString (ring-headers this))))

(defn ring-request
  [^ServerRequest server-request
   ^ServerResponse server-response]
  (-> (.asTransient PersistentHashMap/EMPTY)
      (.assoc :body (zmap/delay
                      (let [content (.content server-request)]
                        (when-not (.consumed content) (.inputStream content)))))
      (.assoc :server-port (zmap/delay (.port (.localPeer server-request))))
      (.assoc :server-name (zmap/delay (.host (.localPeer server-request))))
      (.assoc :remote-addr (zmap/delay
                             (let [address ^java.net.InetSocketAddress (.address (.remotePeer server-request))]
                               (-> address .getAddress .getHostAddress))))
      (.assoc :uri (zmap/delay (.rawPath (.path server-request))))
      (.assoc :query-string (zmap/delay (let [query (.rawValue (.query server-request))]
                                          (when (not= "" query) query))))
      (.assoc :scheme (if (.isSecure server-request) :https :http))
      (.assoc :protocol (zmap/delay (ring-protocol server-request)))
      (.assoc :ssl-client-cert (zmap/delay (some-> server-request .remotePeer .tlsCertificates (.orElse nil) first)))
      (.assoc :request-method (ring-method server-request))
      (.assoc :headers (->HeaderMapProxy (.headers server-request) nil))
      (.assoc ::server-request server-request)
      (.assoc ::server-response server-response)
      (.persistent)
      zmap/wrap))
