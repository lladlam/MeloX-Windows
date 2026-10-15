#!/usr/bin/env python3
"""Record a Windows CI run as auditable evidence from its downloaded artifacts.

Reads the TEST-*.xml reports inside the downloaded Verification artifact (so the
test totals come from the runner, not from a claim), pulls run/artifact metadata
through the GitHub CLI, and writes run.json + windows-tests.json.

Usage:
  record_ci_evidence.py --run-id ID --sha SHA --directory DOWNLOADED_ARTIFACTS --output-dir EVIDENCE_DIR
"""
import argparse
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as E

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--run-id', required=True)
parser.add_argument('--sha', required=True)
parser.add_argument('--directory', type=Path, required=True)
parser.add_argument('--output-dir', type=Path, required=True)
args = parser.parse_args()

xmls = sorted(args.directory.rglob('TEST-*.xml'))
assert xmls, f'no TEST-*.xml reports under {args.directory}'
totals = {'suites': len(xmls), 'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}
per_suite = {}
for path in xmls:
    root = E.parse(path).getroot()
    entry = {key: int(root.get(key, '0')) for key in ('tests', 'failures', 'errors', 'skipped')}
    per_suite[root.get('name', path.stem)] = entry
    for key in entry:
        totals[key] += entry[key]

run = json.loads(subprocess.check_output(
    ['gh', 'api', f'repos/lladlam/MeloX-Windows/actions/runs/{args.run_id}'], text=True))
artifacts = json.loads(subprocess.check_output(
    ['gh', 'api', f'repos/lladlam/MeloX-Windows/actions/runs/{args.run_id}/artifacts'], text=True))['artifacts']

tests_report = {
    'run_id': args.run_id, 'head_sha': args.sha,
    'source': 'TEST-*.xml reports from the downloaded Verification-Windows artifact',
    'totals': totals,
    'zero_failures': totals['failures'] == 0 and totals['errors'] == 0 and totals['skipped'] == 0,
    'suites': per_suite,
}
args.output_dir.mkdir(parents=True, exist_ok=True)
(args.output_dir / 'windows-tests.json').write_text(json.dumps(tests_report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')

report = {
    'run_id': args.run_id,
    'url': f'https://github.com/lladlam/MeloX-Windows/actions/runs/{args.run_id}',
    'head_sha': args.sha,
    'runner': 'windows-latest',
    'status': run.get('status'),
    'conclusion': run.get('conclusion'),
    'started_at': run.get('run_started_at'),
    'completed_at': run.get('updated_at'),
    'artifacts': [{'id': item['id'], 'name': item['name'], 'size_in_bytes': item['size_in_bytes']}
                  for item in artifacts],
    'windows_tests': totals,
}
(args.output_dir / 'run.json').write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
print(json.dumps({'run_id': args.run_id, 'conclusion': report['conclusion'], 'windows_tests': totals,
                  'zero_failures': tests_report['zero_failures']}, indent=2))
