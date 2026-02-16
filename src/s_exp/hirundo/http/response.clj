(ns s-exp.hirundo.http.response
  (:require [ring.core.protocols :as rp])
  (:import (io.helidon.http HeaderNames
                            HeaderName
                            Status)
           (io.helidon.webserver.http ServerResponse)))

(set! *warn-on-reflection* true)

(defn write-body!
  [^ServerResponse server-response response body]
  (with-open [os (.outputStream server-response)]
    (rp/write-body-to-stream body response os)))

(defn header-name ^HeaderName [ring-header-name]
  (HeaderNames/create (name ring-header-name)))

(defn set-headers!
  [^ServerResponse server-response headers]
  (when headers
    (run! (fn [[k v]]
            (let [values-seq (if (sequential? v) v [v])
                  headers ^"[Ljava.lang.String;" (into-array String values-seq)]
              (.header server-response (header-name k) headers)))
          headers)))

(defn set-status!
  [^ServerResponse server-response status]
  (when status
    (.status server-response (Status/create status))))

(defn set-response!
  [^ServerResponse server-response {:as response :keys [body headers status]}]
  (when (zero? (.bytesWritten server-response))
    (set-headers! server-response headers)
    (set-status! server-response status)
    (write-body! server-response response body)))
