#!/usr/bin/env bb
;; Maintained self-check for scripts/mcp_server.bb (offline tools only,
;; no secrets, no network). Spawns the server, speaks JSON-RPC over
;; stdio, and fail-closes (exit 1) on any mismatch.
(require '[cheshire.core :as json]
         '[clojure.java.io :as io]
         '[babashka.process :as p])

(def root-dir (-> *file* io/file .getParent io/file .getParent str))

(def proc
  (p/process ["bb" (str root-dir "/scripts/mcp_server.bb")]
             {:in :pipe :out :pipe :err :inherit :dir root-dir}))

(def stdin-writer (io/writer (:in proc)))
(def stdout-reader (io/reader (:out proc)))

(defn send! [msg]
  (.write stdin-writer (str (json/generate-string msg) "\n"))
  (.flush stdin-writer))

(defn recv! []
  (json/parse-string (.readLine stdout-reader)))

(def checks (atom []))
(defn check! [id ok detail]
  (swap! checks conj [id ok])
  (println (if ok "PASS" "FAIL") id (when-not ok (pr-str detail))))

;; 1. initialize
(send! {"jsonrpc" "2.0" "id" 1 "method" "initialize" "params" {}})
(let [r (recv!)]
  (check! "initialize" (= "ui-review" (get-in r ["result" "serverInfo" "name"])) r))

;; 2. tools/list: 9 tools
(send! {"jsonrpc" "2.0" "id" 2 "method" "tools/list" "params" {}})
(let [r (recv!)
      names (set (map #(get % "name") (get-in r ["result" "tools"])))]
  (check! "tools-list" (= #{"validate-target" "build-matrix" "reduce-verdict"
                            "plan-step" "loop-reduce" "summarize"
                            "session-start" "session-act" "session-snapshot"}
                          names)
          names))

(defn call! [id tool args]
  (send! {"jsonrpc" "2.0" "id" id "method" "tools/call"
          "params" {"name" tool "arguments" args}})
  (json/parse-string (get-in (recv!) ["result" "content" 0 "text"])))

;; 3. validate-target accepts a good descriptor
(check! "call-validate-target"
        (= true (get (call! 3 "validate-target"
                            {"target" {"name" "shell"
                                       "origin" "https://example.invalid/s/"
                                       "kinds" ["shell"]
                                       "artifact-digest" "sha256:abc"}})
                     "ok"))
        nil)

;; 4. plan-step refuses a forbidden action
(check! "call-plan-step-refuses"
        (= ["forbidden-action"]
           (map name (get (call! 4 "plan-step"
                                 {"loop" {"steps" [] "snapshots" 0}
                                  "action" {"type" "click-submit" "params" {}}})
                          "reasons")))
        nil)

;; 5. session-start without secrets is blocked, not an error
(check! "call-session-blocked"
        (= "blocked" (get (call! 5 "session-start" {"session" "s1"}) "status"))
        nil)

(.close stdin-writer)
(p/destroy proc)

(let [failed (count (remove second @checks))]
  (println (str (count @checks) " checks, " failed " failed"))
  (System/exit (if (zero? failed) 0 1)))
