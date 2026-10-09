#!/usr/bin/env python3
"""Export branded vectors from the pinned Android commit, preserving path geometry."""
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT.parent / "MeloX-Android"
SHA = "94e17c920bf554966584955afa2bd2ee92580a8b"
NS = "{http://schemas.android.com/apk/res/android}"


def main():
    out = ROOT / "desktop/src/main/resources/icons"
    out.mkdir(parents=True, exist_ok=True)
    manifest = {"android_sha": SHA, "assets": []}
    for name in ("melox_home_tab", "ic_melox_brand_gear"):
        source = f"android/app/src/main/res/drawable/{name}.xml"
        raw = subprocess.check_output(["git", "-C", str(ANDROID), "show", f"{SHA}:{source}"])
        vector = ET.fromstring(raw)
        svg = ET.Element("svg", {
            "xmlns": "http://www.w3.org/2000/svg",
            "width": "24", "height": "24",
            "viewBox": f"0 0 {vector.attrib[NS + 'viewportWidth']} {vector.attrib[NS + 'viewportHeight']}",
        })
        for path in vector:
            ET.SubElement(svg, "path", {
                "d": path.attrib[NS + "pathData"], "fill": "#000000",
                "fill-rule": "evenodd" if path.attrib.get(NS + "fillType") == "evenOdd" else "nonzero",
            })
        exported = ET.tostring(svg, encoding="utf-8") + b"\n"
        (out / f"{name}.svg").write_bytes(exported)
        manifest["assets"].append({"android": source, "desktop": f"icons/{name}.svg",
                                   "android_sha256": hashlib.sha256(raw).hexdigest(),
                                   "svg_sha256": hashlib.sha256(exported).hexdigest()})
    (out / "android-origin.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Exported {len(manifest['assets'])} pinned Android vectors to {out}")


if __name__ == "__main__":
    main()
