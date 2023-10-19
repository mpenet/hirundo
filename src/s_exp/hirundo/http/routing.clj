(ns s-exp.hirundo.http.routing
  (:require [s-exp.hirundo.http.request :as request]
            [s-exp.hirundo.http.response :as response]
            [s-exp.hirundo.options :as options])
  (:import (io.helidon.webserver WebServerConfig$Builder)
           (io.helidon.webserver.http Handler
                                      HttpRouting)))

(set! *warn-on-reflection* true)

(defn set-ring1-handler! ^WebServerConfig$Builder
  [^WebServerConfig$Builder builder handler _options]
  (doto builder
    (.addRouting
     (.build
      (doto (HttpRouting/builder)
        (.any
         ^"[Lio.helidon.webserver.http.Handler;"
         (into-array Handler
                     [(reify Handler
                        (handle [_ server-request server-response]
                          (->> (request/ring-request server-request server-response)
                               handler
                               (response/set-response! server-response))))])))))))

(defmethod options/set-server-option! :http-handler
  [^WebServerConfig$Builder builder _ handler options]
  (set-ring1-handler! builder handler options))
