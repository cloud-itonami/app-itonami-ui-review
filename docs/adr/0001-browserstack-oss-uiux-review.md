# ADR 0001 — BrowserStack OSS-compatible UIUX review for Otehon OS and itonami dApps

- Status: proposed design (2026-11-09). No live runs, no OSS application filed yet.
- Tracking: [AWAI-160](https://linear.app/kotobalabs/issue/AWAI-160).
- Context: network-awai-apex ADR 0026 (D3 CI scope, D12 shell+dapp shape,
  D18 `.cljc` first, D20 shared nucleus) and
  [aiueos-otehon.md](https://github.com/network-awai/network-awai-apex/blob/main/docs/aiueos-otehon.md)
  (VM-only verification so far).

## 1. Decision

Build the UIUX review harness as a **separate public itonami dApp repo**
(this repo), not inside `network-awai-apex`:

1. `network-awai-apex` is private and cannot qualify for the BrowserStack
   Open Source program. This repo is public AGPL-3.0-or-later, so it
   can be the applicant and the audit surface.
2. The harness holds **shape + pure logic + case data only**.
   Review targets (Otehon React shell builds, dApp artifacts) are
   referenced by digest and resolved at run time, never vendored.
3. The nucleus is portable `.cljc` (per ADR 0026 D18/D20): the same
   verdict/matrix logic runs under clj/JVM, nbb, and babashka.

## 2. Non-goals

- No Native certification: a passing review never certifies Kotoba/Amu
  native builds and never triggers an Otehon production cutover.
- No wallet, funds, or real-user data: first scope is shell + `saifu`
  read-only screens. Sending and signing stay out of scope.
- No automatic pass/fail gating of other repos: this job is advisory
  evidence until an owner separately decides otherwise.

## 3. Cleanroom boundary (compat shape, L4)

- Vendor code in repo: none. No SDK, no client copy.
- `compat/browserstack-shape.json` declares entities (`session`,
  `snapshot`, `verdict`) and routes (session create/delete, snapshot
  post, verdict reduce) as **shape-only** metadata:
  `vendorConformance: not-claimed`, `runtimeQualified: false`,
  `authority: grants-none`.
- The shape may cite vendor docs URLs as evidence, but the repo never
  derives behavior from them; behavior comes from our own cases.

## 4. Review flow

1. **Digest check** — accept a target only with a pinned artifact digest;
   mismatch refuses before any upload (negative control).
2. **Matrix build** (`ui-review.core/matrix`) — pure expansion of declared
   axes (browsers × devices × viewports), bounded (default cap 64).
   Over-cap is an error, not a silent truncation.
3. **Run** — secrets-gated live step (manual dispatch, CI secrets only).
   Without secrets the job fail-closes: it reports `blocked: no-credentials`
   and sends nothing.
4. **Verdict reduce** (`ui-review.core/verdict`) — fail dominates blocked
   dominates pass; unknown step status fail-closes to `:fail`.
5. **Receipts** — date, tool versions, input hash, per-step evidence
   digests; secrets stripped by `strip-secrets` before writing.

## 5. CI placement

- Default check (`oracle-ui-cases`): offline cases only, no secrets,
  named as an oracle job. Never a merge requirement on its own;
  requirement status is a separate owner decision.
- Live job: `workflow_dispatch` only, requires
  `BROWSERSTACK_USERNAME`/`BROWSERSTACK_ACCESS_KEY` secrets, non-blocking,
  receipts uploaded as run artifacts.

## 6. OSS program application (owner action, pending)

The repo being public AGPL-3.0-or-later makes an application possible; filing it
(BrowserStack Open Source program: Live/Automate/Percy on real
browsers/devices) is a separate owner decision, as is which artifact
builds are named as the reviewed project.

## 7. Open questions

1. First target pin: which shell build digest + which dApp screens.
2. Percy visual-diff thresholds and noise policy (collect-only first).
3. Whether the `com-browserstack` shape should also be registered as an
   `api` entry in `itonami-registry` (follow-up PR there, not here).
