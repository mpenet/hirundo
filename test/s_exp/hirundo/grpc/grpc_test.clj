(ns s-exp.hirundo.grpc.grpc-test
  (:require [clojure.test :refer [deftest is]]
            [s-exp.hirundo :as m]
            [s-exp.hirundo.grpc :as grpc])
  (:import (com.google.protobuf DescriptorProtos$FileDescriptorProto
                                DescriptorProtos$MethodDescriptorProto
                                DescriptorProtos$ServiceDescriptorProto
                                Descriptors$FileDescriptor)
           (hirundo.test Person PersonOuterClass)
           (io.grpc CallOptions ManagedChannel ManagedChannelBuilder
                    MethodDescriptor MethodDescriptor$MethodType)
           (io.grpc.protobuf ProtoUtils)
           (io.grpc.stub ClientCalls StreamObserver)))

;;; ---- Proto / service descriptor setup -------------------------------------

(defn- service-file-descriptor
  "Builds a FileDescriptor with PersonService (Echo, StreamEcho, BidiEcho)
  layered on top of person.proto."
  ^Descriptors$FileDescriptor []
  (Descriptors$FileDescriptor/buildFrom
   (-> (DescriptorProtos$FileDescriptorProto/newBuilder)
       (.setName "person_service.proto")
       (.setSyntax "proto3")
       (.addDependency "person.proto")
       (.addService
        (-> (DescriptorProtos$ServiceDescriptorProto/newBuilder)
            (.setName "PersonService")
            (.addMethod (-> (DescriptorProtos$MethodDescriptorProto/newBuilder)
                            (.setName "Echo")
                            (.setInputType ".hirundo.test.Person")
                            (.setOutputType ".hirundo.test.Person")
                            .build))
            (.addMethod (-> (DescriptorProtos$MethodDescriptorProto/newBuilder)
                            (.setName "StreamEcho")
                            (.setInputType ".hirundo.test.Person")
                            (.setOutputType ".hirundo.test.Person")
                            (.setServerStreaming true)
                            .build))
            (.addMethod (-> (DescriptorProtos$MethodDescriptorProto/newBuilder)
                            (.setName "BidiEcho")
                            (.setInputType ".hirundo.test.Person")
                            (.setOutputType ".hirundo.test.Person")
                            (.setClientStreaming true)
                            (.setServerStreaming true)
                            .build))
            .build))
       .build)
   (into-array Descriptors$FileDescriptor [(PersonOuterClass/getDescriptor)])))

(def ^Descriptors$FileDescriptor svc-fd (service-file-descriptor))

;;; ---- gRPC client helpers --------------------------------------------------

(defn- method-descriptor
  ^MethodDescriptor [service-name method-name method-type]
  (let [marshaller (ProtoUtils/marshaller (Person/getDefaultInstance))]
    (-> (MethodDescriptor/newBuilder marshaller marshaller)
        (.setType method-type)
        (.setFullMethodName (MethodDescriptor/generateFullMethodName service-name method-name))
        .build)))

(defn- unary-call ^Person [^ManagedChannel ch service-name method-name ^Person request]
  (ClientCalls/blockingUnaryCall ch
                                 (method-descriptor service-name method-name
                                                    MethodDescriptor$MethodType/UNARY)
                                 (CallOptions/DEFAULT)
                                 request))

(defmacro with-server [options & body]
  `(let [~'server (m/start! ~options)]
     (try
       ~@body
       (finally (m/stop! ~'server)))))

(defmacro with-channel [port & body]
  `(let [~'ch (-> (ManagedChannelBuilder/forAddress "localhost" ~port)
                  .usePlaintext
                  .build)]
     (try
       ~@body
       (finally (.shutdownNow ~'ch)))))

(defn- person [name id] (-> (Person/newBuilder) (.setName name) (.setId id) .build))

;;; ---- Tests -----------------------------------------------------------------

(deftest test-unary-echo
  (with-server
    {:grpc-services
     [{:proto svc-fd
       :name "PersonService"
       :methods {"Echo" {:type :unary
                         :handler (fn [^Person req observer]
                                    (grpc/complete! observer req))}}}]}
    (with-channel (.port server)
      (let [result (unary-call ch "PersonService" "Echo" (person "Alice" 1))]
        (is (= "Alice" (.getName result)))
        (is (= 1 (.getId result)))))))

(deftest test-unary-transform
  (with-server
    {:grpc-services
     [{:proto svc-fd
       :name "PersonService"
       :methods {"Echo" {:type :unary
                         :handler (fn [^Person req observer]
                                    (grpc/complete!
                                     observer
                                     (-> (Person/newBuilder)
                                         (.setName (str "hello:" (.getName req)))
                                         (.setId (.getId req))
                                         .build)))}}}]}
    (with-channel (.port server)
      (let [result (unary-call ch "PersonService" "Echo" (person "Alice" 42))]
        (is (= "hello:Alice" (.getName result)))
        (is (= 42 (.getId result)))))))

(deftest test-server-streaming
  (with-server
    {:grpc-services
     [{:proto svc-fd
       :name "PersonService"
       :methods {"StreamEcho" {:type :server-stream
                               :handler (fn [^Person req observer]
                                          (dotimes [i 3]
                                            (grpc/send! observer (person (str (.getName req) "-" i) i)))
                                          (grpc/complete! observer))}}}]}
    (with-channel (.port server)
      (let [md (method-descriptor "PersonService" "StreamEcho"
                                  MethodDescriptor$MethodType/SERVER_STREAMING)
            results (iterator-seq
                     (ClientCalls/blockingServerStreamingCall ch md (CallOptions/DEFAULT)
                                                              (person "Bob" 0)))
            names (mapv #(.getName ^Person %) results)]
        (is (= ["Bob-0" "Bob-1" "Bob-2"] names))))))

(deftest test-bidi-streaming
  (with-server
    {:grpc-services
     [{:proto svc-fd
       :name "PersonService"
       :methods {"BidiEcho" {:type :bidi
                             :handler (fn [response-observer]
                                        (reify StreamObserver
                                          (onNext [_ req]
                                            (grpc/send! response-observer req))
                                          (onError [_ t]
                                            (grpc/error! response-observer t))
                                          (onCompleted [_]
                                            (grpc/complete! response-observer))))}}}]}
    (with-channel (.port server)
      (let [received (atom [])
            done (promise)
            md (method-descriptor "PersonService" "BidiEcho"
                                  MethodDescriptor$MethodType/BIDI_STREAMING)
            response-obs (reify StreamObserver
                           (onNext [_ msg]
                             (swap! received conj (.getName ^Person msg)))
                           (onError [_ _]
                             (deliver done :error))
                           (onCompleted [_]
                             (deliver done :ok)))
            request-obs (ClientCalls/asyncBidiStreamingCall
                         (.newCall ch md (CallOptions/DEFAULT))
                         response-obs)]
        (doseq [n ["Alice" "Bob" "Carol"]]
          (.onNext request-obs (person n 0)))
        (.onCompleted request-obs)
        (is (= :ok (deref done 5000 :timeout)))
        (is (= ["Alice" "Bob" "Carol"] @received))))))

(deftest test-service-as-fn
  (with-server
    {:grpc-services
     [(fn []
        {:proto svc-fd
         :name "PersonService"
         :methods {"Echo" {:type :unary
                           :handler (fn [req observer]
                                      (grpc/complete! observer req))}}})]}
    (with-channel (.port server)
      (is (= "Dave" (.getName (unary-call ch "PersonService" "Echo" (person "Dave" 0))))))))
