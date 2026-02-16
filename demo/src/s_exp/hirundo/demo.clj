(ns s-exp.hirundo.demo
  "Datastar SSE demo for hirundo.

  Demonstrates:
  - Serving an HTML page with Datastar attributes
  - SSE streaming with s-exp.hirundo.sse/stream!
  - datastar-patch-elements events (DOM updates)
  - datastar-patch-signals events (reactive state)
  - Brotli compression on the SSE stream
  - Client disconnect detection via heartbeat"
  (:require [clojure.core.async :as async]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [s-exp.hirundo :as hirundo]
            [s-exp.hirundo.sse :as sse]))

;; --- D* helpers ---
;; some d* helpers, you don't really need more than that

(defn- patch-element
  [element {:keys [selector mode use-view-transition]}]
  (let [sb (StringBuilder.)]
    (.append sb "elements ")
    (when selector
      (.append sb (str "selector " (name selector))))
    (when mode
      (.append sb (str "mode " (name mode))))
    (when use-view-transition
      (.append sb (str "useViewTransition " (boolean use-view-transition))))
    (.append sb element)
    (.toString sb)))

(defn patch-elements
  "Returns an SSE message map for Datastar's datastar-patch-elements event.
  `html` is the HTML fragment to patch into the DOM.
  `opts` can include :selector, :mode, :use-view-transition."
  [elements & {:keys [selector mode use-view-transition] :as opts}]
  {:event "datastar-patch-elements"
   :data (mapv #(patch-element % opts) elements)})

(defn- patch-signal
  [signal {:keys []}]
  (let [sb (StringBuilder.)]
    (.append sb "signals ")
    (.append sb (json/write-str signal))
    (.toString sb)))

(defn patch-signals
  "Returns an SSE message map for Datastar's datastar-patch-signals event.
  `html` is the HTML fragment to patch into the DOM.
  `opts` can include :selector, :mode, :use-view-transition."
  [signals & {:keys [only-if-missing] :as opts}]
  {:event "datastar-patch-signals"
   :data (cond-> []
           only-if-missing
           (conj ["onlyIfMissing" only-if-missing])
           :then
           (into (mapv #(patch-signal % opts) signals)))})

;; --- HTML page ---

(def index-html
  "<!DOCTYPE html>
<html lang=\"en\">
<head>
  <meta charset=\"UTF-8\">
  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
  <title>Hirundo + Datastar Demo</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: system-ui, sans-serif; max-width: 640px; margin: 2rem auto; padding: 0 1rem; color: #1a1a1a; }
    h1 { margin-bottom: 1rem; }
    h2 { margin: 1.5rem 0 0.5rem; font-size: 1.1rem; }
    button { padding: 0.4rem 1rem; border: 1px solid #999; border-radius: 4px; background: #f5f5f5; cursor: pointer; }
    button:hover { background: #e5e5e5; }
    .card { border: 1px solid #ddd; border-radius: 6px; padding: 1rem; margin: 0.5rem 0; }
    .counter { font-size: 2rem; font-weight: bold; font-variant-numeric: tabular-nums; }
    .time { font-family: monospace; font-size: 1.2rem; }
    .feed-item { padding: 0.3rem 0; border-bottom: 1px solid #eee; font-size: 0.9rem; }
    .feed-item:last-child { border-bottom: none; }
  </style>
  <script type=\"module\" src=\"datastar.js\"></script>
</head>
<body>


  <h1>Hirundo + Datastar</h1>

  <!-- Live clock: streams time updates via SSE on page load -->
  <h2>Live Clock</h2>
  <div class=\"card\" data-init=\"@get('/clock')\">
    <div id=\"clock\" class=\"time\">Loading...</div>
  </div>

  <!-- Counter: click to start a counting stream -->
  <h2>Counter</h2>
  <div class=\"card\">
    <div id=\"counter\" class=\"counter\">0</div>
    <br>
    <button data-on:click=\"@get('/count')\">Start counting</button>
  </div>

  <!-- Shop stats: demonstrates datastar-patch-signals (reactive state) -->
  <h2>Shop Stats (signals)</h2>
  <div class=\"card\" data-signals-orders=\"0\" data-signals-revenue=\"0.00\" data-signals-last__item=\"'...'\" data-init=\"@get('/shop-stats')\">
    <div style=\"display:grid;grid-template-columns:1fr 1fr;gap:0.5rem\">
      <div>Orders: <strong data-text=\"$orders\"></strong></div>
      <div>Revenue: <strong data-text=\"'$'+$revenue\"></strong></div>
    </div>
    <div style=\"margin-top:0.5rem;font-size:0.9rem\">Last sold: <em data-text=\"$last__item\"></em></div>
  </div>

  <!-- Live feed: simulated event feed with brotli compression -->
  <h2>Live Feed (brotli compressed)</h2>
  <div class=\"card\" data-init=\"@get('/feed')\">
    <div id=\"feed\">Connecting...</div>
  </div>
</body>
</html>")

;; --- Route handlers ---

(defn handle-index [_request]
  {:status 200
   :headers {"content-type" "text/html"}
   :body index-html})

(defn handle-clock
  "Streams the current time every second."
  [request]
  (let [{:as _stream :keys [input-ch _close-ch]}
        (sse/stream! request)]
    (loop []
      (let [time-str (.format (java.time.LocalTime/now)
                              (java.time.format.DateTimeFormatter/ofPattern "HH:mm:ss"))]
        (when (async/>!! input-ch (patch-elements
                                   [(str "<div id=\"clock\" class=\"time\">" time-str "</div>")]))
          (Thread/sleep 1000)
          (recur))))))

(defn handle-count
  "Counts from 1 to 1e6, then stops."
  [request]
  (let [{:keys [input-ch]} (sse/stream! request)]
    (dotimes [n 1e6]
      (when (async/>!! input-ch (patch-elements
                                 [(str "<div id=\"counter\" class=\"counter\">" n "</div>")]))
        (Thread/sleep 50)))))

(def shop-items
  [{:name "Wireless Headphones" :price 59.99}
   {:name "USB-C Cable" :price 12.99}
   {:name "Mechanical Keyboard" :price 89.00}
   {:name "Mouse Pad" :price 15.50}
   {:name "Webcam HD" :price 45.00}
   {:name "Monitor Stand" :price 34.99}
   {:name "Laptop Sleeve" :price 22.00}
   {:name "Phone Charger" :price 19.99}])

(defn handle-shop-stats
  "Streams shop signals: order count, revenue, last item sold."
  [request]
  (let [{:keys [input-ch]} (sse/stream! request
                                        :compression {:type :brotli :quality 4 :window-size 18})]
    (loop [orders 0
           revenue 0.0]
      (let [{:keys [name price]} (rand-nth shop-items)
            orders (inc orders)
            revenue (+ revenue price)]
        (when (async/>!! input-ch (patch-signals [{"orders" orders
                                                   "revenue" (format "%.2f" revenue)
                                                   "last__item" name}]))
          (Thread/sleep (+ 800 (rand-int 500)))
          (recur orders revenue))))))

(def feed-messages
  ["New user signed up"
   "Order #4521 placed"
   "Payment processed"
   "Deployment started"
   "Build succeeded"
   "Cache invalidated"
   "Report generated"
   "Backup completed"
   "Alert resolved"
   "Config updated"])

(defn handle-feed
  "Simulates a live event feed with brotli compression."
  [request]
  (let [{:keys [input-ch]} (sse/stream! request)]
    (loop [i 0]
      (let [msg (nth feed-messages (mod i (count feed-messages)))
            time-str (.format (java.time.LocalTime/now)
                              (java.time.format.DateTimeFormatter/ofPattern "HH:mm:ss"))
            items-html (str "<div id=\"feed\">"
                            "<div class=\"feed-item\">"
                            "<strong>" time-str "</strong> " msg
                            "</div></div>")]
        (when (async/>!! input-ch (patch-elements [items-html] :mode :inner :selector "#feed"))
          (Thread/sleep (+ 100 (rand-int 1000)))
          (recur (inc i)))))))

(def handle-js
  {:headers {"content-type" "text/javascript"}
   :status 200
   :body (slurp (io/resource "datastar.js"))})

;; --- Router ---

(defn handler [request]
  (case (:uri request)
    "/" (handle-index request)
    "/clock" (handle-clock request)
    "/count" (handle-count request)
    "/shop-stats" (handle-shop-stats request)
    "/feed" (handle-feed request)
    "/datastar.js" handle-js
    {:status 404 :body "Not found"}))

;; --- Entry point ---

(defn -main [& _args]
  (let [server (hirundo/start! {:http-handler handler
                                :port 8080})]
    (println "Hirundo + Datastar demo running at http://localhost:8080")
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable #(hirundo/stop! server)))))
