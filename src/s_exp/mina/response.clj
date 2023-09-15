(ns s-exp.mina.response
  (:import (io.helidon.http Http$Headers
                            Http$HeaderNames
                            Http$HeaderName
                            Http$Status)
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

(defn header-name ^Http$HeaderName [ring-header-name]
  (Http$HeaderNames/createFromLowercase (name ring-header-name)))

(defn set-headers!
  [^ServerResponse server-response headers]
  (when headers
    (run! (fn [[k v]]
            (.header server-response
                     (Http$Headers/create (header-name k)
                                          v)))
          headers)))

(defn- set-status!
  [^ServerResponse server-response status]
  (when status
    (.status server-response (Http$Status/create status))))

(defn set-response!
  [^ServerResponse server-response {:keys [body headers status]}]
  (set-headers! server-response headers)
  (set-status! server-response status)
  (write-body! body server-response))

