# Guided scenarios and the recovery story

The guided-demo library explains four scenarios: two-stage cache recovery, one
fault hiding another, lost redundancy, and a blocked network path. **Prepare
walkthrough** applies its preset and opens an incident using the actual customer
symptom. It selects suitable operating defaults but does not start model calls.
The presenter reviews the mode and starts the investigation explicitly.

The guide describes expected behavior. Actual progress is shown in **Timeline**,
which is now the default record tab. Its steps come from saved activity rows,
ordered by record ID. New activity records link directly to their investigation;
expanded steps show that run's actual report and receipts. An investigation's
receipts can arrive after its start event, so the view labels this relationship.
Older records use a run link only when the investigation revision is unambiguous.
Evidence, Coordination and the full History remain available.

**What changed?** compares incident-opening measurements with the latest saved
verification. Before the first verification, the right column is explicitly live.
Once verified, it stays fixed even when presenter controls change. Current-world
revision and checkout outcome are called out separately when they differ.
The table shows successful and failed checkout, DB demand, instance availability
and latency. A passing customer check alone is not marked as recovered unless
instance availability also meets its target.

V5 adds nullable measurements and investigation links to activity rows. New
opening, repair and verification activities save measurements in the same database
transaction as the event. Older rows remain unchanged: no recovery measurements
are manufactured. For older incident baselines, a run's saved world can supply
measurements under its historical simulation rules. Missing old verification
metrics are displayed as **Not recorded**.

Operation messages now distinguish permission limits, exhausted repair budgets,
stale evidence, missing supported repairs, investigation failures and stopped work,
with an appropriate next action for the presenter. This is explanatory UI; existing
repair authorization, correction limits and automatic-loop behavior are unchanged.
