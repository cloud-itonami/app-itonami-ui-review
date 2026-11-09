# app-itonami-ui-review — BrowserStack OSS-compatible UIUX review harness

Public itonami dApp repo for reviewing the UIUX of Awai Otehon OS
(React shell + Chromium) and itonami dApps on real browsers/devices,
using the BrowserStack Open Source program.

- Status: **R0 design + pure nucleus**. No live BrowserStack runs yet.
- Tracking: [AWAI-160](https://linear.app/kotobalabs/issue/AWAI-160)
  (Otehon policy; network-awai-apex ADR 0026).
- License: AGPL-3.0-or-later (dApp convention, same as mihari/dandori).
- Design: [docs/adr/0001-browserstack-oss-uiux-review.md](docs/adr/0001-browserstack-oss-uiux-review.md)
- Core (pure, portable `.cljc`): `src/ui_review/core.cljc`
- Cases (data): `cases/review-cases.edn`, runner: `scripts/run_cases.cljc`

## Why a separate public repo

`network-awai-apex` is private, so it cannot itself qualify for the
BrowserStack Open Source program. This repo is public (AGPL-3.0-or-later) and holds only
the review harness: shape declarations, pure verdict logic, and case data.
Review targets (Otehon shell builds, dApp artifacts) are referenced by
digest and fetched at run time; they are never vendored here.

## Cleanroom boundary

- No BrowserStack SDK, client library, or vendor code is vendored.
- `compat/browserstack-shape.json` declares the used surface
  (Automate session + Percy snapshot) as shape-only metadata (L4:
  no vendor conformance claimed).
- Registration/run grants no authority: no credentials in the repo,
  no network operation without per-run approval and CI secrets,
  no consent implications for end users.

## Run the cases (no secrets needed)

```sh
bb scripts/run_cases.cljc      # 22 shared cases, offline
bb scripts/mcp_selfcheck.cljc  # MCP server: 5 checks, offline
```

Cases run offline against the pure nucleus. Live BrowserStack runs are a
separate, secrets-gated step (see the ADRs) and are not part of the default
check.

## Agent use (LLM-driven review)

Agents connect via `mcp/ui-review` (stdio, `bb scripts/mcp_server.cljc`)
and follow `agent/SKILL.md`: validate target → build matrix → bounded
observe→act→verdict loop (`plan-step` gates every action) → `summarize`
receipts. Offline tools always work; live tools (`session-start` /
`session-act` / `session-snapshot`) refuse with `blocked` unless the
owner arms `UI_REVIEW_LIVE=1` with secrets and a transport executable.
Design: [ADR 0002](docs/adr/0002-agent-driven-live-review.md).

## License

AGPL-3.0-or-later — see [LICENSE](LICENSE). Vendored content: none.
