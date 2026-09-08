# Verification record

Verified locally on September 7, 2026 with Java 21.0.2, the configured OpenAI
connection using `gpt-4.1`, and Loomspan framework commit
`fbd9db5926f890fe6df658e5676c3dda8e64ce34` (`1.0.0-beta.2-SNAPSHOT`).

## Automated

`mvnw.cmd package -Drelay.live=true` passed: 14 tests, no failures or skips.
The same build ran the TypeScript check and Vite production build and produced
`target/relay-0.1.0-SNAPSHOT.jar` with its embedded frontend.

Thirteen deterministic tests cover all fault combinations, persistence-backed
workflow transitions, immutable snapshots, stale assessments, evidence/action
validation, compound recovery, concurrent repair idempotency, interrupted run
recovery, missing control fields, HTTP responses and supported Java API invocation.
The opt-in model test performs two real nested investigations against a two-fault
environment and verifies that both repairs are needed to resolve the incident.

## Browser

The packaged app was exercised at `http://127.0.0.1:8083`:

1. Healthy environment → blocked database connection → failed customer checkout.
2. Open incident → real model investigation → three saved probe receipts.
3. Supported network repair → healthy checkout while incident remains mitigated.
4. Fresh verification → resolved incident.
5. Coordination view → recorded application, network and database specialist
   overlap, with the final evidence read after the investigators.

The observed browser investigation took approximately 12 seconds. This is one
local observation, not a latency guarantee. Initial, running and completed layouts
were visually inspected in the in-app browser. Responsive styles are included;
mobile device testing was not performed.

The packaged server was stopped and restarted against its default file database.
The full incident detail and environment state matched exactly before and after:
resolved status, three receipts, sixteen execution events, five incident history
entries, and environment revision 3. The persisted incident was also reopened in
the browser after restart.

## Model validation finding

An initial model run diagnosed the right problem but invented an action ID and
cited a healthy database receipt for a network repair. Application validation
rejected it. The final design uses a supported action enum and an explicit final
read of the authoritative evidence record after specialists complete. Validation
remains strict; a future invalid report fails without applying any repair.

Successful live tests establish the tested scenario and model combination, not
universal provider reliability. Ordinary builds skip provider tests unless the
`relay.live` property is explicitly enabled.
