# ADR 0002 — Agent-driven live review (LLM loop + Otehon CDCI wiring)

- Status: proposed design + working offline implementation (2026-11-09).
  No live runs yet.
- Tracking: [AWAI-160](https://linear.app/kotobalabs/issue/AWAI-160).
- Builds on: [ADR 0001](0001-browserstack-oss-uiux-review.md).

## 1. What the agent uses

An agent (Hermes profile with this repo's MCP entry, or any MCP-capable
runner) drives reviews through `scripts/mcp_server.cljc`:

| Layer | Content | Side effects |
|---|---|---|
| `src/ui_review/core.cljc` | validate / matrix / verdict / strip-secrets | none |
| `src/ui_review/agent_loop.cljc` | `plan-step` / `loop-reduce` / `summarize` | none |
| `scripts/mcp_server.cljc` | JSON-RPC edges + live gate | stdio only, unless live-gated |
| owner transport (not in repo) | vendor specifics | network, screenshots |

The harness never invokes an LLM itself: reasoning stays on the agent's
own rail (Hermes profile rail rules apply — explicit Awai node `/v1`
for Otehon profiles, no silent remote fallback). The harness only
validates proposed actions and folds outcomes, so any rail can drive it.

## 2. Why the transport is owner-provided

Two reasons. First, cleanroom: this repo holds shape + policy only, no
vendor specifics (ADR 0001 §3). Second, credentials: the transport runs
where the secrets live (CI secrets or a fleet node vault) and is never
checked in. The contract is fixed (`agent/SKILL.md`): one JSON object
on stdin, exactly one JSON object on stdout, exit 0; anything else is
`transport-failed` and stops the loop.

## 3. Otehon CDCI integration point (awai-os side, not edited here)

Otehon CI lives in [`network-awai/awai-os`](https://github.com/network-awai/awai-os)
(Nix build + NixOS VM + `.cljc`, per network-awai-apex ADR 0026 D3).
This repo exposes two plug-in points; choosing between them, and any
edit to `awai-os`, is a separate owner decision:

1. **Dispatch:** an `awai-os` `otehon` job triggers this repo's
   `oracle-live-review` workflow (`workflow_dispatch`, secrets-gated).
   The workflow stays advisory: never a merge gate on either side until
   an owner separately requires it.
2. **Fleet gate:** per `network-awai-apex` `docs/distributed-ci.md`, a
   fleet node runs the agent loop with the MCP server and signs a
   receipt. CI truth stays the signed receipt, not the Actions UI.

In both cases the live job needs, per session: pinned target digest,
matrix, owner approval, secrets, and transport. Missing any one fails
closed (`blocked`) and sends nothing.

## 4. Verification so far

- `bb scripts/run_cases.cljc`: 22/22 (13 R0 + 9 loop policy).
- `bb scripts/mcp_selfcheck.cljc`: 5/5 (initialize, tools/list = 9 tools,
  validate accept, policy refusal, live blocked without secrets).
- `oracle-live-review` asserts the CI live path stays
  `live-transport-missing`-blocked until a transport exists.

## 5. Open questions

1. First live target pin (shell build digest + dApp screens).
2. Owner transport implementation for BrowserStack Automate.
3. Dispatch vs fleet gate for the `awai-os` side (owner decision).
4. `itonami-registry` `mcp/ui-review` + `api/com-browserstack` entries
   as follow-up PRs there, not here.
