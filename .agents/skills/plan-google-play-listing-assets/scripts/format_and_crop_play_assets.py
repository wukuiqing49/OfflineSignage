#!/usr/bin/env python3
"""Format and resize AI-generated images (.jfif, .webp, .png, .jpg) to exact Google Play specs."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from PIL import Image


TARGET_SIZES = {
    "feature_graphic": (1024, 500),
    "feature": (1024, 500),
    "tv_screenshot": (1920, 1080),
    "phone_landscape": (1920, 1080),
    "phone_portrait": (1080, 1920),
    "phone": (1080, 1920),
    "tablet": (2560, 1600),
    "icon": (512, 512),
}


def smart_crop_and_resize(img: Image.Image, target_width: int, target_height: int) -> Image.Image:
    """Crop and resize image to exact dimensions without distortion or alpha channel."""
    # Convert RGBA / P / CMYK to standard RGB (strip alpha channel for Google Play compliance)
    if img.mode in ("RGBA", "LA", "P"):
        background = Image.new("RGB", img.size, (255, 255, 255))
        if img.mode == "P":
            img = img.convert("RGBA")
        background.paste(img, mask=img.split()[-1] if "A" in img.mode else None)
        img = background
    elif img.mode != "RGB":
        img = img.convert("RGB")

    orig_width, orig_height = img.size
    target_ratio = target_width / target_height
    orig_ratio = orig_width / orig_height

    if orig_ratio > target_ratio:
        # Source is wider than target -> crop left & right
        new_width = int(orig_height * target_ratio)
        left = (orig_width - new_width) // 2
        img = img.crop((left, 0, left + new_width, orig_height))
    elif orig_ratio < target_ratio:
        # Source is taller than target -> crop top & bottom
        new_height = int(orig_width / target_ratio)
        top = (orig_height - new_height) // 2
        img = img.crop((0, top, orig_width, top + new_height))

    # High-quality resize
    return img.resize((target_width, target_height), Image.Resampling.LANCZOS)


def process_image(input_path: Path, output_path: Path, target_type: str) -> None:
    target_dim = TARGET_SIZES.get(target_type.lower())
    if not target_dim:
        raise ValueError(f"Unknown target type '{target_type}'. Supported: {list(TARGET_SIZES.keys())}")

    target_w, target_h = target_dim

    try:
        with Image.open(input_path) as img:
            processed = smart_crop_and_resize(img, target_w, target_h)
            output_path.parent.mkdir(parents=True, exist_ok=True)
            # Ensure .png extension
            final_output = output_path.with_suffix(".png")
            processed.save(final_output, format="PNG", optimize=True)
            print(f"SUCCESS: Converted '{input_path.name}' -> '{final_output.name}' ({target_w}x{target_h} px, 24-bit RGB PNG, No Alpha)")
    except Exception as error:
        print(f"ERROR processing '{input_path}': {error}", file=sys.stderr)


def auto_detect_type(filename: str, img_size: tuple[int, int]) -> str:
    lower_name = filename.lower()
    if "feature" in lower_name or "cover" in lower_name or "banner" in lower_name:
        return "feature_graphic"
    if "tablet" in lower_name:
        return "tablet"
    if "tv" in lower_name or "landscape" in lower_name:
        return "tv_screenshot"
    if "portrait" in lower_name or "phone" in lower_name:
        return "phone_portrait"
    
    # By ratio:
    w, h = img_size
    ratio = w / h
    if ratio >= 1.8:
        return "feature_graphic"
    elif ratio > 1.2:
        return "tv_screenshot"
    else:
        return "phone_portrait"


def main() -> int:
    parser = argparse.ArgumentParser(description="Convert and resize AI-generated images to exact Google Play specs.")
    parser.add_argument("input", help="Path to input image file or directory containing .jfif, .webp, .png, .jpg")
    parser.add_argument("--type", choices=list(TARGET_SIZES.keys()) + ["auto"], default="auto", help="Target Google Play asset type")
    parser.add_argument("--output-dir", default=".ai-work/play-assets/output/ready-to-upload", help="Output directory for processed images")

    args = parser.parse_args()
    input_path = Path(args.input)
    output_dir = Path(args.output_dir)

    if input_path.is_file():
        target_type = args.type
        if target_type == "auto":
            with Image.open(input_path) as img:
                target_type = auto_detect_type(input_path.name, img.size)
        process_image(input_path, output_dir / input_path.stem, target_type)
    elif input_path.is_dir():
        image_files = [f for f in input_path.iterdir() if f.suffix.lower() in (".jfif", ".webp", ".jpg", ".jpeg", ".png", ".bmp")]
        if not image_files:
            print(f"No image files found in '{input_path}'")
            return 0
        for f in image_files:
            target_type = args.type
            if target_type == "auto":
                try:
                    with Image.open(f) as img:
                        target_type = auto_detect_type(f.name, img.size)
                except Exception:
                    continue
            process_image(f, output_dir / f.stem, target_type)
    else:
        print(f"ERROR: Input path does not exist: {input_path}")
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
