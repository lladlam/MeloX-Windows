#!/usr/bin/env python3
"""Verify immutable pinned legal/default-string bytes through every packaging layer."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
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
    parser.add_argument('--source-revision', help='Read immutable expected manifests from this delivery source commit')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    assert args.sources or args.artifact
    repo = Path(__file__).resolve().parents[1]
    def manifest(path):
        raw = subprocess.check_output(['git', 'show', args.source_revision + ':' + path], cwd=repo) if args.source_revision else (repo / path).read_bytes()
        return json.loads(raw.decode('utf-8'))
    # This committed manifest is from the pure pinned export audit, not from the
    # platform checkout being inspected. Intentional string additions require a
    # new pure-source audit/manifest; platform line-ending conversion cannot pass.
    baseline = manifest('parity/3f877ea/evidence/search-player-validation.json')
    expected = dict(baseline['packaged_legal_sha256'])
    strings = 'parity/pinned-settings-strings.properties'
    expected[strings] = baseline['source_sha256']['desktop/src/main/resources/' + strings]
    font = manifest('parity/3f877ea/evidence/lyrics-font-source-audit.json')
    assert font['pinned_android'] == baseline['pinned_android'] and font['verified_against_pure_git_blob']
    expected[font['resource']] = font['sha256']
    lyrics = manifest('parity/3f877ea/evidence/lyrics-settings-source-audit.json')
    assert lyrics['pinned_android'] == baseline['pinned_android'] and lyrics['verified_against_pure_export']
    expected[lyrics['resource']] = lyrics['sha256']
    skyline = manifest('parity/3f877ea/evidence/skyline-settings-source-audit.json')
    assert skyline['pinned_android'] == baseline['pinned_android'] and skyline['verified_against_pure_export']
    expected[skyline['resource']] = skyline['sha256']
    # UTF-8 manifest: the row symbols are non-ASCII and cp1252 cannot decode them on Windows runners.
    pages = manifest('parity/3f877ea/evidence/settings-pages-source-audit.json')
    assert pages['pinned_android'] == baseline['pinned_android'] and pages['verified_against_pure_export']
    expected.update(pages['resource_sha256'])
    recognition = manifest('parity/3f877ea/evidence/recognition-source-audit.json')
    assert recognition['pinned_android'] == baseline['pinned_android'] and recognition['verified_against_pure_export']
    expected.update({name: value['sha256'] for name, value in recognition['resources'].items()})
    language_path = 'parity/53244b9/evidence/lyric-language/strings-source-audit.json'
    has_language = not args.source_revision or bool(subprocess.check_output(
        ['git', 'ls-tree', '--name-only', args.source_revision, '--', language_path], cwd=repo).strip())
    additional_baselines = []
    if has_language:
        language = manifest(language_path)
        assert language['pinned_android'] == '53244b9d6b21d7e3a609e8cb11876efef70f218b' and language['verified_against_pure_export']
        expected.update(language['resource_sha256'])
        additional_baselines.append(language['pinned_android'])
    split_path = 'parity/53244b9/evidence/split-layout/resources-source-audit.json'
    has_split = not args.source_revision or bool(subprocess.check_output(
        ['git', 'ls-tree', '--name-only', args.source_revision, '--', split_path], cwd=repo).strip())
    if has_split:
        split = manifest(split_path)
        assert split['pinned_android'] == '53244b9d6b21d7e3a609e8cb11876efef70f218b' and split['verified_against_pure_export']
        expected.update(split['resource_sha256'])
    desktop_path = 'parity/desktop-a41ea0e/source-audit.json'
    has_desktop = bool(subprocess.check_output(['git', 'ls-tree', '--name-only', args.source_revision, '--', desktop_path], cwd=repo).strip()) if args.source_revision else (repo / desktop_path).is_file()
    desktop_revision = None
    if has_desktop:
        desktop = manifest(desktop_path)
        desktop_revision = desktop['commit']
        assert desktop_revision == 'a41ea0e1e1fa69fb543d827b853fe3aff5420951' and desktop['verified_against_pure_export']
        expected.update(desktop['resource_sha256'])
    records = []
    archive = zipfile.ZipFile(io.BytesIO(packaged_bytes(args.artifact))) if args.artifact else None
    try:
        for name, digest in sorted(expected.items()):
            source_root = 'shared/src/desktopMain/resources' if name in recognition['resources'] else 'desktop/src/main/resources'
            data = (repo / source_root / name).read_bytes() if args.sources else archive.read(name)
            actual = hashlib.sha256(data).hexdigest()
            records.append({'resource': name, 'bytes': len(data), 'sha256': actual,
                            'expected_sha256': digest, 'crlf_count': data.count(b'\r\n'), 'verified': actual == digest})
        result = {'check': 'pinned legal/default-string exact resource bytes',
                   'pinned_android': baseline['pinned_android'],
                   'additional_android_baselines': additional_baselines,
                   'desktop_reference': desktop_revision,
                    'source_revision': args.source_revision,
                    'artifact': str(args.artifact) if args.artifact else 'source resources',
                    'status': 'PASSED' if all(record['verified'] for record in records) else 'FAILED',
                    'resources': records}
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
        assert result['status'] == 'PASSED', 'Pinned resource bytes changed: ' + ', '.join(record['resource'] for record in records if not record['verified'])
        print(f'PASS: {len(expected)} pinned resources match exact source bytes, including Android MiLan Pro')
    finally:
        if archive is not None:
            archive.close()


if __name__ == '__main__':
    main()
