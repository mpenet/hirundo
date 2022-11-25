# com.s-exp/nima

Helidon/Nima [RING](https://github.com/ring-clojure/ring/blob/master/SPEC) adapter for clojure, loom based 

It's early days to expect breakage. But it mostly works already, just remember
Nima is alpha2 status right now, so do not use this in prod please.


```clojure
(require 's-exp.nima)

(def server
  (nima/start! {:port 8080
                :handler
                (fn [{:as request :keys [body headers ...]}]
                  {:status 200
                   :body "Hello world"
                   :headers {"Something" "Interesting"}})}))
;; ...

(nima/stop! server)

```

## Installation

```clojure
{:deps {com.s-exp/nima {:git/sha "..." :git/url "https://github.com/mpenet/nima"}}}
```

## License

Copyright © 2022 Max Penet

Distributed under the Eclipse Public License version 1.0.
