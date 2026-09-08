# Relay — Loomspan incidents demo

A complete local incident workspace: break a simulated storefront, investigate
with coordinated Loomspan skills, apply a supported repair, and verify recovery.
The environment, incidents, immutable evidence, reports, and action history persist
in a real H2 database. Infrastructure is simulated; model investigation is real.

**Current slice:** cache outages and degradation that cascade into database saturation,
with application, capacity and database specialists correlating evidence. Includes
two checkout instances, variable traffic, persistent incidents and verified recovery.
See the [cache walkthrough and rules](docs/cache-slice.md).

## Run

Requires Java 21+, Node.js 22.13+ for building, and model access. The app consumes
`ai.loomspan:loomspan-spring-boot-starter:1.0.0-beta.2-SNAPSHOT`, built from framework
commit `fbd9db5926f890fe6df658e5676c3dda8e64ce34`. Install it from the sibling checkout:

```powershell
cd C:\opendev\code\loomspan-framework
.\mvnw.cmd -pl loomspan-spring-boot-starter -am install -DskipTests
cd C:\opendev\code\loomspan-incidents-demo
$env:OPENAI_API_KEY = 'your-key'
$env:RELAY_MODEL = 'gpt-4.1'
.\mvnw.cmd package
.\scripts\run.ps1
```

Open [Relay at localhost:8083](http://localhost:8083). The JAR serves both the
Spring API and React frontend; Docker and a separate frontend server are not
required. Stop the application before rebuilding its JAR on Windows. On other
platforms use `./mvnw package` and `java -jar target/relay-0.1.0-SNAPSHOT.jar`.

Model access is needed only for **Investigate with Loomspan**. Fault controls,
customer checks, incident records and recovery verification work without a key.
`.env.example` documents environment overrides; it is not loaded automatically.
Provider calls contain incident descriptions and simulated probe evidence.

For development, run `./mvnw spring-boot:run -DskipFrontend=true` and `npm run dev`
in `frontend/` after `npm ci`. Vite proxies `/api` to the default backend port 8083.
The app binds to loopback and has no production authentication or real infrastructure access.

## First walkthrough

1. Choose **Blocked connection**. Both processes remain running, but customer
   checkout fails with a database connection timeout.
2. Choose **Open incident**, leaving the description empty to use the actual symptom.
3. Choose **Investigate with Loomspan**. The commander selects application,
   network and database specialists. Their probes read the same saved environment
   snapshot. Evidence receipts appear as probes run.
4. Review the diagnosis and cited evidence, then **Apply simulated repair** for
   **Restore database connection**. Repairing does not resolve the incident.
5. Choose **Verify recovery**. A fresh synthetic checkout succeeds, resolving
   the incident and recording the verification revision.
6. Explore **Coordination** for the observed skill timeline and **History** for
   the incident record. Restart the app and select the incident from the sidebar;
   its history remains.

## Signature walkthrough: two faults

1. Choose **Two faults**, then open a new incident.
2. Investigate the checkout validation exception. The application specialist
   should identify the bad deployment and propose a rollback.
3. Apply the rollback, then verify. Checkout still fails: the blocked database
   path remains. The incident returns to **open**.
4. Reinvestigate the new symptom. Application, network and database findings
   distinguish blocked traffic from a stopped database.
5. Restore the database connection and verify again. This time checkout succeeds.

The presenter controls are separate from the investigator's view. Presets change
component state, never return canned diagnoses. An investigation receives its
opaque run ID, ticket and customer symptom, not the preset name or fault switches.
Model wording and chosen plans may vary; receipts, action validation and checkout
outcomes are deterministic.

## Capacity walkthrough: risk becomes impact

1. Choose **Lost redundancy**, then open an incident. Checkout A handles all
   60 requests/s; B is stopped. Customer checkout passes but **Redundancy lost**
   is shown separately. This is an availability risk, not a customer outage.
2. Investigate. The commander coordinates application and capacity specialists
   and can recommend **Start checkout B**. Try **Verify recovery** before repairing:
   the successful customer check alone cannot resolve an unmet availability target.
3. Change **Incoming traffic** to **Peak · 150 req/s**. One instance can handle
   only 100 requests/s, so 50 requests/s now fail. Any earlier proposal is stale.
4. Reinvestigate the current symptom, apply **Start checkout B**, then verify.
   Traffic stays at 150 requests/s, both instances are online, and all requests succeed.
5. For an infeasible case, use **Restore healthy**, choose **Beyond pool capacity ·
   240 req/s**, and open another incident. The pool admits 200 requests/s,
   but database load also saturates: 59 requests/s fail. There is no supported scale-out or traffic
   reduction repair; the investigation should explain the shortfall and escalate.

**Overloaded instance** presets step 3 directly. A broken deployment and the
database connection are shared by both instances; adding a healthy process cannot
repair broken code or a blocked dependency. See [capacity rules](docs/capacity-slice.md).

## Simulation and skill boundaries

`Simulation.java` defines independently composable checkout A/B power, incoming
demand, database power, network permission, shared deployment health, cache power and cache hit rate. The
gateway is always available and distributes requests to online instances. Each
supports 100 requests/s; excess requests receive simulated gateway 503 responses.
A bad deployment fails before the checkout
code reaches the database, so fixing it can reveal a second failure. Database
health is checked locally; network health is checked from the application path.

```text
investigateIncident (YAML planner)
  ├─ investigateApplication (focused YAML specialist) → inspectApplication (Java)
  ├─ investigateNetwork     (focused YAML specialist) → inspectNetwork     (Java)
  ├─ investigateDatabase    (focused YAML specialist) → inspectDatabase    (Java)
  ├─ investigateCapacity    (focused YAML specialist) → inspectCapacity    (Java)
  └─ readInvestigationEvidence (Java; after the selected specialists finish)
```

The commander dynamically selects specialists. Independent specialists may run
concurrently against an immutable snapshot. A final evidence-record read supplies
exact receipt and repair IDs so final synthesis need not reconstruct them from
specialist summaries. Direct child evidence contracts
require successful specialist/probe execution; application validation separately
checks that cited receipt IDs belong to the run and each recommended action is
offered by its cited receipt. These checks do not prove model prose is correct.

The model cannot mutate the environment. Repair buttons use deterministic
application services with exact action IDs, current-revision checks, latest-run
checks, and idempotency. One repair is applied before verification/reassessment.
Recovery requires both a successful fresh customer batch and the instance
availability target, not an LLM assertion.

## Persistence and lifecycle

The default database is `data/relay.mv.db`. Flyway initializes it once. Ordinary
restarts never reset records. **Restore healthy** resets only the simulated
component state, increments its revision, and preserves all incident history.
For a separate clean demo, set `RELAY_DATABASE_URL` to a new file database path.
V3 adds an online cache at 90% hit rate and increments the revision. Earlier snapshots
retain their original rules; all existing incident history remains intact.
V2 commissions checkout B online at 60 requests/s and increments the environment
revision, invalidating old repair proposals. Existing A/database/network/deployment
conditions and all incident records remain intact. Historical snapshots without
B retain their original single-instance semantics; their JSON is not rewritten.

Each investigation saves a snapshot before queueing. Changes during investigation
do not alter its evidence; the resulting assessment is marked historical in the
UI and cannot authorize a repair against a different revision. Active jobs are
marked failed on restart, retaining any evidence already collected. One incident
cannot run two investigations simultaneously. A bounded executor admits two
running jobs and six queued jobs. Framework missions have a 240-second timeout.

The supported `SkillTemplate` observer is called after successful execution.
Accordingly, live progress shows application-owned probe receipts; the Loomspan
start/finish timeline appears afterward. Failure receipts are retained, but a
failed invocation may have no public session view. Optional Console access uses
`RELAY_OBSERVABILITY_ENABLED` and `RELAY_OBSERVABILITY_API_KEY`; detailed traces
are retained by default with `RELAY_TRACE_PERSISTENCE=ALWAYS`.

## Verify

```powershell
# Deterministic integration tests; live-provider test is skipped
.\mvnw.cmd test -DskipFrontend=true

# Explicit model-backed compound incident test, isolated in memory
.\mvnw.cmd test -DskipFrontend=true '-Drelay.live=true' '-Dtest=LiveInvestigationTest'

# Frontend type check, production build, tests and runnable JAR
.\mvnw.cmd package
```

Tests cover all 16 original fault combinations plus 128 two-instance/load cases,
V1/V2 → V3 migrations, 707 cache/load combinations, independent cache repairs, redundancy versus outage, demand boundaries, capacity
repair and no-remedy scenarios, blocked-path evidence, compound recovery,
immutable snapshots, stale repair rejection, unsupported and cross-run receipts,
concurrent duplicate repairs, interrupted runs, public Java skill invocation,
and HTTP persistence/error contracts. See [design notes](docs/design.md) and the
[verification record](docs/verification.md).
