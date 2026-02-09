# Hirundo [![Clojars Project](https://img.shields.io/clojars/v/com.s-exp/hirundo.svg)](https://clojars.org/com.s-exp/hirundo)

<img src="https://github.com/mpenet/hirundo/assets/106390/7fb900d8-c8cb-4211-9c40-1caa97c1c269" data-canonical-src="https://github.com/mpenet/hirundo/assets/106390/7fb900d8-c8cb-4211-9c40-1caa97c1c269" width="150" height="150" />



[Helidon/Nima](https://helidon.io/nima)
[RING](https://github.com/ring-clojure/ring/blob/master/SPEC) compliant adapter
for clojure, loom based

## Usage

```clojure
(require '[s-exp.hirundo :as hirundo])
(require '[s-exp.hirundo.websocket :as ws])

(def server
  (hirundo/start! {;; regular ring handler
                :http-handler (fn [{:as request :keys [body headers ...]}]
                                {:status 200
                                 :body "Hello world"
                                 :headers {"Something" "Interesting"}})

                ;; websocket endpoints
                :websocket-endpoints {"/ws" {:message (fn [session data _last-msg]
                                                        ;; echo back data
                                                        (ws/send! session data true))
                                             :open (fn [session] (prn :opening-session))
                                             :close (fn [_session status reason]
                                                      (prn :closed-session status reason))
                                             :error (fn [session error]
                                                      (prn :error error))
                                             ;; :subprotocols ["chat"]
                                             ;; :extensions ["foobar"]
                                             ;; :http-upgrade (fn [headers] ...)
                                             }}
                :port 8080}))
;; ...

(hirundo/stop! server)

```


There is nothing special to its API, you use hirundo as you would use any blocking
http adapter like jetty; it is RING compliant so compatible with most/all
middlewares out there.

## Supported options

* `:host` - host of the default socket, defaults to 127.0.0.1

* `:port` - port the server listens to, defaults to random free port

* `:http-handler` - ring handler function

* `:websocket-endpoints` - /!\ subject to changes - (map-of string-endpoint
  handler-fns-map), where handler if can be of `:message`, `:ping`, `:pong`,
  `:close`, `:error`, `:open`, `:http-upgrade`. `handler-fns-map` can also
  contain 2 extra keys, `:extensions`, `:subprotocols`, which are sets of
  subprotocols and protocol extensions acceptable by the server. Alternatively
  you can pass a fn that emits a map, or directly an Helidon WsListener. Both
  these options can be useful if you need to keep state for a connection
  lifecycle.

* `:write-queue-length` 

* `:backlog` 

* `:max-payload-size` 

* `:write-queue-length`

* `:receive-buffer-size` 

* `:connection-options`(map-of `:socket-receive-buffer-size` `:socket-send-buffer-size` `:socket-reuse-address` `:socket-keep-alive` `:tcp-no-delay` `:read-timeout` `:connect-timeout`)

* `:tls` - A `io.helidon.nima.common.tls.Tls` instance


You can hook into the server builder via `s-exp.hirundo.options/set-server-option!`
multimethod at runtime and add/modify whatever you want if you need anything
extra we don't provide (yet).

http2 (h2 & h2c) is supported out of the box, iif a client connects with http2
it will do the protocol switch automatically.

## Installation

Note: You need to use java **21**

https://clojars.org/com.s-exp/hirundo

## Running the tests 

```
clj -X:test
```

## Implemented

- [x] HTTP (1.1 & 2) server/handlers
- [x] WebSocket handlers (initial implementation)
- [ ] Grpc handlers

## Building Uberjars with hirundo

Because of the way helidon handles service configuration we need to carefuly
craft the uberjar with merged resources for some entries. 

You will need to provide `:conflict-handlers` for the uberjar task that
concatenates some of the files from resources found in helidon module
dependencies.


Pay attention to the `b/uber` call here:

```clj
(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.tools.build.api :as b]
            [clojure.tools.build.tasks.uber :as uber]))

(def lib 'foo/bar)
(def version "0.1.0-SNAPSHOT")
(def main 'foo.bar.baz)
(def class-dir "target/classes")
(defn- uber-opts [opts]
  (assoc opts
         :lib lib :main main
         :uber-file (format "target/%s-%s.jar" lib version)
         :basis (b/create-basis {})
         :class-dir class-dir
         :src-dirs ["src"]
         :ns-compile [main]))

(defn append-json
  [{:keys [path in existing state]}]
  {:write
   {path
    {:append false
     :string
     (json/write-str
      (concat (json/read-str (slurp existing))
              (json/read-str (#'uber/stream->string in))))}}})

(defn ci "Run the CI pipeline of tests (and build the uberjar)." [opts]
  (b/delete {:path "target"})
  (let [opts (uber-opts opts)]
    (println "\nCopying source...")
    (b/copy-dir {:src-dirs ["src"] :target-dir class-dir})
    (println (str "\nCompiling " main "..."))
    (b/compile-clj opts)
    (println "\nBuilding JAR...")
    
    ;; HERE is the important part
    (b/uber (assoc opts :conflict-handlers
                   {"META-INF/helidon/service.loader" :append-dedupe
                    "META-INF/helidon/feature-metadata.properties" :append-dedupe
                    "META-INF/helidon/config-metadata.json" append-json
                    "META-INF/helidon/service-registry.json" append-json})))

  opts)
```

## License

Copyright © 2023 Max Penet

Distributed under the Eclipse Public License version 1.
