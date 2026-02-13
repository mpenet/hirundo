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
;; format-event is (defn- format-event [& {:as msg}])
;; It takes keyword args destructured as a map.
;; :data must be sequential (iterated with run!).

(deftest test-format-event-data-only
  (testing "map with :data vector produces data fields"
    (is (= "data: hello\n\n"
           (sse/event :data ["hello"])))))

(deftest test-format-event-map-all-fields
  (testing "map with all fields"
    (let [result (sse/event :event "update"
                            :id "42"
                            :retry "3000"
                            :data ["hello"])]
      (is (str/includes? result "event: update\n"))
      (is (str/includes? result "id: 42\n"))
      (is (str/includes? result "retry: 3000\n"))
      (is (str/includes? result "data: hello\n"))
      (is (str/ends-with? result "\n\n")))))

(deftest test-format-event-multiline-data
  (testing "multiple data entries produce multiple data fields"
    (is (= "data: line1\ndata: line2\n\n"
           (sse/event :data ["line1" "line2"])))))

(deftest test-format-event-event-no-data
  (testing "map with event but no data"
    (is (= "event: ping\n\n"
           (sse/event :event "ping")))))

(deftest test-format-event-heartbeat
  (testing "heartbeat produces SSE comment"
    (is (= ":\n\n"
           (sse/event :comment "")))))

;; --- integration tests ---

(deftest test-sse-basic
  (testing "basic SSE: send messages and receive them"
    (let [ch (async/chan 16)]
      (with-server {:http-handler
                    (fn [req]
                      (sse/stream! req :input-ch ch)
                      (Thread/sleep 500)
                      nil)}
        (let [response-future (future (client/get (str endpoint "/sse")
                                                  {:as :string}))]
          (Thread/sleep 200)
          (async/put! ch {:data ["msg1"]})
          (async/put! ch {:data ["msg2"]})
          (async/put! ch {:event "custom" :data ["msg3"]})
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
                      (sse/stream! req :input-ch ch
                                   :headers {"x-custom" "test"})
                      (Thread/sleep 500)
                      nil)}
        (let [response-future (future (client/get (str endpoint "/sse")
                                                  {:as :string}))]
          (Thread/sleep 200)
          (async/put! ch {:data ["hi"]})
          (Thread/sleep 100)
          (async/close! ch)
          (let [resp (deref response-future 5000 nil)]
            (is (= "test" (get-in resp [:headers "x-custom"])))))))))

(deftest test-sse-close-on-channel-close
  (testing "response completes when ch is closed"
    (let [ch (async/chan 16)]
      (with-server {:http-handler
                    (fn [req]
                      (sse/stream! req :input-ch ch)
                      (Thread/sleep 500)
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
          close-ch (async/promise-chan)]
      (with-server {:http-handler
                    (fn [req]
                      (sse/stream! req :input-ch ch
                                   :close-ch close-ch
                                   :heartbeat-ms 500)
                      (Thread/sleep 1000)
                      nil)}
        ;; fire request with short socket timeout to trigger disconnect
        (future
          (try
            (client/get (str endpoint "/sse")
                        {:as :stream
                         :socket-timeout 500})
            (catch Exception _e nil)))
        ;; close-ch should be closed after client disconnects
        (is (nil? (async/alt!! close-ch ([v] v)
                               (async/timeout 5000) ::timeout))
            "close-ch should be closed after disconnect")
        ;; input-ch should be closed by the writer cleanup
        (is (nil? (async/<!! ch))
            "ch should be closed after disconnect")))))

(deftest test-sse-brotli-compression
  (testing "brotli compressed SSE response has correct headers and body"
    (let [ch (async/chan 16)]
      (with-server {:http-handler
                    (fn [req]
                      (sse/stream! req :input-ch ch
                                   :compression {:type :brotli
                                                 :quality 4
                                                 :window-size 18})
                      (Thread/sleep 500)
                      nil)}
        (let [response-future (future (client/get (str endpoint "/sse")
                                                  {:as :byte-array
                                                   :headers {:accept-encoding "br"}
                                                   :decompress-body false}))]
          (Thread/sleep 200)
          (async/put! ch {:data ["compressed-msg"]})
          (Thread/sleep 100)
          (async/close! ch)
          (let [resp (deref response-future 5000 nil)]
            (is (= 200 (:status resp)))
            (is (= "br" (get-in resp [:headers "content-encoding"])))
            (is (pos? (count (:body resp))))))))))

(deftest test-sse-brotli-default-opts
  (testing "brotli with default opts"
    (let [ch (async/chan 16)]
      (with-server {:http-handler
                    (fn [req]
                      (sse/stream! req :input-ch ch
                                   :compression {:type :brotli})
                      (Thread/sleep 500)
                      nil)}
        (let [response-future (future (client/get (str endpoint "/sse")
                                                  {:as :byte-array
                                                   :headers {"Accept-encoding" "br"}
                                                   :decompress-body false}))]
          (Thread/sleep 200)
          (async/put! ch {:data ["test"]})
          (Thread/sleep 100)
          (async/close! ch)
          (let [resp (deref response-future 5000 nil)]
            (is (= "br" (get-in resp [:headers "content-encoding"])))))))))

(deftest test-sse-input-ch
  (testing "user-provided input-ch is used"
    (let [my-ch (async/chan 4)]
      (with-server {:http-handler
                    (fn [req]
                      (sse/stream! req :input-ch my-ch)
                      (Thread/sleep 500)
                      nil)}
        (let [response-future (future (client/get (str endpoint "/sse")
                                                  {:as :string}))]
          (Thread/sleep 200)
          (async/put! my-ch {:data ["from-my-ch"]})
          (Thread/sleep 100)
          (async/close! my-ch)
          (let [resp (deref response-future 5000 nil)]
            (is (str/includes? (:body resp) "data: from-my-ch"))))))))

(deftest test-sse-close-ch
  (testing "externally closing close-ch terminates the stream"
    (let [input-ch (async/chan 16)
          close-ch (async/promise-chan)]
      (with-server {:http-handler
                    (fn [req]
                      (sse/stream! req
                                   :input-ch input-ch
                                   :close-ch close-ch)
                      (Thread/sleep 500)
                      nil)}
        (let [response-future (future (client/get (str endpoint "/sse")
                                                  {:as :string}))]
          (Thread/sleep 200)
          (async/put! input-ch {:data ["before-close"]})
          (Thread/sleep 100)
          (async/close! close-ch)
          (let [resp (deref response-future 5000 nil)]
            (is (some? resp) "response should complete after close-ch is closed")
            (is (str/includes? (:body resp) "data: before-close"))))))))
