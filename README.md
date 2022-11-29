# com.s-exp/mina

Helidon/Nima [RING](https://github.com/ring-clojure/ring/blob/master/SPEC) compliant adapter for clojure, loom based 

It's early days so expect breakage. 
Helidon/Nima is alpha2 status right now, so do not use this in prod please.


```clojure
(require 's-exp.mina)

(def server
  (mina/start! {:port 8080
                :handler
                (fn [{:as request :keys [body headers ...]}]
                  {:status 200
                   :body "Hello world"
                   :headers {"Something" "Interesting"}})}))
;; ...

(mina/stop! server)

```

## Installation

```clojure
{:deps {com.s-exp/mina {:git/sha "..." :git/url "https://github.com/mpenet/mina"}}}
```

Note: You need to use java19 and add `:jvm-opts ["--enable-preview"]` to the alias you will use to be able to run it.

## License

Copyright © 2022 Max Penet

Distributed under the Eclipse Public License version 1.0.
