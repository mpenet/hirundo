(ns s-exp.mina-test
  (:require [clj-http.client :as client]
            [clojure.edn :as edn]
            [clojure.test :refer :all]
            [s-exp.mina :as m]))

(defmacro with-server [opts & body]
  `(let [server# (m/start! ~opts)]
     (try
       ~@body
       (finally (m/stop! server#)))))

(deftest test-headers
  (with-server {:handler (fn [req] {:headers {:foo "bar"}})}
    (is (-> (client/get "http://localhost:8080/") :headers :foo (= "bar"))))

  (with-server {:handler (fn [req] {:headers {:foo ["bar" "baz"]}})}
    (is (-> (client/get "http://localhost:8080/") :headers :foo (= ["bar" "baz"])))))

(deftest test-status
  (with-server {:handler (fn [req] {:status 201})}
    (is (-> (client/get "http://localhost:8080/") :status (= 201)))))

(deftest test-query-string
  (with-server {:handler (fn [req] {:body (:query-string req)})}
    (is (-> (client/get "http://localhost:8080/?foo=bar") :body (= "foo=bar")))))

(deftest test-method
  (with-server {:handler (fn [req] {:body (str (:request-method req))})}
    (is (-> (client/post "http://localhost:8080/") :body (= ":post"))))

  (with-server {:handler (fn [req] {:body (str (:request-method req))})}
    (is (-> (client/put "http://localhost:8080/") :body (= ":put"))))

  (with-server {:handler (fn [req] {:body (str (:request-method req))})}
    (is (-> (client/delete "http://localhost:8080/") :body (= ":delete")))))

(deftest test-uri
  (with-server {:handler (fn [req] {:body (:uri req)})}
    (is (-> (client/delete "http://localhost:8080/foo/bar") :body (= "/foo/bar")))))

(deftest test-scheme
  (with-server {:handler (fn [req] {:body (str (:scheme req))})}
    (is (-> (client/get "http://localhost:8080/") :body (= ":http")))))

(deftest test-body
  (with-server {:handler (fn [req] {})}
    (prn (client/get "http://localhost:8080/"))
    (is (-> (client/get "http://localhost:8080/") :body (= ""))))

  (with-server {:handler (fn [req] {:body "yes"})}
    (is (-> (client/get "http://localhost:8080/") :body (= "yes"))))

  (with-server {:handler (fn [req] {:body ["yes" "no"]})}
    (is (-> (client/get "http://localhost:8080/") :body (= "yesno"))))

  (with-server {:handler (fn [req] {:body (.getBytes "yes")})}
    (is (-> (client/get "http://localhost:8080/") :body (= "yes"))))

  (with-server {:handler (fn [req] {:body (java.io.ByteArrayInputStream. (.getBytes "yes"))})}
    (is (-> (client/get "http://localhost:8080/") :body (= "yes")))))
