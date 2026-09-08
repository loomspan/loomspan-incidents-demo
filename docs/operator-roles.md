# Operator roles

Relay uses Spring Security cookie sessions and CSRF protection. Four seeded local
demo accounts share the password `relay-demo`:

| Account | Permissions |
| --- | --- |
| `viewer` | Read incidents, evidence, measurements and history; run customer checks |
| `presenter` | Viewer access, environment controls and incident preparation |
| `responder` | Viewer access, create incidents, investigate, stop operations, verify recovery and apply routine repairs |
| `commander` | Responder capabilities plus deployment rollback and database network restoration |

Presenter is a separate role. Neither responders nor commanders can change faults
through the environment API. These are local demonstration accounts, not an
identity-provider integration; the application continues to bind to loopback.

## Demonstrate the handoff

1. Sign in as Presenter. Prepare **Healthy services, broken path**.
2. Switch to Responder and select the new incident. Choose Auto-repair and include
   **Restore database connection** in the operation's permitted repairs.
3. Start the investigation. Specialists can diagnose the network fault and retain
   their complete evidence. Automatic work stops with **Incident commander
   authorization required**, without applying the network change.
4. Switch to Incident commander and select that incident. Review the evidence,
   apply the proposed network repair, and verify recovery.
5. The timeline identifies Presenter opening the incident, Responder investigating,
   and Commander applying and verifying the repair.

For routine two-stage cache recovery, Responder can authorize both cache repairs.
Signing out or switching accounts does not cancel a running operation or change its
identity. Use **Stop operation** to stop further work. Restarting the application
still stops unfinished operations rather than resuming them under a new identity.

## Enforcement

- HTTP routes enforce authentication and operator roles. Mutating requests require
  a session CSRF token; `GET /api/csrf` supplies it. Login and logout are POSTs to
  `/api/login` (form encoded) and `/api/logout`.
- All investigation YAML skills declare `rbac_roles: [RESPONDER, COMMANDER]`.
  Java probes use JSR-250 `@RolesAllowed`, with method security enabled. Loomspan
  enforces those policies at its facade and Java invocation boundaries.
- The application carries the initiating Spring authentication across its executor
  handoff before invoking Loomspan. Nested framework workers inherit that caller.
  No model input selects identity or roles.
- Repair execution also checks the operator's role inside the shared repair path,
  covering manual and automatic repairs. A report may propose a commander-only
  action to a responder; a proposal is not execution authority.
- Both the operation allowlist and the operator's permissions must allow an
  automatic repair. Evidence membership, latest-run checks, environment revision,
  repair limits, and verification remain mandatory. `ROLE_BLOCKED` preserves the
  completed report for a commander's review; it does not silently escalate.
- V6 adds nullable activity actors. Historical events retain unknown actors;
  new events record the authenticated caller. Startup housekeeping has no caller
  and is not attributed to a human. Account passwords and session tokens are not
  stored in incident history.

The framework controls investigation-skill access. The application controls repair
authorization because repairs are deterministic application operations, not
model-invoked tools. This slice does not add SSO, account administration or a
separate approval-ticket system.
