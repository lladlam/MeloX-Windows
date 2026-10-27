#!/usr/bin/env python3
"""Compare the actual PE launcher icon resources with the selected Android ICO."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ICON = 'desktop/src/main/resources/branding/melox-android.ico'


def u16(data, offset):
    return struct.unpack_from('<H', data, offset)[0]


def u32(data, offset):
    return struct.unpack_from('<I', data, offset)[0]


def ico_images(data):
    reserved, kind, count = struct.unpack_from('<HHH', data)
    assert reserved == 0 and kind == 1 and count > 0, 'invalid ICO'
    images = {}
    for i in range(count):
        width, height, _, _, _, _, length, start = struct.unpack_from('<BBBBHHII', data, 6 + i * 16)
        assert start + length <= len(data), 'truncated ICO image'
        size = (width or 256, height or 256)
        assert size not in images, f'duplicate ICO dimensions {size}'
        images[size] = data[start:start + length]
    assert {(16, 16), (32, 32), (48, 48), (256, 256)} <= set(images), 'missing Windows icon sizes'
    return images


def pe_icon_resources(data):
    assert data[:2] == b'MZ', 'launcher is not PE'
    pe = u32(data, 0x3c)
    assert data[pe:pe + 4] == b'PE\0\0', 'invalid PE signature'
    section_count, optional_size = u16(data, pe + 6), u16(data, pe + 20)
    optional = pe + 24
    magic = u16(data, optional)
    assert magic in (0x10b, 0x20b), 'unknown PE format'
    directory = optional + (96 if magic == 0x10b else 112)
    resource_rva = u32(data, directory + 2 * 8)
    assert resource_rva, 'PE has no resource directory'
    sections = optional + optional_size

    def offset(rva):
        for i in range(section_count):
            section = sections + 40 * i
            virtual_size, va, raw_size, raw_offset = struct.unpack_from('<IIII', data, section + 8)
            if va <= rva < va + max(virtual_size, raw_size):
                assert rva - va < raw_size, 'resource RVA outside file-backed section'
                return raw_offset + rva - va
        raise AssertionError(f'no PE section for RVA {rva:#x}')

    base = offset(resource_rva)
    leaves = []

    def walk(relative, path):
        assert len(path) <= 3, 'unexpected resource directory depth'
        directory_offset = base + relative
        named, numbered = struct.unpack_from('<HH', data, directory_offset + 12)
        for i in range(named + numbered):
            name, target = struct.unpack_from('<II', data, directory_offset + 16 + 8 * i)
            identity = None if name & 0x80000000 else name
            if target & 0x80000000:
                walk(target & 0x7fffffff, path + [identity])
            elif len(path) == 2:
                entry = base + target
                rva, length = struct.unpack_from('<II', data, entry)
                start = offset(rva)
                assert start + length <= len(data), 'truncated PE icon resource'
                leaves.append((path[0], path[1], identity, data[start:start + length]))

    walk(0, [])
    icons = {}
    for kind, identity, _, payload in leaves:
        if kind == 3:
            icons.setdefault(identity, []).append(payload)
    groups = [payload for kind, _, _, payload in leaves if kind == 14]
    assert groups and icons, 'launcher has no icon group or icon images'
    return icons, groups


def launcher_bytes(artifact):
    if artifact.is_dir():
        paths = [p for p in artifact.glob('*.exe') if p.name.lower() == 'melox-desktop.exe']
        assert len(paths) == 1, f'expected one launcher in {artifact}'
        return paths[0].read_bytes(), paths[0].name
    if artifact.suffix.lower() == '.exe':
        return artifact.read_bytes(), artifact.name
    with zipfile.ZipFile(artifact) as archive:
        paths = [name for name in archive.namelist() if name.lower() == 'melox-desktop.exe']
        assert len(paths) == 1, f'expected one launcher in {artifact}'
        return archive.read(paths[0]), paths[0]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--artifact', type=Path, required=True)
    parser.add_argument('--source-revision')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    ico = subprocess.check_output(['git', 'show', args.source_revision + ':' + ICON], cwd=ROOT) if args.source_revision else (ROOT / ICON).read_bytes()
    expected = ico_images(ico)
    launcher, name = launcher_bytes(args.artifact)
    icons, groups = pe_icon_resources(launcher)
    matches = []
    for group in groups:
        reserved, kind, count = struct.unpack_from('<HHH', group)
        if reserved != 0 or kind != 1 or len(group) < 6 + count * 14:
            continue
        matched = set()
        for i in range(count):
            width, height, _, _, _, _, length, identity = struct.unpack_from('<BBBBHHIH', group, 6 + 14 * i)
            size = (width or 256, height or 256)
            if size in expected and any(image == expected[size] and len(image) == length for image in icons.get(identity, [])):
                matched.add(size)
        matches.append(matched)
    matched = max(matches, key=len, default=set())
    result = {'status': 'PASSED' if matched == set(expected) else 'FAILED', 'launcher': name,
              'source_revision': args.source_revision, 'ico_sha256': hashlib.sha256(ico).hexdigest(),
              'expected_sizes': sorted(size[0] for size in expected), 'matched_sizes': sorted(size[0] for size in matched)}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    assert result['status'] == 'PASSED', f'launcher icon does not match Android icon: {result}'
    print('PASS: PE launcher embeds every Android-derived ICO image')


if __name__ == '__main__':
    main()
