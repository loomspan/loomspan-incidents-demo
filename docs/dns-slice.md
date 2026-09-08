# DNS and layered connectivity failures

Presenter can remove the `orders-db.internal` DNS record independently of
database power and firewall rule DB-17. Two presets and guided walkthroughs are
available: **Missing database DNS** (`dns`) and **DNS + blocked connection**
(`dns-compound`).

When DNS fails, checkout reports a database connection failure. The application
probe records `UnknownHostException`; the database's local readiness check can
still pass. The network probe records NXDOMAIN and offers only `RESTORE_DB_DNS`.
Its hostname-based TCP test cannot run, so it makes no claim about firewall
reachability. This is the defined probe scope, rather than a claim that real
operators cannot inspect firewall configuration independently.

DNS restoration changes only the DNS record. It needs Commander authorization,
just like firewall repair. Responder can investigate and retain the proposal,
but neither manual repair nor an automatic allowlist bypasses that role check.

## Walkthrough

1. Sign in as Presenter and prepare **DNS recovery reveals a blocked path**.
2. Switch to Responder, investigate, and inspect the application, network and
   database receipts. The supported repair is DNS restoration.
3. Switch to Commander, apply it, and verify. DNS now works, but checkout times out.
4. Reinvestigate. The new network receipt now observes the firewall denial and
   supports **Restore database connection**.
5. Apply that repair and verify successful recovery. Compare the two snapshots
   and the failed verification between them.

Commander may alternatively use Auto-repair, selecting both DNS and database
connection restoration with a two-repair limit. The application still applies
one repair, verifies, and obtains fresh evidence before the second repair.
The original DNS receipt cannot authorize firewall repair.

## Deterministic rules and compatibility

- DNS failure yields zero successful checkout requests and zero offered database
  operations. No online instances and a bad deployment take precedence in the
  customer symptom; DNS repair does not fix those independent faults.
- There is no DNS cache, TTL propagation delay, real resolver or network traffic.
  A restored record becomes usable at the next revision.
- `POST /api/environment/control` accepts `control: "dns"`, `enabled`, and
  `expectedRevision`, under Presenter authorization. `World.dnsHealthy` appears
  in live state and new investigation snapshots.
- Other controls and repairs preserve DNS state. Applying any preset creates
  that preset's complete environment, including healthy DNS for older presets.
- V7 adds an initially healthy DNS column without changing the current revision
  or rewriting history. Missing DNS in historical JSON retains the original
  successful-resolution behavior.

Tests cover DNS/firewall combinations, probe scope, local database health,
preservation across unrelated changes, revision fencing, role denial, and
two-stage automatic recovery. The live test exercises both real model
investigations and their distinct supported repair proposals.
