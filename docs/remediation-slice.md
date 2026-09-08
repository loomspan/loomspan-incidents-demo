# Operating modes and bounded automatic remediation

Relay now offers a per-operation policy alongside the incident investigation.
**Recommend** remains the default. Starting an operation saves its mode, exact
repair permissions and limit; changing the form never changes an active policy.

| Mode | Behavior |
| --- | --- |
| Observe | Collect evidence and explain. Persist no repair recommendations; the repair API refuses this run as authorization. |
| Recommend | Produce a validated report, then wait for an explicit operator repair. |
| Auto-repair | Choose a permitted supported repair, apply it, verify recovery, and investigate a fresh snapshot if recovery fails. |

The automatic policy starts with cache service start and hit-rate restoration
selected in the UI, a two-repair limit, and no other selected repairs. The server
accepts only the seven known repair IDs and limits from one to three. An empty
permission list grants no automatic repairs. Permissions are application rules
for this local simulator. They are evaluated alongside the authenticated operator's
role: both must authorize a repair. See [operator roles](operator-roles.md) for
account permissions, framework skill access and commander handoff.

## Signature walkthrough

1. Choose **Two-stage cache recovery**, then open an incident.
2. Select **Auto-repair**. Keep the two cache repairs selected and the limit at two.
3. Start the investigation. The cache is offline with a configured 20% hit rate.
4. The first supported start brings the cache online; its hit rate remains 20%.
   Verification still fails because database demand remains 270 operations/s.
5. A new investigation reads the updated snapshot and can propose restoring the
   hit rate. Applying that repair lowers database demand to 165 operations/s.
6. All 150 checkout requests/s succeed and both instances are online. Verification
   resolves the incident. History records policy, proposals, repairs, verification
   and reassessment; previous investigations retain their own evidence.

Try a limit of one to stop after the first repair, or remove permission to restore
hit rate to require operator review. **Stop operation** fences further repairs
and evidence writes; an already-running model request may finish physically, but
cannot authorize more work after the stop commits.

## One report correction attempt

For a database saturation symptom, the application selects `investigateIncidentLoad`,
a variant of the commander with framework-enforced minimum tasks for application,
capacity and database specialists. Other symptoms retain selective planning through
`investigateIncident`. `scripts/generate-investigation-variants.py` derives the load
manifest from the shared commander instructions. This makes required evidence
coverage explicit while preserving planning and concurrency within Loomspan.

The application first parses the commander's report and checks its existing
receipt/action contract, including a useful recommendation when probes offer repairs. If that returned report is invalid, it invokes the
separate `correctIncidentReport` skill once with the candidate, validation error
and authoritative saved receipts. That skill has no tools and no output-schema
semantic retries. It cannot gather new evidence or execute repairs. The returned
report must pass the same application validation. A second invalid report or a
correction invocation failure fails the investigation and stops automatic work.

The report correction is separate from framework/provider retries during the
initial investigation. If the initial invocation fails without returning a
candidate report, the application fails the run rather than inventing a candidate.
A correction records its status, reason, separate Loomspan session ID and public
start/finish events when available. The UI shows those separately from the
original investigation session. This does not guarantee model prose is correct.

## Atomic transitions and stop conditions

Policy checks, world revision comparison, one repair, recovery verification and
the next investigation snapshot share a database transaction under the world and
incident locks. The application selects a permitted recommendation in the saved
permission order. The UI orders starts before restoration actions. The model
cannot expand permissions or choose an arbitrary repair endpoint.

The loop stops on success, unsupported/disallowed recommendations, a changed world
revision, exhausted repair budget, failed investigation, explicit stop, or app
restart. It never resumes automatically after a restart. Manual investigation,
repair and verification on that incident are blocked while an operation is active.
Presenter controls remain available; changing them stops automatic work at its
next decision because the saved environment revision no longer matches.

Each investigation is a new Loomspan root session with existing mission limits.
At most three repairs and three investigations occur per operation, with at most
one application correction invocation per investigation. The executor still admits
two running operations and six queued operations. A loop is an application-owned
sequence of framework investigations, not a single self-modifying framework plan.

## API and migration

`POST /api/incidents/{id}/investigate` optionally accepts:

```json
{"mode":"AUTO","allowedActions":["START_CACHE","RESTORE_CACHE_HIT_RATE"],"maxRepairs":2}
```

An absent body or mode retains Recommend behavior. `OBSERVE` and `RECOMMEND`
grant no automatic permissions. `POST /api/incidents/{id}/operations/{operationId}/stop`
stops only an operation belonging to that incident. Incident detail includes saved
operations; each run includes its mode and optional correction record.

V4 adds operation records and nullable run metadata. Existing worlds, snapshots,
reports, receipts and histories are not rewritten. Earlier runs retain Recommend
semantics and have no correction record. Existing investigations interrupted by
restart retain their evidence and are marked failed as before.
