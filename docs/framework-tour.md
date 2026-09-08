# A focused Loomspan tour

Use three walkthroughs to explain the framework. All other scenarios remain
available under **More scenarios** and through the simulator presets.

| Walkthrough | Loomspan capability | Relay's application responsibility |
| --- | --- | --- |
| 1. One fault hides another | Selective planning and structured specialist results | Apply repairs, verify recovery and start a fresh investigation |
| 2. Two-stage cache recovery | Required specialist tasks, optional concurrent groups, dependent evidence read | Snapshot probes, citation validation, bounded automatic repair loop |
| 3. Healthy services, broken path | Authenticated caller propagation and Java/YAML skill authorization | Sign-in, commander-only repair policy and operator audit history |

The framework evidence contract requires successful supporting skill execution.
Relay separately validates that cited receipt IDs belong to this investigation
and that the exact proposed action is offered by its cited receipt. Neither
mechanism proves that every model-written explanation is correct.

## 1. Show selective investigation

Prepare **One fault hides another** as Presenter. Switch to Commander and
investigate in Recommend mode. Inspect the application evidence and rollback
proposal. Apply it, then verify: a database connection failure remains.

Reinvestigate. Compare the current **Coordination** record with **History →
Previous investigations**, which now includes each prior coordination record.
The first investigation should focus on application failure; the second should
include network and database evidence. Apply the network repair and verify.

**What to say:** the planner selects specialists for each current symptom.
Relay requests a new root invocation after verification fails; this is not an
in-place replan of the earlier framework session.

**Acceptance:** both validated reports complete, the first supports rollback,
the second supports network restoration, and recovery passes after both repairs.
The live tour test also checks that the first investigation did not call the
network probe. If a future run takes a broader plan, show the actual result
rather than claiming selectivity that it did not demonstrate.

## 2. Show coordination and evidence

Prepare **Two-stage cache recovery** as Presenter. Switch to Responder, choose
Auto-repair, select **Start cache service** and **Restore cache hit rate**, and
set a limit of two. Review the saved operation policy after starting.

In Coordination, inspect the application, capacity and database specialists.
The load planner requires these tasks. Independent specialists may run in a
parallel group; the evidence reader depends on their completion. Verify actual
overlap between sibling specialists, not between a parent and its child.

**What to say:** Loomspan executes the generated plan and validates structured
skill output. Relay applies one supported repair, measures recovery, and starts
another investigation when needed. The repair loop is application code.

**Acceptance:** two repairs, two investigation snapshots, a failed verification
between repairs, and final recovery. Parallel execution is a supported capability,
not a promise that every model-generated plan groups tasks or produces overlap.
Use Console plan evidence when making that claim about a particular run.

## 3. Show the authorization boundary

Prepare **Healthy services, broken path** as Presenter. Switch to Responder and
investigate. The supported network repair is visible but unavailable to that
operator. Switch to Commander, review the same evidence, apply it, and verify.
The timeline records different investigators and repair authorizers.

Alternatively, Responder can allowlist the network repair in Auto-repair mode;
the operation still stops with `ROLE_BLOCKED` and preserves its report.

**What to say:** Loomspan carries the caller through nested skills and enforces
`rbac_roles` and Java `@RolesAllowed`. Relay enforces the repair restriction.
The handoff alone does not prove framework denial: `OperatorSecurityTest`
separately invokes both Java and YAML skills through the public facade as Viewer
and checks access denial. Responder can invoke the Java probe successfully.

**Acceptance:** a validated Responder diagnosis, no unauthorized repair, a
Commander repair, successful recovery, and both identities in the activity record.

## Inspect in Console

The Coordination tab has a copyable session ID and inspection instructions.
Previous investigations have their own records. A report correction, when used,
has a separate session; it is not an extra step in the original root invocation.

Before presenting with Console:

1. Set `RELAY_OBSERVABILITY_ENABLED=true` and set `RELAY_OBSERVABILITY_API_KEY`
   to an operator-chosen key of 32–512 characters before starting Relay. Keep
   `RELAY_TRACE_PERSISTENCE=ALWAYS` to retain traces. These settings are already
   supported; the tour does not enable them or create a credential automatically.
2. Start your matching Loomspan Console executable with
   `--target-address http://127.0.0.1:8083`. This only prefills the target screen.
   Follow its browser pairing flow, supply the application's observability key,
   and select **Connect**. Relay's `relay-demo` password is unrelated to this key.
3. Find the execution or retained trace corresponding to the copied session ID.
   Obtain the actual trace ID from Console; do not construct it from the session.
4. Inspect the plan, specialist frames, dependencies, evidence reader, final output,
   and any retries. Distinguish recovered retries from terminal failures.

For a configured Console MCP client, use runtime discovery, then trace discovery
by session and authoritative plan/frame/record queries. The runtime skill documents
`LOOMSPAN_query_trace_plans` for grouping and observed overlap. Missing Console
tools or trace data do not imply the application investigation failed.

The Relay panel is an inspection aid, not a Console connection check. This slice
was verified with public execution observations and real model calls; no Console
MCP tools were available in the implementation session, so end-to-end Console
retrieval was not verified. Check that connection before presenting it live.

## Reliability checks

The three tour cases are opt-in tests in `LiveInvestigationTest`:

- `compoundIncidentRepairsAndReinvestigatesWithRealNestedSkills`
- `automaticTwoStageCacheRecoveryUsesSavedPolicyAndFreshEvidence`
- `frameworkTourResponderHandoffRetainsValidatedEvidence`

Run with `-Drelay.live=true`; tests use a separate in-memory database. Repeated
passing runs are a smoke check, not a measured reliability rate. Model prose,
task grouping and correction use can vary. Corrections are a useful secondary
feature when actually observed, rather than a mandatory part of the tour.
