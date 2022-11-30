(ns s-exp.mina-test
  "Tests vars run in parallel"
  (:require [clj-http.client :as client]
            [clojure.test :refer :all]
            [s-exp.mina :as m]))

(def ^:dynamic *endpoint*)

(defmacro with-server [options & body]
  `(let [server# (m/start! ~options)]
     (binding [*endpoint* (format "http://localhost:%s/" (.port server#))]
       (try
         ~@body
         (finally (m/stop! server#))))))

(deftest test-headers
  (with-server {:handler (fn [req] {:headers {:foo "bar"}})}
    (is (-> (client/get *endpoint*) :headers :foo (= "bar"))))

  (with-server {:handler (fn [req] {:headers {:foo ["bar" "baz"]}})}
    (is (-> (client/get *endpoint*) :headers :foo (= ["bar" "baz"])))))

(deftest test-status
  (with-server {:handler (fn [req] {:status 201})}
    (is (-> (client/get *endpoint*) :status (= 201)))))

(deftest test-query-string
  (with-server {:handler (fn [req] {:body (:query-string req)})}
    (is (-> (client/get (str *endpoint* "?foo=bar")) :body (= "foo=bar")))))

(deftest test-method
  (with-server {:handler (fn [req] {:body (str (:request-method req))})}
    (is (-> (client/post *endpoint*) :body (= ":post"))))

  (with-server {:handler (fn [req] {:body (str (:request-method req))})}
    (is (-> (client/put *endpoint*) :body (= ":put"))))

  (with-server {:handler (fn [req] {:body (str (:request-method req))})}
    (is (-> (client/delete *endpoint*) :body (= ":delete")))))

(deftest test-uri
  (with-server {:handler (fn [req] {:body (:uri req)})}
    (is (-> (client/delete (str *endpoint* "foo/bar")) :body (= "/foo/bar")))))

(deftest test-scheme
  (with-server {:handler (fn [req] {:body (str (:scheme req))})}
    (is (-> (client/get *endpoint*) :body (= ":http")))))

(deftest test-body
  (with-server {:handler (fn [req] {})}
    (prn (client/get *endpoint*))
    (is (-> (client/get *endpoint*) :body (= ""))))

  (with-server {:handler (fn [req] {:body "yes"})}
    (is (-> (client/get *endpoint*) :body (= "yes"))))

  (with-server {:handler (fn [req] {:body ["yes" "no"]})}
    (is (-> (client/get *endpoint*) :body (= "yesno"))))

  (with-server {:handler (fn [req] {:body (.getBytes "yes")})}
    (is (-> (client/get *endpoint*) :body (= "yes"))))

  (with-server {:handler (fn [req] {:body (java.io.ByteArrayInputStream. (.getBytes "yes"))})}
    (is (-> (client/get *endpoint*) :body (= "yes")))))
