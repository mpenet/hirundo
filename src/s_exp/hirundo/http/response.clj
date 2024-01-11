(ns s-exp.hirundo.http.response
  (:import (io.helidon.http HeaderNames
                            HeaderName
                            Status)
           (io.helidon.webserver.http ServerResponse)
           (java.io FileInputStream InputStream OutputStream)))

(set! *warn-on-reflection* true)

(def ring-core-available?
  (try
    (require 'ring.core.protocols)
    true
    (catch Throwable _ false)))

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

(when ring-core-available?
  (extend-protocol BodyWriter
    Object
    (write-body! [o ^ServerResponse server-response]
      (let [StreamableResponseBody @(ns-resolve 'ring.core.protocols 'StreamableResponseBody)
            write-body-to-stream (ns-resolve 'ring.core.protocols 'write-body-to-stream)]
        (if (satisfies? StreamableResponseBody o)
          (write-body-to-stream o nil (.outputStream server-response))
          (.send server-response o))))))

(defn header-name ^HeaderName [ring-header-name]
  (HeaderNames/createFromLowercase (name ring-header-name)))

(defn set-headers!
  [^ServerResponse server-response headers]
  (when headers
    (run! (fn [[k v]]
            (let [values-seq (if (sequential? v) v [v])
                  headers ^"[Ljava.lang.String;" (into-array String values-seq)]
              (.header server-response (header-name k) headers)))
          headers)))

(defn- set-status!
  [^ServerResponse server-response status]
  (when status
    (.status server-response (Status/create status))))

(defn set-response!
  [^ServerResponse server-response {:keys [body headers status]}]
  (set-headers! server-response headers)
  (set-status! server-response status)
  (write-body! body server-response))
