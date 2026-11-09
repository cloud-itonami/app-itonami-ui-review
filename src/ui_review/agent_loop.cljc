(ns ui-review.agent-loop
  "Bounded observe -> act -> verdict loop policy for LLM-driven UI review.
   Pure: proposes nothing, executes nothing. Validates proposed actions
   against an allowlist and budgets, and folds step outcomes into loop
   state. All side effects (transport, credentials, screenshots) live in
   the owner-provided transport and the MCP server edges, never here.
   Depends on ui-review.core being loaded first (qualified calls only,
   no :require, so plain load-file order works on every runner)."
  (:require [clojure.string :as str]))

(def default-budget
  "Default loop bounds. Over-budget is a stop, never a silent extension."
  {:max-steps 24
   :max-snapshots 12})

(def allowed-actions
  "Read-mostly action allowlist for first scope. Anything else is
   refused so the LLM cannot invent state-changing operations."
  #{:navigate :snapshot :assert-visible :assert-text :back :reload})

(def refused-param-keys
  "Params carrying these keys are refused: review traffic must never
   carry credentials."
  #{:password :secret :seed-phrase :private-key :token})

(defn- blank?
  [s]
  (or (nil? s) (and (string? s) (str/blank? s))))

(defn- https-url?
  [s]
  (and (string? s) (str/starts-with? s "https://") (> (count s) 10)))

(defn plan-step
  "Validate one proposed agent action against policy.
   loop   {:keys [steps snapshots budget]} (missing budget => defaults)
   action {:keys [type params]}
   Returns {:ok true :action action} or {:ok false :reasons [...]}."
  [loop action]
  (let [budget (merge default-budget (:budget loop))
        steps (vec (:steps loop))
        snapshots (or (:snapshots loop) 0)
        params (or (:params action) {})
        reasons (cond-> []
                  (not (contains? allowed-actions (:type action)))
                  (conj :forbidden-action)

                  (>= (count steps) (:max-steps budget))
                  (conj :budget-exhausted)

                  (and (= :snapshot (:type action))
                       (>= snapshots (:max-snapshots budget)))
                  (conj :snapshot-budget-exhausted)

                  (and (= :navigate (:type action))
                       (not (https-url? (:url params))))
                  (conj :non-https-navigation-refused)

                  (some refused-param-keys (keys params))
                  (conj :credential-params-refused))]
    (if (empty? reasons)
      {:ok true :action action}
      {:ok false :reasons reasons})))

(defn loop-reduce
  "Fold one executed step outcome into loop state (pure).
   state {:keys [steps snapshots status stop-reason budget]}
   (missing budget => defaults).
   event {:keys [action outcome]} where outcome
         {:keys [step-status observation]} and step-status is one of
         :pass :fail :blocked.
   Unknown outcomes fail-close the loop to :stopped."
  [state event]
  (if (not= :running (:status state))
    state
    (let [outcome (:outcome event)
          ok-status? (contains? #{:pass :fail :blocked}
                                 (:step-status outcome))]
      (if (not ok-status?)
        (-> state
            (update :steps conj event)
            (assoc :status :stopped :stop-reason :unknown-outcome))
        (let [next (cond-> (update state :steps conj event)
                     (= :snapshot (get-in event [:action :type]))
                     (update :snapshots (fnil inc 0)))
              done? (true? (:done outcome))]
          (cond
            (= :fail (:step-status outcome))
            (assoc next :status :stopped :stop-reason :step-failed)

            done?
            (assoc next :status :done)

            (>= (count (:steps next))
                (:max-steps (merge default-budget (:budget state))))
            (assoc next :status :stopped :stop-reason :budget-exhausted)

            :else next))))))

(defn summarize
  "Build a receipt-ready summary from a finished loop (pure).
   Secrets are stripped before the receipt leaves the agent."
  [loop-state]
  (let [steps (vec (:steps loop-state))
        verdict (ui-review.core/verdict
                 (mapv (fn [e]
                         {:step (or (get-in e [:action :type]) :unknown)
                          :status (get-in e [:outcome :step-status] :unknown)})
                       steps))]
    {:loop-status (:status loop-state)
     :stop-reason (:stop-reason loop-state)
     :verdict verdict
     :receipt (ui-review.core/strip-secrets
               {:steps steps :verdict verdict})}))
