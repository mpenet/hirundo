(ns s-exp.hirundo.http.header
  (:import (clojure.lang
            IEditableCollection
            IFn
            IKVReduce
            IPersistentMap
            MapEntry
            MapEquivalence
            Util)
           (io.helidon.http
            Headers
            Header
            HeaderName
            HeaderNames
            HeaderValues)
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

(defn kv->header [^HeaderName header-name ^String v]
  (HeaderValues/create header-name v))

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
         (eduction (map (fn [^Header header]
                          (MapEntry. (.lowerCase (.headerName header))
                                     (.value header)))))
         .iterator))

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


