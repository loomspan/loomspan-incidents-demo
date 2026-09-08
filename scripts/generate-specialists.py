"""Generate focused specialist manifests with identical receipt contracts."""
from pathlib import Path

root = Path(__file__).resolve().parents[1] / 'src/main/resources/skills'
for domain, probe, mission in [
    ('Application', 'inspectApplication', 'Interpret checkout health, logs and deployment history. Distinguish a process outage, code regression and dependency symptom.'),
    ('Network', 'inspectNetwork', 'Interpret DNS, firewall and connectivity evidence. NXDOMAIN prevents hostname-based TCP testing; do not infer firewall health or propose its repair until fresh evidence after DNS restoration supports it. Distinguish blocked traffic from an allowed path to an unavailable dependency.'),
    ('Database', 'inspectDatabase', 'Interpret local database health, cache state, hit rate and offered database operations against capacity. Distinguish a stopped database from a live but saturated database caused by cache misses. A healthy local check does not prove application reachability. Recommend only exact repairs from the receipt; a cache start preserves the configured hit rate.'),
    ('Capacity', 'inspectCapacity', 'Interpret online instances, demand and pool capacity. Distinguish lost redundancy with successful checkout from saturation or total loss of capacity. Propose only the stopped-instance starts offered by the probe. Never treat lowering simulated traffic as a repair, and never invent extra instances.'),
]:
    (root / f'investigate{domain}.yml').write_text(f'''name: investigate{domain}
description: {mission}
rbac_roles: [RESPONDER, COMMANDER]
model: investigator
allowed_skills:
  - name: {probe}
prompt: |
  {mission}
  Call {probe} with the exact runId. Never invent a receipt or observation.
  Return a concise summary and copy every field of the returned receipt into receipts.
  Preserve the receipt ID and supported action IDs exactly, including healthy observations
  with empty actions. Do not treat a suggested repair as executed or claim recovery.
input_schema:
  type: object
  properties:
    runId: {{ type: string }}
    context: {{ type: string }}
  required: [runId, context]
  additionalProperties: false
output_schema:
  type: object
  properties:
    summary:
      type: string
      evidence: {probe}
    receipts:
      type: array
      evidence: {probe}
      items:
        type: object
        properties:
          id: {{ type: string }}
          runId: {{ type: string }}
          revision: {{ type: integer }}
          observedAt: {{ type: string }}
          probe: {{ type: string }}
          observation: {{ type: string }}
          actions:
            type: array
            items:
              type: object
              properties:
                id: {{ type: string }}
                label: {{ type: string }}
                effect: {{ type: string }}
              required: [id, label, effect]
              additionalProperties: false
        required: [id, runId, revision, observedAt, probe, observation, actions]
        additionalProperties: false
  required: [summary, receipts]
  additionalProperties: false
output_schema_max_retries: 2
''', encoding='utf-8', newline='\n')
