#!/usr/bin/env python3
"""Compare explicitly cropped, same-state app captures; metrics are not parity acceptance.

Requires Pillow and NumPy. Never crops automatically or silently resizes a capture.
"""
import argparse
import hashlib
import json
import io
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageCms


def crop_spec(text):
    x, y, width, height = map(int, text.split(","))
    if min(x, y) < 0 or min(width, height) <= 0:
        raise argparse.ArgumentTypeError("crop must be x,y,width,height with positive dimensions")
    return x, y, width, height


def load(path, crop, normalize_srgb=False):
    image = Image.open(path).convert("RGB")
    profile_bytes = image.info.get("icc_profile")
    profile = ImageCms.ImageCmsProfile(io.BytesIO(profile_bytes)) if profile_bytes else None
    color = {
        "embedded_profile": ImageCms.getProfileDescription(profile).strip() if profile else None,
        "untagged_assumption": None if profile else "sRGB",
        "normalized_to_srgb": normalize_srgb,
    }
    if normalize_srgb:
        srgb = ImageCms.ImageCmsProfile(ImageCms.createProfile("sRGB"))
        if profile:
            image = ImageCms.profileToProfile(image, profile, srgb, outputMode="RGB")
        image.info["icc_profile"] = srgb.tobytes()
    if crop:
        x, y, width, height = crop
        if x + width > image.width or y + height > image.height:
            raise ValueError(f"Crop {crop} is outside {path}: {image.size}")
        image = image.crop((x, y, x + width, y + height))
    return image, color


def mean_filter(array, window=11):
    """Valid uniform-window mean, independently for each color channel."""
    integral = np.pad(array, ((1, 0), (1, 0), (0, 0))).cumsum(0).cumsum(1)
    return (integral[window:, window:] - integral[:-window, window:]
            - integral[window:, :-window] + integral[:-window, :-window]) / window**2


def ssim(a, b):
    window = min(11, a.shape[0], a.shape[1])
    mu_a, mu_b = mean_filter(a, window), mean_filter(b, window)
    var_a = np.maximum(0, mean_filter(a * a, window) - mu_a * mu_a)
    var_b = np.maximum(0, mean_filter(b * b, window) - mu_b * mu_b)
    covariance = mean_filter(a * b, window) - mu_a * mu_b
    c1, c2 = (0.01 * 255)**2, (0.03 * 255)**2
    scores = ((2 * mu_a * mu_b + c1) * (2 * covariance + c2)
              / ((mu_a * mu_a + mu_b * mu_b + c1) * (var_a + var_b + c2)))
    return float(scores.mean())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", type=Path)
    parser.add_argument("desktop", type=Path)
    parser.add_argument("--reference-crop", type=crop_spec)
    parser.add_argument("--desktop-crop", type=crop_spec)
    parser.add_argument("--resize-desktop", action="store_true", help="Explicit density normalization, recorded in report")
    parser.add_argument("--normalize-srgb", action="store_true", help="Convert embedded ICC profiles to sRGB before comparison; untagged Skia images are assumed sRGB")
    parser.add_argument("--scene", required=True)
    parser.add_argument("--reference-sha", default="unknown")
    parser.add_argument("--expected-sha", default="94e17c920bf554966584955afa2bd2ee92580a8b")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    reference, reference_color = load(args.reference, args.reference_crop, args.normalize_srgb)
    desktop, desktop_color = load(args.desktop, args.desktop_crop, args.normalize_srgb)
    original_size = desktop.size
    if desktop.size != reference.size:
        if not args.resize_desktop:
            parser.error(f"Size mismatch: {reference.size} vs {desktop.size}; set crops or explicitly normalize density")
        desktop = desktop.resize(reference.size, Image.Resampling.LANCZOS)
    a, b = np.asarray(reference, dtype=np.float64), np.asarray(desktop, dtype=np.float64)
    difference = np.abs(a - b)
    report = {
        "scene": args.scene, "status": "PARTIAL",
        "evidence": "Image metrics only; identical content, settings and animation timestamps require review",
        "reference_sha": args.reference_sha, "expected_sha": args.expected_sha,
        "pinned_reference": args.reference_sha == args.expected_sha,
        "reference": str(args.reference), "desktop": str(args.desktop),
        "reference_image_sha256": hashlib.sha256(args.reference.read_bytes()).hexdigest(),
        "desktop_image_sha256": hashlib.sha256(args.desktop.read_bytes()).hexdigest(),
        "reference_crop": args.reference_crop, "desktop_crop": args.desktop_crop,
        "compared_size": reference.size, "desktop_crop_original_size": original_size,
        "desktop_resized": original_size != reference.size,
        "reference_color": reference_color, "desktop_color": desktop_color,
        "ssim_rgb_uniform_11": ssim(a, b), "mean_absolute_error": float(difference.mean()),
        "root_mean_square_error": float(np.sqrt(np.square(a - b).mean())),
        "pixels_over_8_levels_percent": float((difference.max(2) > 8).mean() * 100),
    }
    args.output.mkdir(parents=True, exist_ok=True)
    reference.save(args.output / "reference.png")
    desktop.save(args.output / "desktop.png")
    Image.blend(reference, desktop, 0.5).save(args.output / "overlay.png")
    ImageChops.difference(reference, desktop).save(args.output / "difference.png")
    heat = np.zeros_like(a, dtype=np.uint8)
    heat[:, :, 0] = np.minimum(difference.max(2) * 4, 255).astype(np.uint8)
    Image.fromarray(heat).save(args.output / "heatmap.png")
    side_by_side = Image.new("RGB", (reference.width * 2, reference.height))
    side_by_side.paste(reference, (0, 0))
    side_by_side.paste(desktop, (reference.width, 0))
    side_by_side.save(args.output / "side-by-side.png")
    (args.output / "metrics.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps(report, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
