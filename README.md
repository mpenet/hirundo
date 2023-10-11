# mīnā

[![Clojars
Project](https://img.shields.io/clojars/v/com.s-exp/mina.svg)](https://clojars.org/com.s-exp/mina)

[Helidon/Nima](https://helidon.io/nima)
[RING](https://github.com/ring-clojure/ring/blob/master/SPEC) compliant adapter
for clojure, loom based

## Usage

```clojure
(require '[s-exp.mina :as mina])
(require '[s-exp.mina.websocket :as ws])

(def server
  (mina/start! { ;; regular ring handler
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

(mina/stop! server)

```

```

```

There is nothing special to its API, you use mina as you would use any blocking
http adapter like jetty; it is RING compliant so compatible with most/all
middlewares out there.

## Supported options

* `:host` - host of the default socket, defaults to 127.0.0.1

* `:port` - port the server listens to, defaults to random free port

* `:http-handler` - ring handler function

* `:websocket-endpoints` - /!\ subject to changes - (map-of string-endpoint handler-fns-map), where handler if can be of `:message`, `:ping`, `:pong`, `:close`, `:error`, `:open`, `:http-upgrade`. `handler-fns-map` can also contain 2 extra keys, `:extensions`, `:subprotocols`, which are sets of subprotocols and protocol extensions acceptable by the server

* `:write-queue-length` 

* `:backlog` 

* `:max-payload-size` 

* `:write-queue-length`

* `:receive-buffer-size` 

* `:connection-options`(map-of `:socket-receive-buffer-size` `:socket-send-buffer-size` `:socket-reuse-address` `:socket-keep-alive` `:tcp-no-delay` `:read-timeout` `:connect-timeout`)

* `:tls` - A `io.helidon.nima.common.tls.Tls` instance


You can hook into the server builder via `s-exp.mina.options/set-server-option!`
multimethod at runtime and add/modify whatever you want if you need anything
extra we don't provide (yet).

http2 (h2 & h2c) is supported out of the box, iif a client connects with http2
it will do the protocol switch automatically.

## Installation

Note: You need to use java **21**

https://clojars.org/com.s-exp/mina

## Running the tests 

```
clj -X:test s-exp.mina-test-runner/run
```

## Implemented

- [x] HTTP (1.1 & 2) server/handlers
- [x] WebSocket handlers (initial implementation)
- [ ] Grpc handlers

## License

Copyright © 2023 Max Penet

Distributed under the Eclipse Public License version 1.
