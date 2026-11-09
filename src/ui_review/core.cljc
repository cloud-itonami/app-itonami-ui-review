(ns ui-review.core
  "Pure nucleus for BrowserStack OSS-compatible UIUX review.
   Side-effect free: validation, matrix expansion, verdict reduction,
   and secret stripping for receipts. Portable across clj, cljs, nbb,
   and babashka."
  (:require [clojure.string :as str]))

(def allowed-kinds
  "Review target kinds in first scope."
  #{:shell :dapp})

(def max-sessions
  "Default bound for matrix expansion. Over-cap is an error, never a
   silent truncation."
  64)

(defn- blank?
  [s]
  (or (nil? s) (and (string? s) (str/blank? s))))

(defn- https-url?
  [s]
  (and (string? s) (str/starts-with? s "https://") (> (count s) 10)))

(defn valid-target?
  "Validate a review target descriptor.
   Returns {:ok true :target t} or {:ok false :reasons [...]}."
  [t]
  (let [reasons (cond-> []
                  (blank? (:target/name t))
                  (conj :blank-name)

                  (not (https-url? (:target/origin t)))
                  (conj :origin-must-be-https)

                  (or (not (set? (:target/kinds t)))
                      (empty? (:target/kinds t))
                      (not (every? allowed-kinds (:target/kinds t))))
                  (conj :unknown-or-empty-kinds)

                  (blank? (:target/artifact-digest t))
                  (conj :missing-artifact-digest))]
    (if (empty? reasons)
      {:ok true :target t}
      {:ok false :reasons reasons})))

(defn matrix
  "Pure expansion of review axes into session specs.
   axes => {:browsers [...] :devices [...] :viewports [...]}
   Returns {:ok true :sessions [...]} or {:ok false :reasons [...]}.
   Empty axes and over-cap matrices are errors (fail-closed)."
  ([axes] (matrix axes max-sessions))
  ([axes cap]
   (let [{:keys [browsers devices viewports]} axes
         reasons (cond-> []
                   (empty? browsers) (conj :no-browsers)
                   (empty? devices) (conj :no-devices)
                   (empty? viewports) (conj :no-viewports))]
     (if (seq reasons)
       {:ok false :reasons reasons}
       (let [sessions (vec (for [b browsers d devices v viewports]
                             {:browser b :device d :viewport v}))]
         (if (> (count sessions) cap)
           {:ok false
            :reasons [:matrix-over-cap]
            :count (count sessions)
            :cap cap}
           {:ok true :sessions sessions}))))))

(def step-statuses
  #{:pass :fail :blocked})

(defn verdict
  "Reduce step results to a review verdict (pure).
   Each step: {:step <id> :status (:pass|:fail|:blocked) ...}.
   Precedence: :fail > :blocked > :pass.
   Unknown or missing status fail-closes to :fail."
  [steps]
  (let [steps (vec steps)
        bad (filter #(not (contains? step-statuses (:status %))) steps)]
    (if (seq bad)
      {:status :fail
       :reasons [:unknown-step-status]
       :offenders (mapv :step bad)}
      (let [failed (mapv :step (filter #(= :fail (:status %)) steps))
            blocked (mapv :step (filter #(= :blocked (:status %)) steps))]
        (cond
          (seq failed)
          {:status :fail :failed failed :evidence-count (count steps)}

          (seq blocked)
          {:status :blocked :blocked blocked :evidence-count (count steps)}

          :else
          {:status :pass :evidence-count (count steps)})))))

(def secret-key-names
  "Key names treated as secrets when writing receipts."
  #{:password :passwd :secret :api-key :api_key :access-key :access_key
    :token :auth-token :username :user})

(defn strip-secrets
  "Recursively remove secret-named keys from nested maps/vectors.
   Unknown (non-secret) data passes through unchanged."
  [x]
  (cond
    (map? x)
    (into {} (comp (remove (fn [[k _]] (contains? secret-key-names k)))
                   (map (fn [[k v]] [k (strip-secrets v)])))
             x)
    (vector? x)
    (mapv strip-secrets x)
    (seq? x)
    (map strip-secrets x)
    :else x))
