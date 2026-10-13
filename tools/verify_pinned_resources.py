#!/usr/bin/env python3
"""Verify immutable pinned legal/default-string bytes through every packaging layer."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile


def packaged_bytes(artifact):
    if artifact.is_dir():
        jars = list(artifact.rglob('MeloX-Desktop-*.jar'))
        assert len(jars) == 1, jars
        return jars[0].read_bytes()
    if artifact.suffix.lower() == '.jar':
        return artifact.read_bytes()
    with zipfile.ZipFile(artifact) as archive:
        jars = [name for name in archive.namelist() if name.startswith('app/') and name.endswith('.jar')]
        assert len(jars) == 1, jars
        return archive.read(jars[0])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--sources', action='store_true')
    parser.add_argument('--artifact', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    assert args.sources or args.artifact
    repo = Path(__file__).resolve().parents[1]
    # This committed manifest is from the pure pinned export audit, not from the
    # platform checkout being inspected. Intentional string additions require a
    # new pure-source audit/manifest; platform line-ending conversion cannot pass.
    baseline = json.loads((repo / 'parity/3f877ea/evidence/search-player-validation.json').read_text())
    expected = dict(baseline['packaged_legal_sha256'])
    strings = 'parity/pinned-settings-strings.properties'
    expected[strings] = baseline['source_sha256']['desktop/src/main/resources/' + strings]
    font = json.loads((repo / 'parity/3f877ea/evidence/lyrics-font-source-audit.json').read_text())
    assert font['pinned_android'] == baseline['pinned_android'] and font['verified_against_pure_git_blob']
    expected[font['resource']] = font['sha256']
    records = []
    archive = zipfile.ZipFile(io.BytesIO(packaged_bytes(args.artifact))) if args.artifact else None
    try:
        for name, digest in sorted(expected.items()):
            data = (repo / 'desktop/src/main/resources' / name).read_bytes() if args.sources else archive.read(name)
            actual = hashlib.sha256(data).hexdigest()
            records.append({'resource': name, 'bytes': len(data), 'sha256': actual,
                            'expected_sha256': digest, 'crlf_count': data.count(b'\r\n'), 'verified': actual == digest})
        result = {'check': 'pinned legal/default-string exact resource bytes',
                  'pinned_android': baseline['pinned_android'],
                  'artifact': str(args.artifact) if args.artifact else 'source resources',
                  'status': 'PASSED' if all(record['verified'] for record in records) else 'FAILED',
                  'resources': records}
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2) + '\n')
        assert result['status'] == 'PASSED', 'Pinned resource bytes changed: ' + ', '.join(record['resource'] for record in records if not record['verified'])
        print(f'PASS: {len(expected)} pinned resources match exact source bytes, including Android MiLan Pro')
    finally:
        if archive is not None:
            archive.close()


if __name__ == '__main__':
    main()
