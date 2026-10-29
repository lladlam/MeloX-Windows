#!/usr/bin/env python3
"""Obtain the pinned public beta.4 models for an opt-in real ONNX contract run."""
import hashlib
import shutil
import subprocess
import sys
from pathlib import Path

repo = Path(__file__).resolve().parents[1]
target = Path(sys.argv[1]).resolve()
target.mkdir(parents=True, exist_ok=True)
assets = (
    'android-models.json',
    'en-wav2vec2-base-960h-int8.onnx', 'en-vocab.json',
    'ja-hubert-phoneme-ctc-int8.onnx', 'ja-vocab.json',
    'zh-fa-zh-int8.onnx', 'zh-tokens.json', 'zh-seg_dict',
)
assert not any((target / name).exists() for name in assets), 'Use a fresh fixture directory'
command = ['gh', 'release', 'download', 'android-models-v0.1.0-beta.4', '--repo', 'lladlam/MeloX-AutoSync',
           '--dir', str(target)]
for asset in assets:
    command.extend(['--pattern', asset])
subprocess.run(command, check=True)
index = target / 'android-models.json'
assert hashlib.sha256(index.read_bytes()).hexdigest() == '4b7b54a6f436b4f74ac6028f3876477a2d7dffe13fba557f9649577354a0f0ed'
for language in ('en', 'ja', 'zh'):
    shutil.copyfile(repo / f'shared/src/desktopTest/resources/autosync/{language}_vector.wav', target / f'{language}_vector.wav')
print('Fetched pinned index, three model packs and deterministic PCM vectors; the contract verifies every installed file.')
