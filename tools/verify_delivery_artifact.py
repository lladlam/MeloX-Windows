#!/usr/bin/env python3
"""Independently verify a downloaded Windows delivery ZIP.

Checks, without trusting CI's own summary:
  1. exact byte size and SHA-256 of the delivery ZIP
  2. CRC of every ZIP entry
  3. stamped build-info revision equals the CI run's head SHA
  4. packaged pinned resource bytes (tools/verify_pinned_resources.py --artifact)
  5. packaged native decoder bytes and PE magic (tools/verify_decoder_bundle.py --artifact)
  6. required runtime entries exist (launcher exe, application jar, ffmpeg.exe)

Physical audio, Windows execution of ffmpeg.exe, DPAPI and pinned-Android pixels
are out of scope here and stay BLOCKED/PENDING.

Usage:
  verify_delivery_artifact.py --run-id ID --expected-sha SHA --directory DIR --output FILE
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import zipfile

repo = Path(__file__).resolve().parents[1]

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--run-id', required=True)
parser.add_argument('--expected-sha', required=True)
parser.add_argument('--directory', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()

zips = sorted(p for p in args.directory.rglob('*.zip') if p.is_file())
assert len(zips) == 1, f'expected exactly one delivery ZIP under {args.directory}: {zips}'
zip_path = zips[0]
raw = zip_path.read_bytes()
size = len(raw)
digest = hashlib.sha256(raw).hexdigest()

failures = []
with zipfile.ZipFile(io.BytesIO(raw)) as archive:
    bad = archive.testzip()
    if bad is not None:
        failures.append(f'CRC failure at {bad}')
    entries = archive.namelist()
    jar_names = [name for name in entries if name.endswith('.jar')]
    # jpackage layout: <root>/MeloX-Desktop.exe, app/*.jar, runtime/**; the native
    # decoder and the build-info stamp live inside the application JAR.
    jars = [name for name in jar_names if name.startswith('app/')]
    assert jars, f'no application JAR under app/: {jar_names[:10]}'
    assert len(jars) == 1, f'expected one application JAR, got {jars}'
    launchers = [name for name in entries if name.lower().endswith('.exe') and not name.startswith('runtime/')]
    assert launchers, 'no launcher exe in the delivery'
    decoders = []  # the native decoder ships inside the application JAR, not as a ZIP entry

    revision = None
    decoder_bytes = None
    with zipfile.ZipFile(io.BytesIO(archive.read(jars[0]))) as jar:
        bad_jar_entry = jar.testzip()
        if bad_jar_entry is not None:
            failures.append(f'JAR CRC failure at {bad_jar_entry}')
        jar_names = jar.namelist()
        build_info = [name for name in jar_names if name.endswith('build-info.properties')]
        assert build_info, 'no build-info.properties in the application JAR'
        for name in build_info:
            text = jar.read(name).decode('utf-8')
            for line in text.splitlines():
                if line.startswith('commit='):
                    revision = line.split('=', 1)[1].strip()
        decoders = [name for name in jar_names if name == 'decoder/ffmpeg.exe']
        assert len(decoders) == 1, f'expected one packaged ffmpeg.exe in the JAR, got {decoders}'
        decoder_info = jar.getinfo(decoders[0])
        decoder_bytes = decoder_info.file_size
    if revision != args.expected_sha:
        failures.append(f'build-info revision {revision} != expected {args.expected_sha}')

with zipfile.ZipFile(io.BytesIO(raw)) as archive:
    entry_sizes = {info.filename: info.file_size for info in archive.infolist()}

pinned_output = args.output.parent / 'download-pinned-resources.json'
decoder_output = args.output.parent / 'download-decoder-bundle.json'
tool_status = {}
tools = []
for label, command, output in (
    ('pinned_resources', [sys.executable, 'tools/verify_pinned_resources.py', '--artifact', str(zip_path), '--output', str(pinned_output)], pinned_output),
    ('decoder_bundle', [sys.executable, 'tools/verify_decoder_bundle.py', '--artifact', str(zip_path), '--output', str(decoder_output)], decoder_output),
):
    completed = subprocess.run(command, cwd=repo, capture_output=True, text=True)
    passed = completed.returncode == 0 and output.exists()
    tool_status[label] = 'PASSED' if passed else 'FAILED'
    tools.append({'label': label, 'exit_code': completed.returncode,
                  'stdout_tail': completed.stdout[-800:], 'stderr_tail': completed.stderr[-800:]})
    if not passed:
        failures.append(f'verification tool failed: {label}')

report = {
    'run_id': args.run_id,
    'expected_head_sha': args.expected_sha,
    'delivery_zip': {'name': zip_path.name, 'bytes': size, 'sha256': digest},
    'zip_crc_all_entries': 'PASSED' if bad is None else 'FAILED',
    'jar_crc_all_entries': 'PASSED' if bad_jar_entry is None else 'FAILED',
    'zip_entry_count': len(entry_sizes),
    'launcher_entries': launchers,
    'application_jar': jars[0],
    'decoder_entry': f'{jars[0]}!/{decoders[0]}',
    'decoder_bytes': decoder_bytes,
    'build_info_revision': revision,
    'pinned_resource_check': tool_status.get('pinned_resources', 'FAILED'),
    'decoder_bundle_check': tool_status.get('decoder_bundle', 'FAILED'),
    'tools': tools,
    'physical_windows_execution': 'BLOCKED_NOT_RUN_HERE',
    'pinned_android_pixel_acceptance': 'BLOCKED_NO_REFERENCE_FRAMES',
    'failures': failures,
    'status': 'PASSED' if not failures else 'FAILED',
}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
print(json.dumps({k: report[k] for k in ('status', 'run_id', 'expected_head_sha', 'build_info_revision',
                                         'zip_crc_all_entries', 'zip_entry_count')}, indent=2))
print('delivery: %s bytes, sha256 %s' % (size, digest))
if failures:
    print('FAILURES:', failures)
    sys.exit(1)
print('PASS: independent download verification (%s)' % zip_path.name)
