#!/usr/bin/env python3
"""Legal generated WAV/artwork for real Local-provider PCM and production UI captures."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import struct
import wave
from PIL import Image, ImageDraw

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    args.directory.mkdir(parents=True, exist_ok=True)
    records = []
    for name, hz, seconds, color in [("generated-1000hz", 1000, 20, (45, 140, 230)), ("generated-4000hz", 4000, 16, (200, 90, 160))]:
        file = args.directory / f"{name}.wav"
        pcm = bytearray()
        for frame in range(seconds * 44100):
            value = round(6000 * math.sin(2 * math.pi * hz * frame / 44100))
            pcm.extend(struct.pack("<hh", value, value))
        with wave.open(str(file), "wb") as stream:
            stream.setnchannels(2)
            stream.setsampwidth(2)
            stream.setframerate(44100)
            stream.writeframes(pcm)
        image = Image.new("RGB", (512, 512), color)
        draw = ImageDraw.Draw(image)
        for radius in range(200, 20, -24):
            draw.ellipse((256-radius, 256-radius, 256+radius, 256+radius), outline="white", width=4)
        draw.text((30, 30), f"GENERATED {hz} Hz", fill="white")
        image.save(args.directory / f"{name}.png")
        records.append({"file": file.name, "frequency_hz": hz, "duration_ms": seconds * 1000, "channels": 2,
                        "sample_rate": 44100, "pcm_amplitude": 6000, "wav_sha256": hashlib.sha256(file.read_bytes()).hexdigest()})
    (args.directory / "fixtures.json").write_text(json.dumps({"origin": "generated sine PCM, no private audio", "tracks": records}, indent=2)+"\n")

if __name__ == "__main__":
    main()
