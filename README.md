# mīnā

[![Clojars Project](https://img.shields.io/clojars/v/com.s-exp/mina.svg)](https://clojars.org/com.s-exp/mina)

Helidon/Nima [RING](https://github.com/ring-clojure/ring/blob/master/SPEC) compliant adapter for clojure, loom based 

It's early days so expect breakage. 
Helidon/Nima is alpha2 status right now, so do not use this in prod please.


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

* `:host` - host of the default socket

* `:port` - port the server listens to, default to 8080

* `:default-socket` - map-of `:write-queue-length` `:backlog` `:max-payload-size` `:receive-buffer-size`

* `:ssl-context` - A `javax.net.ssl.SSLContext`

* `:tls` - A `io.helidon.nima.common.tls.Tls` instance

... more to come

You can hook into the server builder via `s-exp.mina/set-server-option!`
multimethod at runtime and add/modify whatever you want if you need anything
extra we don't provide (yet).

You can also configure the server via an application.yml file in the resources
(tbd).

## Installation

Note: You need to use java19 and add `:jvm-opts ["--enable-preview"]` to the
alias you will use to be able to run it.

https://clojars.org/com.s-exp/mina

## License

Copyright © 2022 Max Penet

Distributed under the Eclipse Public License version 1.
