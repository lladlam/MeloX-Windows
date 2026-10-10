#!/usr/bin/env python3
"""Verify actual source/JAR/app-image/distribution FFmpeg bytes and native PCM.

This is packaging/decoder evidence. It never declares physical audible output.
"""
import argparse
import hashlib
import io
import json
import math
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import struct
import subprocess
import tempfile
import wave
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def verify(data, expected, label):
    if data.startswith(b"version https://git-lfs.github.com/spec/v1"):
        raise ValueError(f"{label}: unresolved Git LFS pointer")
    digest = hashlib.sha256(data).hexdigest()
    if len(data) != expected["size"] or digest != expected["sha256"]:
        raise ValueError(f"{label}: size/SHA-256 mismatch: {len(data)} / {digest}")
    if expected["magic"] == "ELF":
        if data[:5] != b"\x7fELF\x02" or struct.unpack_from("<H", data, 18)[0] != 62:
            raise ValueError(f"{label}: expected x64 ELF executable")
    else:
        offset = struct.unpack_from("<I", data, 0x3c)[0]
        if data[:2] != b"MZ" or data[offset:offset + 4] != b"PE\0\0" or struct.unpack_from("<H", data, offset + 4)[0] != 0x8664:
            raise ValueError(f"{label}: expected x64 Windows PE executable")
    return {"location": label, "bytes": len(data), "sha256": digest, "magic": expected["magic"], "verified": True}


def jars(artifact):
    if artifact.is_dir():
        for file in sorted(artifact.rglob("*.jar")):
            yield str(file), file
    elif artifact.suffix.lower() == ".jar":
        yield str(artifact), artifact
    elif artifact.suffix.lower() == ".zip":
        with zipfile.ZipFile(artifact) as distribution:
            for entry in distribution.namelist():
                if entry.lower().endswith(".jar"):
                    yield f"{artifact}!/{entry}", io.BytesIO(distribution.read(entry))
    else:
        raise ValueError("Artifact must be a JAR, app-image directory or final ZIP")


def native_smoke(data, name):
    expected_name = "ffmpeg.exe" if os.name == "nt" else "ffmpeg"
    if name != expected_name:
        raise ValueError(f"Cannot execute {name} on this OS; native test is BLOCKED")
    preferred = Path("/tmp/opencode")
    with tempfile.TemporaryDirectory(prefix="melox-bundle-", dir=preferred if preferred.is_dir() and os.name != "nt" else None) as temporary:
        directory = Path(temporary)
        executable = directory / name
        executable.write_bytes(data)
        executable.chmod(0o700)
        version = subprocess.run([str(executable), "-version"], capture_output=True, text=True, timeout=30, check=True)
        sample = directory / "tone.wav"
        pcm = b"".join(struct.pack("<hh", 1000 if i % 100 < 50 else -1000, 1000 if i % 100 < 50 else -1000) for i in range(11025))
        with wave.open(str(sample), "wb") as wav:
            wav.setnchannels(2)
            wav.setsampwidth(2)
            wav.setframerate(44100)
            wav.writeframes(pcm)
        decoded = subprocess.run([str(executable), "-hide_banner", "-loglevel", "error", "-nostdin", "-xerror", "-i", str(sample),
                                  "-f", "s16le", "-acodec", "pcm_s16le", "-ar", "44100", "-ac", "2", "pipe:1"],
                                 capture_output=True, timeout=30, check=True)
        if decoded.stdout != pcm:
            raise ValueError("Native packaged decoder did not preserve the known WAV PCM")
        formats = []
        for extension, codec in (("mp3", "libmp3lame"), ("flac", "flac"), ("m4a", "aac")):
            encoded = directory / f"tone.{extension}"
            subprocess.run([str(executable), "-hide_banner", "-loglevel", "error", "-nostdin", "-i", str(sample), "-c:a", codec, str(encoded)],
                           capture_output=True, timeout=30, check=True)
            result = subprocess.run([str(executable), "-hide_banner", "-loglevel", "error", "-nostdin", "-xerror", "-i", str(encoded),
                                     "-f", "s16le", "-ar", "44100", "-ac", "2", "pipe:1"], capture_output=True, timeout=30, check=True)
            samples = struct.unpack(f"<{len(result.stdout) // 2}h", result.stdout)
            rms = math.sqrt(sum(value * value for value in samples) / len(samples))
            if len(result.stdout) < len(pcm) * .9 or len(result.stdout) > len(pcm) * 1.25 or not 500 < rms < 1500:
                raise ValueError(f"Packaged {extension} decode has wrong duration/energy: {len(result.stdout)} / {rms}")
            formats.append({"format": extension, "decoded_bytes": len(result.stdout), "rms_s16": rms, "status": "PASSED"})
        received = []
        class AuthorizedFixture(BaseHTTPRequestHandler):
            def do_GET(self):
                accepted = self.headers.get("Authorization") == "Bearer generated-fixture"
                received.append(accepted)
                self.send_response(200 if accepted else 403)
                self.send_header("Content-Length", str(sample.stat().st_size if accepted else 0))
                self.end_headers()
                if accepted:
                    self.wfile.write(sample.read_bytes())
            def log_message(self, *args):
                pass
        server = ThreadingHTTPServer(("127.0.0.1", 0), AuthorizedFixture)
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            result = subprocess.run([str(executable), "-hide_banner", "-loglevel", "error", "-nostdin", "-xerror",
                                     "-headers", "Authorization: Bearer generated-fixture\r\n", "-i", f"http://127.0.0.1:{server.server_port}/tone.wav",
                                     "-f", "s16le", "-ar", "44100", "-ac", "2", "pipe:1"], capture_output=True, timeout=30, check=True)
            if result.stdout != pcm or not received or not all(received):
                raise ValueError("Packaged HTTP decoder did not carry required headers or preserve PCM")
        finally:
            server.shutdown()
            server.server_close()
            worker.join()
        return {"os": os.name, "version": version.stdout.splitlines()[0], "pcm_bytes": len(decoded.stdout),
                "pcm_sha256": hashlib.sha256(decoded.stdout).hexdigest(), "decoder_native_run": "PASSED",
                "formats": formats, "authorized_http_pcm": "PASSED",
                "physical_audible_output": "BLOCKED", "physical_audible_reason": "No hardware/loopback listening test in this packaging check"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sources", action="store_true")
    parser.add_argument("--artifact", type=Path)
    parser.add_argument("--native-resource", choices=("ffmpeg", "ffmpeg.exe"), default="ffmpeg.exe" if os.name == "nt" else "ffmpeg")
    parser.add_argument("--run", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads((ROOT / "desktop/src/main/resources/decoder/manifest.json").read_text())
    results, native = [], None
    if args.sources:
        for name, expected in manifest.items():
            path = ROOT / "desktop/src/main/resources/decoder" / name
            data = path.read_bytes()
            results.append(verify(data, expected, str(path.relative_to(ROOT))))
            if name == args.native_resource:
                native = data
    if args.artifact:
        found = 0
        for label, jar in jars(args.artifact):
            with zipfile.ZipFile(jar) as archive:
                names = archive.namelist()
                entry = f"decoder/{args.native_resource}"
                if entry not in names:
                    continue
                if names.count(entry) != 1:
                    raise ValueError(f"{label}: duplicate decoder resources")
                data = archive.read(entry)
                results.append(verify(data, manifest[args.native_resource], f"{label}!/{entry}"))
                native = data
                found += 1
        if found != 1:
            raise ValueError(f"{args.artifact}: expected exactly one bundled native decoder, found {found}")
    if not results:
        raise ValueError("Select --sources or --artifact")
    report = {"check": "source/packaged decoder integrity", "resources": results,
              "native": native_smoke(native, args.native_resource) if args.run else {"decoder_native_run": "NOT_RUN"},
              "audible_acceptance": "BLOCKED"}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
