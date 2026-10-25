#!/usr/bin/env python3
"""Record/check live Android feature sources without modifying that repository."""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument('android', type=Path)
parser.add_argument('--output', type=Path, default=Path('parity/53244b9/evidence/lan-update/android-live-reference.json'))
parser.add_argument('--watch', action='store_true', help='Notify and exit on the next change (30-second checks)')
args = parser.parse_args()
root = args.android.resolve()
while True:
    files = {root/'docs/MELOX-LAN.md', root/'android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXPlaybackService.kt'}
    for source in ('main', 'test', 'androidTest'):
        for package in ('lan', 'dlna', 'miplay', 'usb'):
            files.update(root.glob(f'android/app/src/{source}/kotlin/com/lladlam/melox/{package}/*.kt'))
            files.update(root.glob(f'android/app/src/{source}/java/com/lladlam/melox/{package}/*.java'))
    files.update(root.glob('android/app/src/main/kotlin/com/lladlam/melox/ui/player/*Output*.kt'))
    files.update(root.glob('android/app/src/main/cpp/*usb*'))
    # Features can land outside the output packages while this migration runs.
    # Track the complete Kotlin/Java/UI/test surface and release documentation.
    for source in ('main', 'test', 'androidTest'):
        for extension in ('kt', 'java'):
            files.update(root.glob(f'android/app/src/{source}/**/*.{extension}'))
    files.update(root.glob('docs/**/*.md'))
    files.update(root.glob('android/app/src/main/assets/legal/*'))
    files.update(root.glob('android/app/src/main/res/values*/strings.xml'))
    files.add(root/'android/app/build.gradle.kts')
    contents = {str(p.relative_to(root)): p.read_bytes() for p in sorted(files)}
    current = {name: hashlib.sha256(data).hexdigest() for name, data in contents.items()}
    old = json.loads(args.output.read_text()) if args.output.exists() else {}
    previous = old.get('files', {})
    changed = sorted(p for p in current.keys() | previous.keys() if current.get(p) != previous.get(p))
    if changed or not args.watch:
        # Local build-only copies make later diffs possible even while Android keeps changing.
        for name, data in contents.items():
            snapshot = Path('desktop/build/reference/android-live')/current[name]/Path(name).name
            snapshot.parent.mkdir(parents=True, exist_ok=True)
            if not snapshot.exists():
                snapshot.write_bytes(data)
        result = dict(repository='MeloX-Android local working tree',
                      head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
                      checked_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),files=current,
                      changed_since_previous_check=changed)
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(json.dumps(result,indent=2)+'\n')
        print(json.dumps({'changed_count':len(changed),'changed':changed},indent=2), flush=True)
        break
    time.sleep(30)
