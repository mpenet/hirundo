(ns s-exp.mina.response
  (:import (io.helidon.common.http Http$Status)
           (io.helidon.nima.webserver.http ServerResponse)
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

(defn- header-val-array ^"[Ljava.lang.String;" [x]
  (if (coll? x)
    (if (counted? x)
      (let [len (count x)
            a ^"[Ljava.lang.String;" (make-array String len)]
        (dotimes [i len] (aset a i (nth x i)))
        a)
      (into-array String x))
    (doto ^"[Ljava.lang.String;" (make-array String 1)
      (aset 0 x))))

(defn set-headers!
  [^ServerResponse server-response headers]
  (run! (fn [[k v]]
          (-> server-response
              (.header (name k)
                       (header-val-array v))))
        headers))

(defn set-response!
  [^ServerResponse server-response {:as _ring-response
                                    :keys [body headers status]}]
  (set-headers! server-response headers)
  (.status server-response (Http$Status/create (or status 200)))
  (write-body! body server-response)
  server-response)
