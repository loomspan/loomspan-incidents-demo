# Cache degradation and cascading database load

The presenter controls cache power and configured hit rate independently. Two presets,
**Cache outage** and **Cache degradation**, run at 150 checkout requests/s with both
checkout instances online. The database process stays ready while its workload saturates.

## Repeatable workload rules

There is no random sampling, cache warming clock, real server load or queue simulation.
Each check models a one-second aggregate:

1. Pool admission is min(incoming demand, online instances × 100).
2. A broken deployment, blocked DB path or stopped DB prevents this modeled workload;
   offered operations are zero and customer checks fail for that upstream fault.
3. Effective hit percentage is the configured value (0–100) when cache is online, otherwise zero.
4. Hits = floor(admitted requests × effective hit percentage / 100).
5. Offered DB operations = admitted requests × 2 − cache hits. Every checkout needs
   one write; each cache miss adds a read. A cache hit never bypasses the required write.
6. The database supports 200 operations/s. When demand exceeds that, completed
   requests = floor(admitted requests × 200 / offered operations). This proportional
   budget is an aggregate approximation, not individual request scheduling.
7. Failed requests = incoming minus completed, including gateway rejections and DB failures.

| Scenario at 150 requests/s | DB operations/s | Successful requests/s | Failed requests/s |
| --- | ---: | ---: | ---: |
| Cache online, 90% hits | 165 | 150 | 0 |
| Cache online, 20% hits | 270 | 111 | 39 |
| Cache offline | 300 | 100 | 50 |

Database saturation produces a 3,000 ms timeout band. Otherwise successful batches
use 600 ms above 80% of either pool or DB capacity, and 120 ms below those thresholds.
Existing deployment, connectivity and zero-instance failure bands take priority.
At 240 incoming requests/s with a healthy 90% cache, 200 are admitted, 220 DB ops/s
are offered, and 181 complete: 40 gateway rejections plus 19 DB failures.

## Coordination and repairs

For database saturation, the commander selects application, capacity and database
specialists. Their independent probes read the same immutable snapshot. Application
logs establish the dependency symptom; capacity distinguishes pool limits; database
observations expose cache misses and workload despite passing local readiness.
The authoritative evidence reader runs after the selected specialists finish.

Database receipts can offer `START_CACHE` for a stopped cache or
`RESTORE_CACHE_HIT_RATE` below the healthy 90% baseline. Starting preserves the
configured hit rate; restoring hit rate preserves cache power. Both preserve traffic,
checkout instances, DB power, network and deployment. One repair is applied per
assessment, then recovery is verified and remaining faults can be reinvestigated.
Starting a checkout instance does not increase DB capacity. No arbitrary scale-out
or traffic reduction action is exposed.

Recovery still requires all customer requests to succeed and both checkout instances
online. Cache health itself is not an availability target: at low demand, checkout
can remain healthy with the cache stopped. This demo does not require every control
to be in its default position before resolving an incident.

## API and persistence

`POST /api/environment/cache` accepts `running`, integer `hitPercent` (0–100), and
`expectedRevision`. Missing/invalid values return 400; stale revisions return 409.
`GET /api/state` includes `dataLoad` with admitted requests, hits, effective hit rate,
offered DB operations, fixed DB capacity and saturation status.

V3 adds cache power and hit rate, defaults to online/90%, increments the world revision,
and records an upgrade activity. New receipts use short database-sequence handles
(such as `evidence-12`) to reduce copying errors in nested model reports. Existing
receipt IDs remain unchanged, and run/action validation is unchanged. Other controls and incident records are preserved.
Snapshots with missing/null `cacheRunning` retain the previous simulation behavior;
they are never rewritten to imply a cache existed in the earlier run.

## Walkthrough

Choose **Cache degradation**, open an incident and investigate. Review the three
specialists' receipts and apply **Restore cache hit rate**. DB demand drops from
270 to 165 operations/s while incoming traffic remains 150. Verify recovery.
For a second case, choose **Cache outage** and follow the same flow with **Start cache**.
For two dependent repairs, stop the cache and set its hit rate to 20%: restarting alone
still leaves an overloaded DB, requiring verification and a fresh investigation.
