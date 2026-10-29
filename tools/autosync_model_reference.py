#!/usr/bin/env python3
"""Independent NumPy/ONNX reference for the exact CPU running the contract.

The Viterbi and CIF formulas follow MeloX-AutoSync's Python reference. INT8
acoustic output for synthetic tones can differ substantially across CPU kernels,
so compare both implementations on the same machine rather than pinning Linux
frame positions as Windows ground truth.
"""
import json
import hashlib
import sys
import wave
from pathlib import Path

import numpy as np
import onnxruntime as ort

root = Path(sys.argv[1])
index_bytes = (root / 'android-models.json').read_bytes()
assert hashlib.sha256(index_bytes).hexdigest() == '4b7b54a6f436b4f74ac6028f3876477a2d7dffe13fba557f9649577354a0f0ed'
index = json.loads(index_bytes)
models = {'en': 'en-wav2vec2-base-960h-int8.onnx',
          'ja': 'ja-hubert-phoneme-ctc-int8.onnx', 'zh': 'zh-fa-zh-int8.onnx'}
targets = {'en': [11, 5, 15, 15, 8], 'ja': [46, 11, 4, 17, 5, 46]}

def align(log_probs, tokens):
    lp = np.asarray(log_probs, dtype=np.float64)
    frames = lp.shape[0]
    extended = np.zeros(2 * len(tokens) + 1, dtype=np.int64)
    extended[1::2] = tokens
    dp = np.full(len(extended), -1e30)
    dp[:2] = lp[0, extended[:2]]
    back = np.zeros((frames - 1, len(extended)), dtype=np.int8)
    for t in range(1, frames):
        one = np.full_like(dp, -1e30)
        one[1:] = dp[:-1]
        two = np.full_like(dp, -1e30)
        two[2:] = np.where(extended[2:] != extended[:-2], dp[:-2], -1e30)
        choices = np.stack((dp, one, two))
        back[t - 1] = np.argmax(choices, axis=0)
        dp = choices[back[t - 1], np.arange(len(dp))] + lp[t, extended]
    total = float(dp.max())
    state = len(dp) - 1 if dp[-1] >= dp[-2] else len(dp) - 2
    path = np.empty(frames, dtype=np.int64)
    for t in range(frames - 1, -1, -1):
        path[t] = state
        if t: state -= int(back[t - 1, state])
    spans = []
    for k in range(len(tokens)):
        indices = np.flatnonzero(path == 2 * k + 1)
        assert indices.size
        spans.append([int(indices[0]), int(indices[-1])])
    return {'spans': spans, 'total': total}

def chinese(alphas, peaks, count):
    threshold = 1.0 - 1e-4
    fire = np.flatnonzero(peaks >= threshold).astype(np.float64) - 1.5
    if len(fire) != count + 1:
        alphas = alphas / (alphas.sum() / (count + 1))
        integral = np.float32(0)
        recomputed = []
        for alpha in alphas:
            integral = np.float32(integral + alpha)
            recomputed.append(integral)
            if integral >= threshold: integral = np.float32(integral - threshold)
        fire = np.flatnonzero(np.asarray(recomputed) >= threshold).astype(np.float64) - 1.5
    assert len(fire) == count + 1
    intervals = []
    if fire[0] > 5: intervals.append((True, 0.0, fire[0]))
    for start, end in zip(fire, fire[1:]):
        intervals.append((False, start, min(end, start + 12)))
        if end - start > 12: intervals.append((True, start + 12, end))
    last = intervals[-1]
    end = (len(peaks) + fire[-1]) * 0.5 if len(peaks) - fire[-1] > 5 else len(peaks)
    intervals[-1] = (last[0], last[1], end)
    return [[int(start * .02 * 1000), int(end * .02 * 1000)] for silence, start, end in intervals if not silence]

results = {}
for lang, model in models.items():
    metadata = next(item for item in index['languages'][lang]['files'] if item['name'] == model)
    model_path = root / model
    digest = hashlib.sha256()
    with model_path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''): digest.update(block)
    assert model_path.stat().st_size == metadata['bytes'] and digest.hexdigest() == metadata['sha256']
    with wave.open(str(root / f'{lang}_vector.wav'), 'rb') as wav:
        assert (wav.getnchannels(), wav.getsampwidth(), wav.getframerate(), wav.getnframes()) == (1, 2, 16000, 48000)
        audio = np.frombuffer(wav.readframes(48000), dtype='<i2').astype(np.float32) / np.float32(32768)
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    session = ort.InferenceSession(str(model_path), options, providers=['CPUExecutionProvider'])
    feed = {'input_values': audio[None, :]}
    if lang == 'zh': feed['token_num'] = np.array([6], dtype=np.int64)
    outputs = dict(zip((x.name for x in session.get_outputs()), session.run(None, feed)))
    results[lang] = {'spans': chinese(outputs['us_alphas'][0], outputs['us_peaks'][0], 5)} if lang == 'zh' else align(outputs['log_probs'][0], targets[lang])
    print(f'{lang}: {results[lang]}', flush=True)
    del session
(root / 'python-reference.json').write_text(json.dumps(results), encoding='utf-8')
