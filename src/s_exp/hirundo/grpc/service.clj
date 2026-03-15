(ns s-exp.hirundo.grpc.service
  (:import (com.google.protobuf Descriptors$FileDescriptor)
           (io.grpc.stub ServerCalls$UnaryMethod
                         ServerCalls$ServerStreamingMethod
                         ServerCalls$ClientStreamingMethod
                         ServerCalls$BidiStreamingMethod)
           (io.helidon.webserver.grpc GrpcService GrpcService$Routing)))

(set! *warn-on-reflection* true)

(defn- register-method!
  [^GrpcService$Routing routing method-name {:keys [type handler]}]
  (case type
    :unary
    (.unary routing ^String method-name
            (reify ServerCalls$UnaryMethod
              (invoke [_ req response-observer]
                (handler req response-observer))))
    :server-stream
    (.serverStream routing ^String method-name
                   (reify ServerCalls$ServerStreamingMethod
                     (invoke [_ req response-observer]
                       (handler req response-observer))))
    :client-stream
    (.clientStream routing ^String method-name
                   (reify ServerCalls$ClientStreamingMethod
                     (invoke [_ response-observer]
                       (handler response-observer))))
    :bidi
    (.bidi routing ^String method-name
           (reify ServerCalls$BidiStreamingMethod
             (invoke [_ response-observer]
               (handler response-observer))))
    (throw (ex-info (str "Unknown gRPC method type: " type)
                    {:type :s-exp.hirundo.grpc/unknown-method-type
                     :method-name method-name}))))

(defn service
  "Creates a `GrpcService` from a descriptor map:

   {:proto    ^Descriptors$FileDescriptor  ; proto file descriptor
    :name     \"MyService\"                 ; optional service name
    :methods  {\"MethodName\" {:type    :unary | :server-stream | :client-stream | :bidi
                              :handler <fn>}}}

  Handler signatures:
  - `:unary`, `:server-stream`  — `(fn [request ^StreamObserver response-observer])`
  - `:client-stream`, `:bidi`   — `(fn [^StreamObserver response-observer]) => StreamObserver`

  Handlers receive raw protobuf objects. Use pronto or direct Java interop to
  encode/decode messages within the handler."
  ^GrpcService
  [{:keys [^Descriptors$FileDescriptor proto name methods]}]
  (reify GrpcService
    (proto [_] proto)
    (serviceName [_] (or name ""))
    (update [_ routing]
      (run! (fn [[method-name method-descriptor]]
              (register-method! routing method-name method-descriptor))
            methods))))
