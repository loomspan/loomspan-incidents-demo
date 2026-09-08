# Relay design and next slices

## Current scope

Relay demonstrates a full investigate → repair → verify → adapt loop for a
fictional storefront. The database is real; servers, network traffic, logs and
transactions are simulated. A single Java dependency model gives all probes
consistent observations without launching containers or touching real hosts.

The current slice adds two checkout instances, variable traffic, six presets,
persistent incidents, asynchronous model investigation, four focused specialists,
saved evidence, supported repair proposals, verified resolution and historical runs.
See [capacity rules](capacity-slice.md) for the new behavior and migration contract.

## Ownership

- `Simulation`: pure component behavior and action effects.
- `IncidentStore`: transactions, environment revisions, snapshots, receipts,
  report validation, action idempotency and lifecycle transitions.
- `ProbeSkills`: four annotation-defined Java probe leaves plus a read of the
  authoritative evidence record after the selected specialists finish. The final
  read avoids reconstructing action/receipt identifiers from nested summaries.
- `InvestigationService`: bounded job scheduling and the supported `SkillTemplate`
  facade; persists only selected start/finish event fields, not raw diagnostic payloads.
- YAML skills: investigation selection, interpretation, synthesis, output and
  direct-child evidence contracts. Specialists use direct tool execution; only
  the commander needs an explicit plan.
- React: presenter controls and operator review. Customer checks and the
  environment map reflect current state; evidence always identifies its revision.

## State transitions

An incident opens as `OPEN`. Starting a run moves it to `INVESTIGATING` and saves
a world snapshot. A valid report moves it to `DIAGNOSED`; failure returns it to
`OPEN`. A repair moves it to `MITIGATED`, not resolved. Verification checks both
customer success and the configured instance availability target; it either moves
the incident to `RESOLVED` or back to `OPEN`. Verification also supports manual recovery:
the presenter may fix controls and verify without asking a model to approve reality.

World mutations, snapshots and verification serialize on the singleton environment
row. Repairs also lock the incident and compare the snapshot revision with the
current world. Repair retries use `(run_id, action_id)` to avoid applying twice.
Evidence writes lock their run and require `RUNNING`; completion/failure closes
that write window. Receipts expose no mutable fault-state fields.

The model's report must cite recorded evidence from its own run. Each action is
offered by a probe and copied into a model recommendation with a matching receipt.
The UI displays the original deterministic action label and effect. Repair
execution checks the saved recommendation, not an arbitrary browser-supplied action.

## Deliberate limits

- One storefront, two checkout instances and one database; no tenants, real hosts or external
  monitoring integration.
- No random failure generation, real clock progression, cache, configurable gateway faults,
  DNS fault switch, or automatic background alert detection yet.
- Investigations observe immutable snapshots. They do not automatically interrupt
  and replan when the presenter changes controls during an active model call.
- Traffic is a deterministic one-second aggregate with fixed per-instance capacity
  and latency bands, not a queueing engine or historical metric generator.
  Deployment history is simulated and shared by the checkout pool.
- Recovery checks are deterministic application operations; the model proposes
  repairs but never executes them automatically.
- Receipt IDs and allowed actions are validated; model explanations remain
  hypotheses. Confidence is model-reported, not a calibrated probability.
- The embedded workflow displays successful public execution observations after
  completion. It does not impersonate Console's live plan/trace facilities.

## Candidate follow-on slices

1. Cache degradation with database load: trace a cascading performance problem.
2. Additional path-specific failures such as DNS and region-specific connectivity.
3. Role-constrained remediation and an explicitly enabled automatic repair policy.
4. Controlled snapshot refresh during investigation, if the framework's supported
   mission semantics make that useful without hidden state propagation.
