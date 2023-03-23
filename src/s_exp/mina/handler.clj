(ns s-exp.mina.handler
  (:require [s-exp.mina.options :as options]
            [s-exp.mina.request :as request]
            [s-exp.mina.response :as response])
  (:import (io.helidon.nima.webserver WebServer$Builder)
           (io.helidon.nima.webserver.http Handler Handler HttpRouting$Builder)))

(defn set-ring1-handler! ^WebServer$Builder
  [^WebServer$Builder builder handler _options]
  (doto builder
    (.routing
     (reify java.util.function.Consumer
       (accept [_ route]
         (.any ^HttpRouting$Builder route
               (reify Handler
                 (handle [_ server-request server-response]
                   (->> (request/ring-request server-request server-response)
                        handler
                        (response/set-response! server-response))))))))))

(defmethod options/set-server-option! :handler
  [^WebServer$Builder builder _ handler options]
  (set-ring1-handler! builder handler options))
