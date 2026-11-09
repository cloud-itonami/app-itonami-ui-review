#!/usr/bin/env bb
;; Offline case runner for app-itonami-ui-review (no secrets, no network).
;; Loads the pure .cljc nucleus and checks every case in
;; cases/review-cases.edn by deep equality. Any mismatch fail-closes
;; the whole run (exit 1).
(require '[clojure.edn :as edn]
         '[clojure.java.io :as io])

(load-file (str (-> *file* io/file .getParent io/file .getParent)
                "/src/ui_review/core.cljc"))

(def fns
  {"valid-target?" ui-review.core/valid-target?
   "matrix" ui-review.core/matrix
   "verdict" ui-review.core/verdict
   "strip-secrets" ui-review.core/strip-secrets})

(defn run-case [{:keys [id fn args expect]}]
  (let [f (get fns fn)
        actual (apply f args)]
    (if (= expect actual)
      (do (println "PASS" id) true)
      (do (println "FAIL" id)
          (println "  expect:" (pr-str expect))
          (println "  actual:" (pr-str actual))
          false))))

(let [cases (-> (str (-> *file* io/file .getParent io/file .getParent)
                    "/cases/review-cases.edn")
                slurp
                edn/read-string)
      results (mapv run-case cases)
      failed (count (remove true? results))]
  (println (str (count results) " cases, " failed " failed"))
  (System/exit (if (zero? failed) 0 1)))
