# Verification record

## Capacity slice — September 7, 2026

The capacity build passed 19 deterministic tests and three opt-in live-model
tests using the same `gpt-4.1` connection and framework revision listed below.
`mvnw.cmd package` passed the deterministic suite, TypeScript check, Vite build
and JAR packaging; the three provider tests were run separately with
`-Drelay.live=true -Dtest=LiveInvestigationTest` and all passed.

New checks include 128 two-instance/fault/load combinations, exact capacity
boundaries, rejecting resolution when only customer checks pass, preserving
demand and independent faults during a restart, stale proposals after traffic
changes, no supported repair for full-pool overload, and a populated V1 → V2
migration that preserves the original snapshot JSON.

The existing local file database was backed up before upgrading. Its previous
incident, report, receipts, activity and execution events were compared before
and after the upgrade and preserved. The environment revision advanced once,
and historical snapshots retained single-instance semantics.

The packaged UI was exercised through **Lost redundancy → Open incident →
Verify recovery → Peak traffic → Investigate → Start checkout B → Verify**.
Verification correctly kept the risk incident open while customer requests
passed. Peak demand produced 100 successful and 50 failed requests/s. The
application and capacity specialists recommended checkout B's specific restart.
The repair preserved 150 requests/s of demand, restored 200 requests/s of online
capacity, and passed both recovery checks. The two resolved incidents remain in
the presenter's database. The capacity dashboard was visually inspected at the
desktop browser size; mobile device testing was not performed.

## Original slice baseline

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
