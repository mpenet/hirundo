# mīnā

[![Clojars Project](https://img.shields.io/clojars/v/com.s-exp/mina.svg)](https://clojars.org/com.s-exp/mina)

[Helidon/Nima](https://helidon.io/nima) [RING](https://github.com/ring-clojure/ring/blob/master/SPEC) compliant adapter for clojure, loom based 

**Warning** It's early days so expect breakage.

[Helidon/Nima](https://helidon.io/nima) is alpha2 status right now, so do not use this in prod please. 


```clojure
(require 's-exp.mina)

(def server
  (mina/start! (fn [{:as request :keys [body headers ...]}]
                  {:status 200
                   :body "Hello world"
                   :headers {"Something" "Interesting"}})
               {:port 8080}))
;; ...

(mina/stop! server)
```

## Supported options

* `:host` - host of the default socket, defaults to 127.0.0.1

* `:port` - port the server listens to, defaults to random free port

* `:default-socket` - map-of `:write-queue-length` `:backlog` `:max-payload-size` `:receive-buffer-size` `:connection-options`(map-of `:socket-receive-buffer-size` `:socket-send-buffer-size` `:socket-reuse-address` `:socket-keep-alive` `:tcp-no-delay` `:read-timeout` `:connect-timeout`)

* `:ssl-context` - A `javax.net.ssl.SSLContext`

* `:tls` - A `io.helidon.nima.common.tls.Tls` instance


You can hook into the server builder via `s-exp.mina.options/set-server-option!`
multimethod at runtime and add/modify whatever you want if you need anything
extra we don't provide (yet).

http2 (h2 & h2c) is supported out of the box, iif a client connects with http2
it will do the protocol switch automatically.

## Installation

Note: You need to use java19 and add `:jvm-opts ["--enable-preview"]` to the
alias you will use to be able to run it.

https://clojars.org/com.s-exp/mina

## Running the tests 

```
clj -X:test s-exp.mina-test-runner/run
```

## License

Copyright © 2022 Max Penet

Distributed under the Eclipse Public License version 1.
