# Checkout capacity and redundancy

> This records the capacity slice before V3. Current worlds also model cache and
> database limits; see [cache rules](cache-slice.md). Historical snapshots keep these original rules.

## Demonstration

The storefront has checkout A and B. The gateway distributes work across online
instances. Each instance provides 100 requests/s of online capacity. Stopping one
does not automatically cause a customer outage: current demand determines impact.

| Instances online | Demand | Successful | Failed | System state |
| --- | --- | --- | --- | --- |
| 2 | 60 req/s | 60 req/s | 0 | Healthy |
| 1 | 60 req/s | 60 req/s | 0 | At risk: redundancy lost |
| 1 | 150 req/s | 100 req/s | 50 req/s | Degraded: partial customer failure |
| 0 | 60 req/s | 0 | 60 req/s | Outage |
| 2 | 240 req/s | 200 req/s | 40 req/s | Degraded: exceeds configured pool |

These examples assume healthy code, database and network. Deployment and database
access are shared by the pool. A regression or failed dependency drives successful
throughput to zero regardless of online capacity. Bringing up a process preserves
these independent faults and the incoming demand.

The presenter can set 60, 150 or 240 requests/s in the UI. The API accepts integer
demand from 1 through 400. These are simulated aggregate batches, not timed load
tests against real services. Demand at exactly the online capacity succeeds;
excess demand fails. Successful batches use 120 ms latency below or at 80% of
online capacity and 600 ms above 80%. Overload uses a fixed 1,000 ms marker.
Code, dependency and no-instance failures retain their own fixed latency markers.

## Coordination and evidence

The commander receives a ticket, a current customer symptom plus any instance
availability alert, and an opaque snapshot ID. It never receives a preset key or
raw fault controls. For availability alerts and gateway saturation it selects
application and capacity specialists, which may run independently in parallel.

The application specialist checks code and logs. The capacity specialist measures
demand, instance availability and online capacity. It distinguishes successful
customer requests with lost redundancy from capacity-driven customer failures.
Its Java probe offers `START_CHECKOUT_A` or `START_CHECKOUT_B` only when that
specific instance is stopped. Recommendations still require matching recorded
receipt IDs; the final evidence read retains exact identities across skill summaries.

At 240 requests/s with both instances online, the probe offers no repair. Scaling
beyond two instances and reducing traffic are outside the repair catalog. The
model should explain the shortfall and escalation. Changing demand in presenter
controls remains possible, but must not be represented as a model-executed repair.

## Recovery and stale proposals

Verification uses current state and requires both:

1. Every request in the current customer batch succeeds.
2. The configured checkout instance availability target is met (two online).

If only the customer check passes, verification reports that specifically and
leaves the incident open. This is intentional: a redundancy incident cannot be
resolved merely because the remaining instance happens to handle the current load.
The instance target is not a guarantee against all possible shared failure modes.

Changes to incoming demand increment the environment revision just like component
switches. Earlier probes retain their saved demand and instance state, and their
repair proposals cannot be applied to a newer revision. A repair preserves demand
and every untargeted component. A successful repair still requires verification.

## Database and API compatibility

Flyway V2 adds `checkout_b_running` and `demand_rps`, commissions B online, and
defaults demand to 60. It preserves existing fault conditions and incident tables,
increments the world revision, and records the upgrade as an environment activity.
Old proposals therefore become stale when the topology changes.

Old snapshot JSON has no B field. The Java contract interprets an absent/null B
as the historical single-instance topology and missing demand as 60. It does not
rewrite old snapshots, reports, receipts or execution events. New snapshots always
have an explicit B state and demand. The old `checkout` control now names A;
`checkoutB` controls B. The old `START_CHECKOUT` repair identifier is retained only
for historical compatibility; new model recommendations use explicit A/B IDs.

`POST /api/environment/traffic` takes `{demandRps, expectedRevision}`.
`GET /api/state` now includes `capacity` measurements and status.
`POST /api/incidents/{id}/verify` returns `success`, `customerHealthy`,
`redundancyRestored`, `message`, and `revision`. Verification no longer presents
a passing customer transaction as proof of restored instance availability.

## Verification

Deterministic tests enumerate 128 combinations (32 independent fault combinations
at four demand levels), exercise the 100 requests/s boundary, preserve demand on
repair, reject stale proposals, prevent resolution with lost redundancy, cover
full-pool overload and migrate a populated V1 database without rewriting snapshots.
Live tests cover risk → overload → restart → recovery and a full pool with no
supported repair, alongside the original compound deployment/network scenario.
