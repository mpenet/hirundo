(ns s-exp.hirundo-test
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [gniazdo.core :as wsc]
            [less.awful.ssl :as ls]
            [ring.core.protocols :as p]
            [s-exp.hirundo :as m]
            [s-exp.hirundo.websocket :as ws])
  (:import (io.helidon.common.tls Tls TlsClientAuth)
           (io.helidon.common.tls TlsConfig)))

(def ^:dynamic *endpoint* nil)
(def ^:dynamic *client* nil)

(defn status-ok? [response]
  (some-> response :status (= 200)))

(defmacro with-server [options & body]
  `(let [server# (m/start! ~options)]
     (binding [*endpoint* (format "http://localhost:%s/" (.port server#))]
       (try
         ~@body
         (finally (m/stop! server#))))))

(deftest test-send-headers
  (with-server {:http-handler (fn [req] {:headers {:foo "bar"}})}
    (is (-> (client/get *endpoint*) :headers :foo (= "bar"))))
  (with-server {:http-handler (fn [req] {:headers {:foo ["bar" "baz"]}})}
    (is (-> (client/get *endpoint*) :headers :foo (= ["bar" "baz"])))))

(deftest test-status
  (with-server {:http-handler (fn [req] {:status 201})}
    (is (-> (client/get *endpoint*) :status (= 201)))))

(deftest test-headers
  (with-server {:http-handler (fn [req]
                                {:body (str (count (:headers req)))})}
    (is (-> (client/get *endpoint*) :body (= "4"))))
  (with-server {:http-handler (fn [req]
                                {:body (str (:headers req))})}
    (is (-> (client/get *endpoint*) :status (= 200)))))

(deftest test-query-string
  (with-server {:http-handler (fn [req] {:body (:query-string req)})}
    (is (-> (client/get (str *endpoint* "?foo=bar")) :body (= "foo=bar"))))

  (with-server {:http-handler (fn [req] {:body (:query-string req)})}
    (is (-> (client/get (str *endpoint* "?")) :body (= ""))))

  (with-server {:http-handler (fn [req] {:body (:query-string req)})}
    (is (-> (client/get (str *endpoint* "")) :body (= "")))))

(deftest test-method
  (with-server {:http-handler (fn [req] {:body (str (:request-method req))})}
    (is (-> (client/post *endpoint*) :body (= ":post"))))

  (with-server {:http-handler (fn [req] {:body (str (:request-method req))})}
    (is (-> (client/put *endpoint*) :body (= ":put"))))

  (with-server {:http-handler (fn [req] {:body (str (:request-method req))})}
    (is (-> (client/delete *endpoint*) :body (= ":delete")))))

(deftest test-uri
  (with-server {:http-handler (fn [req] {:body (:uri req)})}
    (is (-> (client/delete (str *endpoint* "foo/bar")) :body (= "/foo/bar")))))

(deftest test-scheme
  (with-server {:http-handler (fn [req] {:body (str (:scheme req))})}
    (is (-> (client/get *endpoint*) :body (= ":http")))))

(deftest test-body
  (with-server {:http-handler (fn [req] {})}
    (is (-> (client/get *endpoint*) :body (= ""))))

  (with-server {:http-handler (fn [req] {:body "yes"})}
    (is (-> (client/get *endpoint*) :body (= "yes"))))

  (with-server {:http-handler (fn [req] {:body ["yes" "no"]})}
    (is (-> (client/get *endpoint*) :body (= "yesno"))))

  (with-server {:http-handler (fn [req] {:body (.getBytes "yes")})}
    (is (-> (client/get *endpoint*) :body (= "yes"))))

  (with-server {:http-handler (fn [req] {:body (java.io.ByteArrayInputStream. (.getBytes "yes"))})}
    (is (-> (client/get *endpoint*) :body (= "yes")))))

(deftest resp-map-decoding
  (with-server {:http-handler (fn [req]
                                {:body (str (select-keys req [:something]))})}
    (is (status-ok? (client/get (str *endpoint* ""))))))

(defn tls []
  (let [b (doto (TlsConfig/builder)
            (.sslContext (ls/ssl-context "test/server.key"
                                         "test/server.crt"
                                         "test/server.crt"))
            (.clientAuth TlsClientAuth/REQUIRED)
            (.trustAll true)
            (.endpointIdentificationAlgorithm (Tls/ENDPOINT_IDENTIFICATION_NONE)))]
    (.build b)))

(deftest test-ssl-context
  (with-server {:http-handler (fn [req] {}) :tls (tls)}
    (let [endpoint (str/replace *endpoint* "http://" "https://")]
      (is (thrown? Exception (client/get endpoint)))
      (is (status-ok? (client/get endpoint
                                  {:insecure? true
                                   :keystore "test/keystore.jks"
                                   :keystore-pass "password"
                                   :trust-store "test/keystore.jks"
                                   :trust-store-pass "password"}))))))

(deftest test-streamable-body
  (with-server {:http-handler (fn [_req]
                                {:status 200
                                 :headers {"content-type" "text/event-stream"
                                           "transfer-encoding" "chunked"}
                                 :body (reify p/StreamableResponseBody
                                         (write-body-to-stream [_ _ output-stream]
                                           (with-open [w (io/writer output-stream)]
                                             (doseq [n (range 1 6)]
                                               (doto w
                                                 (.write (str "data: " n "\n\n"))
                                                 (.flush))))))})}
    (let [resp (client/get *endpoint*)]
      (is (status-ok? resp))
      (is (= "data: 1\n\ndata: 2\n\ndata: 3\n\ndata: 4\n\ndata: 5\n\n"
             (:body resp))))))

(defmacro with-ws-client
  [options & body]
  `(binding [*client* (wsc/connect (str (str/replace *endpoint* "http" "ws") "/ws")
                        ~@(into [] cat options))]

     (try
       ~@body
       (finally
         (wsc/close *client*)))))

(deftest test-websocket
  (with-server {:websocket-endpoints {"/ws"
                                      {:message (fn [session data _last]
                                                  (s-exp.hirundo.websocket/send! session data true))}}}
    (let [client-recv (promise)]
      (with-ws-client {:on-receive (fn [msg] (deliver client-recv msg))}
        (wsc/send-msg *client* "bar")
        (is (= "bar" @client-recv) "echo test"))))

  (with-server {:websocket-endpoints {"/ws"
                                      {:message (fn [session data _last]
                                                  (s-exp.hirundo.websocket/send! session data false)
                                                  (s-exp.hirundo.websocket/send! session data true))}}}
    (let [client-recv (promise)]
      (with-ws-client {:on-receive (fn [msg] (deliver client-recv msg))}
        (wsc/send-msg *client* "bar")
        (is (= "barbar" @client-recv) "double echo test"))))

  (with-server {:websocket-endpoints {"/ws"
                                      {:subprotocols ["chat"]
                                       :message (fn [session data _last]
                                                  (s-exp.hirundo.websocket/send! session data true))}}}
    (let [client-recv (promise)]
      (with-ws-client {:subprotocols ["chat"]
                       :on-receive (fn [msg] (deliver client-recv msg))}
        (wsc/send-msg *client* "bar")
        (is (= "bar" @client-recv) "echo with correct subprotocols"))

      (is (thrown-with-msg? Exception
                            #"Not Found"
                            (with-ws-client {}))
          "Missing subprotocols")

      (is (thrown-with-msg? Exception
                            #"Not Found"
                            (with-ws-client {:subprotocols ["foo"]}))
          "Incorrect subprotocols"))))

(deftest test-websocket-fn-listener
  (with-server {:websocket-endpoints
                {"/ws" (fn []
                         {:message (fn [session data _last]
                                     (s-exp.hirundo.websocket/send! session data true))})}}
    (let [client-recv (promise)]
      (with-ws-client {:on-receive (fn [msg] (deliver client-recv msg))}
        (wsc/send-msg *client* "bar")
        (is (= "bar" @client-recv) "echo test")))))
