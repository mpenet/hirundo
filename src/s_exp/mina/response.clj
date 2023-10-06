(ns s-exp.mina.response
  (:import (io.helidon.http Header
                            Headers
                            HeaderNames
                            HeaderName
                            Status)
           (io.helidon.webserver.http ServerResponse)
           (java.io FileInputStream InputStream OutputStream)))

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

(defn header-name ^HeaderName [ring-header-name]
  (HeaderNames/createFromLowercase (name ring-header-name)))

(defn set-headers!
  [^ServerResponse server-response headers]
  (when headers
    (run! (fn [[k v]]
            (.header server-response
                     (header-name k)
                     (if (sequential? v)
                       (into-array String v)
                       (doto (make-array String 1)
                         (aset 0 v)))))
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

