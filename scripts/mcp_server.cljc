#!/usr/bin/env bb
;; MCP stdio server for app-itonami-ui-review (newline-delimited JSON-RPC).
;; Offline tools run the pure .cljc nucleus. Live tools (session-start,
;; session-act, session-snapshot) fail closed unless ALL of the following
;; hold: UI_REVIEW_LIVE=1, BROWSERSTACK_USERNAME and
;; BROWSERSTACK_ACCESS_KEY are set, and UI_REVIEW_TRANSPORT points at an
;; owner-provided executable transport. The repo ships no transport and no
;; credentials; refusal is a legitimate tool outcome, not a protocol error.
(require '[cheshire.core :as json]
         '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[babashka.process :as p])

(load-file (str (-> *file* io/file .getParent io/file .getParent)
                "/src/ui_review/core.cljc"))
(load-file (str (-> *file* io/file .getParent io/file .getParent)
                "/src/ui_review/agent_loop.cljc"))

(def server-info
  {:name "ui-review" :version "0.1.0"})

(def tools
  [{:name "validate-target"
    :description "Validate a review target descriptor (offline, pure)."
    :inputSchema {:type "object"}}
   {:name "build-matrix"
    :description "Expand review axes into session specs (offline, pure, bounded)."
    :inputSchema {:type "object"}}
   {:name "reduce-verdict"
    :description "Reduce step results to a verdict (offline, pure)."
    :inputSchema {:type "object"}}
   {:name "plan-step"
    :description "Validate one proposed agent action against loop policy (offline, pure)."
    :inputSchema {:type "object"}}
   {:name "loop-reduce"
    :description "Fold one step outcome into loop state (offline, pure)."
    :inputSchema {:type "object"}}
   {:name "summarize"
    :description "Build a receipt-ready, secret-stripped loop summary (offline, pure)."
    :inputSchema {:type "object"}}
   {:name "session-start"
    :description "Start a live review session via the owner transport (gated)."
    :inputSchema {:type "object"}}
   {:name "session-act"
    :description "Policy-check then perform one live action via the owner transport (gated)."
    :inputSchema {:type "object"}}
   {:name "session-snapshot"
    :description "Capture a live snapshot via the owner transport (gated)."
    :inputSchema {:type "object"}}])

;; --- JSON edge: translate plain JSON maps into nucleus maps ---

(defn- kw-status [s] (keyword (str s)))

(defn- arg-target [m]
  {:target/name (get m "name")
   :target/origin (get m "origin")
   :target/kinds (set (map keyword (get m "kinds" [])))
   :target/artifact-digest (get m "artifact-digest")})

(defn- arg-axes [m]
  {:browsers (vec (get m "browsers" []))
   :devices (vec (get m "devices" []))
   :viewports (vec (get m "viewports" []))})

(defn- arg-steps [v]
  (mapv (fn [s] {:step (get s "step")
                 :status (kw-status (get s "status"))})
        v))

(defn- arg-action [m]
  {:type (keyword (get m "type"))
   :params (into {} (map (fn [[k v]] [(keyword k) v])) (get m "params" {}))})

(defn- arg-loop [m]
  {:steps (vec (get m "steps" []))
   :snapshots (or (get m "snapshots") 0)
   :budget (get m "budget")})

;; --- live gate ---

(defn- live-env []
  (let [e (System/getenv)]
    {:enabled? (= "1" (get e "UI_REVIEW_LIVE"))
     :username (get e "BROWSERSTACK_USERNAME")
     :access-key (get e "BROWSERSTACK_ACCESS_KEY")
     :transport (get e "UI_REVIEW_TRANSPORT")}))

(defn- live-refusal [{:keys [enabled? username access-key transport]}]
  (cond
    (not enabled?) {:status :blocked :reasons [:live-not-enabled]}
    (or (str/blank? username) (str/blank? access-key))
    {:status :blocked :reasons [:live-credentials-missing]}
    (str/blank? transport)
    {:status :blocked :reasons [:live-transport-missing]}
    :else nil))

(defn- via-transport [transport payload creds]
  (try
    (let [proc (p/process [transport] {:in :pipe :out :pipe :err :pipe})
          in (:in proc)]
      (.write in (.getBytes (json/generate-string
                             (assoc payload :credentials creds))
                            "UTF-8"))
      (.write in (int \newline))
      (.flush in)
      (.close in)
      (let [out (slurp (:out proc))
            {:keys [exit]} @proc]
        (if (zero? exit)
          {:status :transport-ok
           :response (json/parse-string (str/trim out) true)}
          {:status :blocked
           :reasons [:transport-failed]
           :exit exit
           :stderr (slurp (:err proc))})))
    (catch Exception e
      {:status :blocked :reasons [:transport-error] :message (str e)})))

(defn- live-call [tool args]
  (let [env (live-env)]
    (if-let [refusal (live-refusal env)]
      refusal
      (via-transport (:transport env)
                     {:tool tool :args args}
                     {:username (:username env)
                      :access-key (:access-key env)}))))

;; --- tool dispatch ---

(defn- call-tool [tool-name args]
  (case tool-name
    "validate-target"
    (ui-review.core/valid-target? (arg-target (get args "target" {})))

    "build-matrix"
    (let [axes (arg-axes (get args "axes" {}))
          cap (get args "cap")]
      (if cap
        (ui-review.core/matrix axes cap)
        (ui-review.core/matrix axes)))

    "reduce-verdict"
    (ui-review.core/verdict (arg-steps (get args "steps" [])))

    "plan-step"
    (ui-review.agent-loop/plan-step (arg-loop (get args "loop" {}))
                                    (arg-action (get args "action" {})))

    "loop-reduce"
    (ui-review.agent-loop/loop-reduce
     (assoc (arg-loop (get args "state" {}))
            :status (kw-status (get (get args "state" {}) "status" "running"))
            :stop-reason (some-> (get (get args "state" {}) "stop-reason") keyword))
     {:action (arg-action (get args "action" {}))
      :outcome {:step-status (kw-status (get-in args ["outcome" "step-status"]))
                :done (true? (get-in args ["outcome" "done"]))}})

    "summarize"
    (ui-review.agent-loop/summarize
     {:steps (vec (get args "steps" []))
      :snapshots (or (get args "snapshots") 0)
      :status (kw-status (get args "status" "running"))
      :stop-reason (some-> (get args "stop-reason") keyword)})

    "session-start"
    (live-call "session-start" args)

    "session-act"
    (let [check (ui-review.agent-loop/plan-step
                 (arg-loop (get args "loop" {}))
                 (arg-action (get args "action" {})))]
      (if (:ok check)
        (live-call "session-act" args)
        {:status :blocked :reasons [:policy-refused]
         :policy (:reasons check)}))

    "session-snapshot"
    (live-call "session-snapshot" args)

    {:status :blocked :reasons [:unknown-tool] :tool tool-name}))

;; --- JSON-RPC loop ---

(defn- respond [id result]
  (println (json/generate-string {:jsonrpc "2.0" :id id :result result}))
  (flush))

(defn- respond-error [id code message]
  (println (json/generate-string {:jsonrpc "2.0" :id id
                                  :error {:code code :message message}}))
  (flush))

(defn- handle [msg]
  (let [id (get msg "id")
        method (get msg "method")
        params (get msg "params" {})]
    (cond
      (= method "initialize")
      (respond id {:protocolVersion "2024-11-05"
                   :capabilities {:tools {}}
                   :serverInfo server-info})

      (= method "tools/list")
      (respond id {:tools tools})

      (= method "tools/call")
      (let [result (call-tool (get params "name") (get params "arguments" {}))]
        (respond id {:content [{:type "text"
                                :text (json/generate-string result)}]}))

      (= method "notifications/initialized")
      nil

      (nil? id)
      nil ;; other notifications: no reply

      :else
      (respond-error id -32601 (str "unknown method: " method)))))

(doseq [line (line-seq (io/reader *in*))]
  (when-not (str/blank? line)
    (try
      (handle (json/parse-string line))
      (catch Exception e
        (respond-error nil -32700 (str "parse error: " (.getMessage e)))))))
