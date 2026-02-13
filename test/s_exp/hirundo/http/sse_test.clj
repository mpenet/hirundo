(ns s-exp.hirundo.http.sse-test
  (:require [clj-http.client :as client]
            [clojure.core.async :as async]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [s-exp.hirundo :as m]
            [s-exp.hirundo.sse :as sse]))

(defmacro with-server [options & body]
  `(let [server# (m/start! ~options)]
     (try
       (let [~'endpoint (format "http://localhost:%s" (.port server#))]
         ~@body)
       (finally (m/stop! server#)))))

(defn- read-sse-events
  "Reads the SSE response body and returns a vec of parsed event strings.
  Filters out heartbeat comments (lines starting with :)."
  [body]
  (->> (str/split body #"\n\n")
       (remove str/blank?)
       (remove #(re-matches #"^:\s*$" (str/trim %)))
       vec))

;; --- format-event unit tests ---

(deftest test-format-event-string
  (testing "string message produces data field"
    (is (= "data: hello\n\n"
           (#'sse/format-event "hello")))))

(deftest test-format-event-map-data-only
  (testing "map with :data produces data field"
    (is (= "data: payload\n\n"
           (#'sse/format-event {:data "payload"})))))

(deftest test-format-event-map-all-fields
  (testing "map with all fields"
    (let [result (#'sse/format-event {:event "update"
                                      :id "42"
                                      :retry "3000"
                                      :data "hello"})]
      (is (str/includes? result "event: update\n"))
      (is (str/includes? result "id: 42\n"))
      (is (str/includes? result "retry: 3000\n"))
      (is (str/includes? result "data: hello\n"))
      (is (str/ends-with? result "\n\n")))))

(deftest test-format-event-multiline-data
  (testing "multiline data splits into multiple data fields"
    (is (= "data: line1\ndata: line2\n\n"
           (#'sse/format-event {:data "line1\nline2"})))))

(deftest test-format-event-map-no-data
  (testing "map with event but no data"
    (is (= "event: ping\n\n"
           (#'sse/format-event {:event "ping"})))))

;; --- integration tests ---
;; sse-response blocks the handler thread until the connection ends.
;; We pre-create input-ch and feed messages from the test thread.

(deftest test-sse-basic
  (testing "basic SSE: send messages and receive them"
    (let [ch (async/chan 16)]
      (with-server {:http-handler
                    (fn [req]
                      (sse/response! req {:input-ch ch})
                      nil)}
        (let [response-future (future (client/get (str endpoint "/sse")
                                                  {:as :string}))]
          (Thread/sleep 200)
          (async/put! ch "msg1")
          (async/put! ch "msg2")
          (async/put! ch {:event "custom" :data "msg3"})
          (Thread/sleep 100)
          (async/close! ch)
          (let [resp (deref response-future 5000 nil)]
            (is (some? resp) "should get a response")
            (is (= 200 (:status resp)))
            (is (= "text/event-stream"
                   (get-in resp [:headers "content-type"])))
            (is (= "no-cache"
                   (get-in resp [:headers "cache-control"])))
            (let [events (read-sse-events (:body resp))]
              (is (= "data: msg1" (nth events 0)))
              (is (= "data: msg2" (nth events 1)))
              (is (str/includes? (nth events 2) "event: custom"))
              (is (str/includes? (nth events 2) "data: msg3")))))))))

(deftest test-sse-custom-headers
  (testing "custom headers are merged into response"
    (let [ch (async/chan 16)]
      (with-server {:http-handler
                    (fn [req]
                      (sse/response! req {:input-ch ch
                                          :headers {"x-custom" "test"}})
                      nil)}
        (let [response-future (future (client/get (str endpoint "/sse")
                                                  {:as :string}))]
          (Thread/sleep 200)
          (async/put! ch "hi")
          (Thread/sleep 100)
          (async/close! ch)
          (let [resp (deref response-future 5000 nil)]
            (is (= "test" (get-in resp [:headers "x-custom"])))))))))

(deftest test-sse-close-on-channel-close
  (testing "response completes when ch is closed"
    (let [ch (async/chan 16)]
      (with-server {:http-handler
                    (fn [req]
                      (sse/response! req {:input-ch ch})
                      nil)}
        (let [response-future (future (client/get (str endpoint "/sse")
                                                  {:as :string}))]
          (Thread/sleep 200)
          (async/close! ch)
          (let [resp (deref response-future 5000 nil)]
            (is (some? resp) "response should complete after ch is closed")
            (is (= 200 (:status resp)))))))))

(deftest test-sse-client-disconnect
  (testing "handler unblocks promptly on client disconnect"
    (let [ch (async/chan 16)
          handler-returned (promise)]
      (with-server {:http-handler
                    (fn [req]
                      (sse/response! req {:input-ch ch
                                          :heartbeat-ms 500})
                      (deliver handler-returned true)
                      nil)}
        ;; fire request with short socket timeout to trigger disconnect
        (future
          (try
            (client/get (str endpoint "/sse")
                        {:as :stream
                         :socket-timeout 500})
            (catch Exception _e nil)))
        ;; handler should unblock within a few seconds of client disconnect
        (is (true? (deref handler-returned 5000 false))
            "handler should return after client disconnects")
        ;; ch should be closed by the writer cleanup
        (is (nil? (async/<!! ch))
            "ch should be closed after disconnect")))))

(deftest test-sse-brotli-compression
  (testing "brotli compressed SSE response has correct headers and body"
    (let [ch (async/chan 16)]
      (with-server {:http-handler
                    (fn [req]
                      (sse/response! req {:input-ch ch
                                          :compress {:quality 4
                                                     :window-size 18}})
                      nil)}
        (let [response-future (future (client/get (str endpoint "/sse")
                                                  {:as :byte-array
                                                   :decompress-body false}))]
          (Thread/sleep 200)
          (async/put! ch "compressed-msg")
          (Thread/sleep 100)
          (async/close! ch)
          (let [resp (deref response-future 5000 nil)]
            (is (= 200 (:status resp)))
            (is (= "br" (get-in resp [:headers "content-encoding"])))
            (is (pos? (count (:body resp))))))))))

(deftest test-sse-brotli-default-opts
  (testing "compress true uses defaults"
    (let [ch (async/chan 16)]
      (with-server {:http-handler
                    (fn [req]
                      (sse/response! req {:input-ch ch
                                          :compress true})
                      nil)}
        (let [response-future (future (client/get (str endpoint "/sse")
                                                  {:as :byte-array
                                                   :decompress-body false}))]
          (Thread/sleep 200)
          (async/put! ch "test")
          (Thread/sleep 100)
          (async/close! ch)
          (let [resp (deref response-future 5000 nil)]
            (is (= "br" (get-in resp [:headers "content-encoding"])))))))))

(deftest test-sse-input-ch
  (testing "user-provided input-ch is used"
    (let [my-ch (async/chan 4)]
      (with-server {:http-handler
                    (fn [req]
                      (sse/response! req {:input-ch my-ch})
                      nil)}
        (let [response-future (future (client/get (str endpoint "/sse")
                                                  {:as :string}))]
          (Thread/sleep 200)
          (async/put! my-ch "from-my-ch")
          (Thread/sleep 100)
          (async/close! my-ch)
          (let [resp (deref response-future 5000 nil)]
            (is (str/includes? (:body resp) "data: from-my-ch"))))))))
