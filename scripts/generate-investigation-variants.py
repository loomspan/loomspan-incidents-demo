"""Derive the load planner with framework-enforced specialist task coverage."""
from pathlib import Path
root = Path(__file__).resolve().parents[1] / 'src/main/resources/skills'
source = (root / 'investigateIncident.yml').read_text(encoding='utf-8')
source = source.replace('name: investigateIncident\n', 'name: investigateIncidentLoad\n', 1)
for name in ['investigateApplication', 'investigateCapacity', 'investigateDatabase']:
    source = source.replace(f'  - name: {name}\n    max_tasks: 1', f'  - name: {name}\n    min_tasks: 1\n    max_tasks: 1')
(root / 'investigateIncidentLoad.yml').write_text(source, encoding='utf-8', newline='\n')
