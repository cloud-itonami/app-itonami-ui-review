# mcp/ui-review — agent connection definition

Hermes-layout MCP entry for `scripts/mcp_server.bb`
(newline-delimited JSON-RPC over stdio).

## Tools

Offline (always available, pure):

- `validate-target`, `build-matrix`, `reduce-verdict`
- `plan-step`, `loop-reduce`, `summarize`

Live (gated — refuse with `status: blocked` unless all hold):

- `session-start`, `session-act`, `session-snapshot`
- Gates: `UI_REVIEW_LIVE=1`, `BROWSERSTACK_USERNAME` /
  `BROWSERSTACK_ACCESS_KEY` set, `UI_REVIEW_TRANSPORT` pointing at an
  owner-provided executable transport.
- `session-act` additionally runs `plan-step` first; a policy refusal
  never reaches the transport.

## Registration grants nothing

Copying this entry, like every registry entry, grants no authority:
no credentials, no network operation, no consent. Live use needs a
separate, per-session owner approval plus the secrets above.
