#!/usr/bin/env python3
"""Independent readback of generated PCM captured from production single/dual decoders."""
import argparse
import array
import gzip
import hashlib
import json
import math
from pathlib import Path
import sys


def projection(samples, hz, start_ms, length_ms=100):
    start = start_ms * 44100 // 1000
    count = length_ms * 44100 // 1000
    values = samples[start * 2:(start + count) * 2:2]
    assert len(values) == count
    real = sum(value * math.cos(2 * math.pi * hz * index / 44100) for index, value in enumerate(values))
    imaginary = sum(value * math.sin(2 * math.pi * hz * index / 44100) for index, value in enumerate(values))
    return 2 * math.hypot(real, imaginary) / count


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    records = []
    files = sorted(args.directory.glob("*.json"))
    assert len(files) == 5, "Both single-output modes and genuine dual decode must be present"
    for file in files:
        metadata = json.loads(file.read_text())
        assert metadata["origin"] == "GENERATED_AUDIO_PRODUCTION_NATIVE_DECODER"
        assert metadata["output"] == "CONTROLLED_SOFTWARE_SOURCE_DATA_LINE"
        assert (metadata["sample_rate"], metadata["channels"], metadata["format"]) == (44100, 2, "S16LE")
        raw = gzip.decompress((args.directory / metadata["file"]).read_bytes())
        assert len(raw) == metadata["bytes"]
        assert hashlib.sha256(raw).hexdigest() == metadata["pcm_sha256"]
        samples = array.array("h", raw)
        if sys.byteorder != "little":
            samples.byteswap()
        assert samples[::2] == samples[1::2], "Generated stereo channel isolation changed"
        peak = max(abs(value) for value in samples)
        if file.stem.startswith("single-eq-boost"):
            assert len(raw) == 88200 and peak == 1995
            assert set(samples) == {-1995, 1995}
            measurement = {"measured_gain_db": 20 * math.log10(peak / 1000), "expected_preamp_db": 6}
        elif file.stem.startswith("single-eq-cut-after-seek"):
            assert len(raw) == 88200 and peak == 501
            assert set(samples) == {-501, 501}
            measurement = {"measured_gain_db": 20 * math.log10(peak / 1000), "expected_preamp_db": -6}
        elif file.stem == "dual-eq-band5-cut":
            assert len(raw) == 564480
            windows = [{"output_ms": start, "primary_1000hz": projection(samples, 1000, start),
                        "incoming_4000hz": projection(samples, 4000, start)} for start in [100, 1750, 2800]]
            assert 2980 < windows[0]["primary_1000hz"] < 3040 and windows[0]["incoming_4000hz"] < 2
            assert 1450 < windows[1]["primary_1000hz"] < 1580 and 3650 < windows[1]["incoming_4000hz"] < 4050
            assert windows[2]["primary_1000hz"] < 2 and 7500 < windows[2]["incoming_4000hz"] < 8010
            measurement = {"windows": windows, "primary_center_gain_db": 20 * math.log10(windows[0]["primary_1000hz"] / 6000)}
        else:
            raise AssertionError(f"Unknown captured scenario: {file.name}")
        records.append({**metadata, "status": "PASSED", "peak_s16": peak, **measurement})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({"check": "independent native single/dual user-EQ PCM readback", "status": "PASSED",
                                      "physical_audible_acceptance": "BLOCKED", "records": records}, indent=2) + "\n")
    print("PASS: real single preamp/seek rebuild and dual primary/overlap/incoming EQ")


if __name__ == "__main__":
    main()
