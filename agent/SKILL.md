# SKILL.md — LLM-driven UIUX review with app-itonami-ui-review

Use this app when the owner asks for an LLM-based UIUX review of an
Otehon shell build or an itonami dApp screen on real browsers/devices.

## Hard rules (never override)

1. Credentials never flow through the loop. If any step proposes
   `:password`, `:secret`, `:seed-phrase`, `:private-key`, or `:token`
   params, `plan-step` refuses — do not retry with renamed keys.
2. Real wallets, funds, and real-user data are out of scope.
   Scope is shell + dApp read-only screens (ADR 0001 §2).
3. Live tools need a separate, per-session owner approval:
   `UI_REVIEW_LIVE=1`, BrowserStack secrets, and an owner-provided
   transport. Without all three, live calls return `blocked` — report
   that, do not work around it.
4. A passing review certifies nothing about Native builds and triggers
   no production cutover.

## Procedure

1. `validate-target` — pin the target: name, `https://` origin, kinds,
   artifact digest. Refuse non-https origins and missing digests.
2. `build-matrix` — expand browsers × devices × viewports (cap 64).
   Over-cap is an error: narrow the axes, never truncate silently.
3. Per session, run the bounded loop (default 24 steps / 12 snapshots):
   a. Observe: last snapshot + current loop state.
   b. Reason: propose exactly one action from the allowlist
      (`navigate`, `snapshot`, `assert-visible`, `assert-text`,
      `back`, `reload`).
   c. `plan-step`: on refusal, stop and report the reasons.
      Never rephrase a refused action to evade policy.
   d. Live call (`session-act` / `session-snapshot`) via the transport.
   e. `loop-reduce`: fold the outcome. On `:stopped`, end the session.
4. `summarize` — verdict + secret-stripped receipt per session.
5. `reduce-verdict` across sessions for the review-level verdict.
6. Write receipts: date, tool versions, input hash, per-step digests.
   No secrets in receipts.

## Transport contract (owner-provided executable)

- Invoked as `<transport>` with one JSON object on stdin:
  `{"tool": ..., "args": {...}, "credentials": {"username": ..., "access-key": ...}}`.
- Must reply with exactly one JSON object on stdout and exit 0.
  Any other outcome is recorded as `transport-failed` and stops the loop.
- The transport owns all vendor specifics. This repo never describes them.

## First run is always offline

Before any live session: `bb scripts/run_cases.cljc` (22 cases) and
`bb scripts/mcp_selfcheck.cljc` (5 checks) must pass. If they do not,
do not proceed to live.
