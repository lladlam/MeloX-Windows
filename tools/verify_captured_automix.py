#!/usr/bin/env python3
"""Independent spectral readback of generated sine fixtures consumed by production UI playback."""
import argparse
import hashlib
import gzip
import json
from pathlib import Path
import numpy as np

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    capture = json.loads((args.directory / "capture.json").read_text())
    assert capture["audio_endpoint"] == "VALIDATION_SOFTWARE_PCM_OUTPUT"
    records = []
    overlap_seen = False
    for endpoint in capture["pcm_outputs"]:
        file = args.directory / endpoint["file"]
        raw = file.read_bytes() if file.is_file() else gzip.decompress(file.with_suffix(file.suffix + ".gz").read_bytes())
        assert len(raw) == endpoint["written_frames"] * 4
        pcm = np.frombuffer(raw, dtype="<i2").reshape((-1, 2))[:endpoint["consumed_frames"]]
        windows = []
        for start in range(0, len(pcm) - 4410 + 1, 4410):
            samples = pcm[start:start + 4410, 0].astype(float)
            transform = np.fft.rfft(samples)
            a = float(abs(transform[100]) * 2 / len(samples))
            b = float(abs(transform[400]) * 2 / len(samples))
            windows.append({"output_ms": start * 1000 / 44100, "primary_1000hz": a, "incoming_4000hz": b})
        simultaneous = [w for w in windows if w["primary_1000hz"] > 1000 and w["incoming_4000hz"] > 1000]
        primary = [w for w in windows if w["primary_1000hz"] > 5900 and w["incoming_4000hz"] < 10]
        incoming = [w for w in windows if w["incoming_4000hz"] > 5900 and w["primary_1000hz"] < 10]
        if simultaneous:
            assert primary and incoming, "Must observe actual primary, dual overlap and isolated incoming on one endpoint"
            assert primary[0]["output_ms"] < simultaneous[0]["output_ms"] < incoming[-1]["output_ms"]
            overlap_seen = True
        records.append({**endpoint, "sha256": hashlib.sha256(raw).hexdigest(),
                        "consumed_peak_s16": int(np.max(np.abs(pcm.astype(np.int32)))) if len(pcm) else 0,
                        "clipped_samples": int(np.count_nonzero((pcm == -32768) | (pcm == 32767))),
                        "simultaneous_100ms_windows": len(simultaneous), "windows": windows})
    assert overlap_seen, "No independently measured two-frequency overlap"
    assert all(record["closed"] for record in records)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({"check": "production capture PCM spectral readback", "status": "PASSED",
                                      "generated_frequencies_hz": [1000, 4000], "endpoints": records,
                                      "physical_audible_acceptance": "BLOCKED",
                                      "android_pixel_parity": "BLOCKED"}, indent=2) + "\n")
    print("PASS: real primary / two-frequency overlap / incoming on same software endpoint")

if __name__ == "__main__":
    main()
