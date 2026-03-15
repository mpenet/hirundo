(ns s-exp.hirundo.grpc.routing
  (:require [s-exp.hirundo.grpc.service :as svc]
            [s-exp.hirundo.options :as options])
  (:import (io.helidon.webserver WebServerConfig$Builder)
           (io.helidon.webserver.grpc GrpcRouting GrpcRouting$Builder GrpcService)))

(set! *warn-on-reflection* true)

(defn- add-service!
  [^GrpcRouting$Builder grpc-builder service-descriptor]
  (let [^GrpcService grpc-svc
        (cond
          (map? service-descriptor)
          (svc/service service-descriptor)

          (instance? GrpcService service-descriptor)
          service-descriptor

          (ifn? service-descriptor)
          (svc/service (service-descriptor))

          :else
          (throw
           (ex-info (format "Invalid gRPC service descriptor type: %s"
                            (type service-descriptor))
                    {:type :s-exp.hirundo.grpc.routing/invalid-service})))]
    (.service grpc-builder grpc-svc)))

(defn set-grpc-services!
  ^WebServerConfig$Builder
  [^WebServerConfig$Builder builder services _options]
  (doto builder
    (.addRouting
     ^GrpcRouting$Builder
     (reduce add-service! (GrpcRouting/builder) services))))

(defmethod options/set-server-option! :grpc-services
  [^WebServerConfig$Builder builder _ services options]
  (set-grpc-services! builder services options))
