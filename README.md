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

## SSE (Server-Sent Events)

Hirundo provides built-in SSE support via `s-exp.hirundo.sse/stream!`.
Call it from within a ring handler — it takes over the response, streaming
events to the client until the channel is closed or the client disconnects.

`stream!` returns a map of `{:input-ch :close-ch}`. Put event maps onto
`input-ch` to send them; close `input-ch` or `close-ch` to end the stream.

```clojure
(require '[s-exp.hirundo.sse :as sse])
(require '[clojure.core.async :as async])

(defn my-handler [request]
  (let [{:keys [input-ch close-ch]} (sse/stream! request)]
      (async/>!! input-ch {:event "update"
                          :data ["{\"count\": 1}"]
                          :id "1"})
      ;; close to end the SSE stream
      (async/close! input-ch)))

```

Messages are maps with keys `:event`, `:data`, `:id`, `:retry`.
`:data` is a vector of strings — each entry becomes a separate `data:` line
per the SSE spec.

### Options

* `:input-ch` - user-provided `core.async` channel. If not provided, one is
  created with a buffer of 10.
* `:close-ch` - user-provided `core.async` promise channel for close signaling.
  If not provided, one is created internally.
* `:headers` - extra headers to merge into the SSE response.
* `:compression` - compression settings map. Defaults to
  `{:type :brotli :quality 4 :window-size 18}`. Only applied when the client
  sends `accept-encoding: br`. Keys:
  * `:type` - compression type (currently only `:brotli`)
  * `:quality` - compression quality (0-11)
  * `:window-size` - LZ77 window size (10-24)
* `:heartbeat-ms` - interval in ms for heartbeat SSE comments to detect client
  disconnect (default 1500).

### Brotli compression

When `:compression` is set, the response is compressed with brotli (`content-encoding: br`).
This can significantly reduce bandwidth for text-heavy SSE streams.

```clojure
(let [{:keys [input-ch]} (sse/stream! request
                                      :compression {:type :brotli
                                                    :quality 4
                                                    :window-size 18})]
  ;; put events onto input-ch
  )
```

### Client disconnect detection

A heartbeat SSE comment (`:` line, ignored by clients) is sent periodically. When
the any write fails (client gone), both channels are closed and the stream ends.
Configure the interval with `:heartbeat-ms`.

### Datastar demo

The `demo/` directory contains a full [Datastar](https://data-star.dev/) example
that demonstrates SSE streaming with hirundo. It includes:

- **Live Clock** — streams the current time every second via `patch-elements`
- **Counter** — click to start a counting stream
- **Shop Stats** — uses `patch-signals` to stream reactive state (order count, revenue, last item sold) without replacing DOM elements
- **Live Feed** — simulated event log with brotli compression

Run it with:

```
cd demo && clj -M -m s-exp.hirundo.demo
```

Then open http://localhost:8080.

## Installation

Note: You need to use java **21**

https://clojars.org/com.s-exp/hirundo

## Running the tests 

```
clj -X:test
```

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
