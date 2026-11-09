# app-itonami-ui-review — BrowserStack OSS-compatible UIUX review harness

Public itonami dApp repo for reviewing the UIUX of Awai Otehon OS
(React shell + Chromium) and itonami dApps on real browsers/devices,
using the BrowserStack Open Source program.

- Status: **R0 design + pure nucleus**. No live BrowserStack runs yet.
- Tracking: [AWAI-160](https://linear.app/kotobalabs/issue/AWAI-160)
  (Otehon policy; network-awai-apex ADR 0026).
- Design: [docs/adr/0001-browserstack-oss-uiux-review.md](docs/adr/0001-browserstack-oss-uiux-review.md)
- Core (pure, portable `.cljc`): `src/ui_review/core.cljc`
- Cases (data): `cases/review-cases.edn`, runner: `scripts/run_cases.bb`

## Why a separate public repo

`network-awai-apex` is private, so it cannot itself qualify for the
BrowserStack Open Source program. This repo is public (MIT) and holds only
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
bb scripts/run_cases.bb
```

Cases run offline against the pure nucleus. Live BrowserStack runs are a
separate, secrets-gated step (see the ADR) and are not part of the default
check.

## License

MIT — see [LICENSE](LICENSE). Vendored content: none.
