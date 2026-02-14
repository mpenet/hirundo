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
            [clojure.java.io :as io]
            [s-exp.hirundo :as hirundo]
            [s-exp.hirundo.sse :as sse]))

;; --- Datastar SSE event helpers ---
;; These produce maps compatible with hirundo's format-event:
;;   {:event "..." :data "..."}
;; format-event adds `data: ` prefixes and splits multiline data,
;; which maps directly to Datastar's expected wire format.

;; event: datastar-patch-elements
;; data: elements <div id="foo">Hello world!</div>
;; data: elements <div id="foo">Hello world!</div>

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

;; (defn patch-signals
;;   "Returns an SSE message map for Datastar's datastar-patch-signals event.
;;   `signals` is a JSON string of signals to merge."
;;   [signals & {:keys [only-if-missing]}]
;;   {:event "datastar-patch-signals"
;;    :data (str/join "\n"
;;                    (cond-> []
;;                      only-if-missing (conj "onlyIfMissing true")
;;                      true (conj (str "signals " signals))))})

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
  (let [ch (async/chan 16)]
    (async/go-loop []
      (let [time-str (.format (java.time.LocalTime/now)
                              (java.time.format.DateTimeFormatter/ofPattern "HH:mm:ss"))]
        (when (async/>! ch (patch-elements
                            [(str "<div id=\"clock\" class=\"time\">" time-str "</div>")]))
          (async/<! (async/timeout 1000))
          (recur))))
    (sse/stream! request
                 :input-ch ch
                 :compression {:type :brotli :quality 4 :window-size 18})
    nil))

(defn handle-count
  "Counts from 1 to 20, then stops."
  [request]
  (let [ch (async/chan 16)]
    (async/go-loop [n 1]
      (when (<= n 20)
        (when (async/>! ch (patch-elements
                            [(str "<div id=\"counter\" class=\"counter\">" n "</div>")]))
          (async/<! (async/timeout 200))
          (recur (inc n)))))
    (sse/stream! request
                 :input-ch ch
                 :compression {:type :brotli :quality 4 :window-size 18})
    nil))

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
  (let [ch (async/chan 16)]
    (async/go-loop [i 0]
      (let [msg (nth feed-messages (mod i (count feed-messages)))
            time-str (.format (java.time.LocalTime/now)
                              (java.time.format.DateTimeFormatter/ofPattern "HH:mm:ss"))
            items-html (str "<div id=\"feed\">"
                            "<div class=\"feed-item\">"
                            "<strong>" time-str "</strong> " msg
                            "</div></div>")]
        (when (async/>! ch (patch-elements [items-html] :mode :inner :selector "#feed"))
          (async/<! (async/timeout (+ 1000 (rand-int 2000))))
          (recur (inc i)))))
    (sse/stream! request
                 :input-ch ch
                 :compression {:type :brotli :quality 4 :window-size 18})
    nil))

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
    "/feed" (handle-feed request)
    "/datastar.js" handle-js
    {:status 404 :body "Not found"}))

;; --- Entry point ---

(defn -main [& _args]
  (let [server (hirundo/start! {:http-handler handler
                                :port 8080
                                ;; :max-in-memory-entity 1
                                ;; :smart-async-writes true
                                ;; :write-buffer-size -1
                                })]
    (println "Hirundo + Datastar demo running at http://localhost:8080")
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable #(hirundo/stop! server)))))

;; (-main)
