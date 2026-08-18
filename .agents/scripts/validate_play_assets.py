#!/usr/bin/env python3
"""Validate raster assets produced for Google Play listings."""

from __future__ import annotations

import argparse
import re
import struct
import sys
import zlib
from dataclasses import dataclass
from pathlib import Path


PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
JPEG_SOF_MARKERS = {
    0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7,
    0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF,
}


@dataclass(frozen=True)
class ImageInfo:
    format: str
    width: int
    height: int
    has_transparency: bool | None


@dataclass(frozen=True)
class ValidationResult:
    errors: list[str]
    warnings: list[str]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Google Play PNG/JPEG assets.")
    parser.add_argument(
        "--asset",
        action="append",
        default=[],
        metavar="TYPE=PATH",
        help="Asset type and path. TYPE: icon, screenshot, feature-graphic. Repeat as needed.",
    )
    parser.add_argument(
        "--screenshot-orientation",
        choices=("any", "portrait", "landscape"),
        default="any",
        help="Expected screenshot orientation. Default: any.",
    )
    parser.add_argument(
        "--screenshot-size",
        metavar="WIDTHxHEIGHT",
        help="Require an exact screenshot size, for example 1920x1080.",
    )
    return parser.parse_args()


def paeth(left: int, up: int, upper_left: int) -> int:
    estimate = left + up - upper_left
    distances = (abs(estimate - left), abs(estimate - up), abs(estimate - upper_left))
    return (left, up, upper_left)[distances.index(min(distances))]


def unfilter_png_rows(data: bytes, width: int, height: int, bytes_per_pixel: int) -> list[bytes]:
    stride = width * bytes_per_pixel
    expected = height * (stride + 1)
    if len(data) != expected:
        raise ValueError(f"unexpected PNG scanline size: expected {expected}, got {len(data)}")

    rows: list[bytes] = []
    offset = 0
    previous = bytearray(stride)
    for _ in range(height):
        filter_type = data[offset]
        raw = data[offset + 1:offset + 1 + stride]
        offset += stride + 1
        current = bytearray(stride)
        for index, value in enumerate(raw):
            left = current[index - bytes_per_pixel] if index >= bytes_per_pixel else 0
            up = previous[index]
            upper_left = previous[index - bytes_per_pixel] if index >= bytes_per_pixel else 0
            if filter_type == 0:
                decoded = value
            elif filter_type == 1:
                decoded = value + left
            elif filter_type == 2:
                decoded = value + up
            elif filter_type == 3:
                decoded = value + ((left + up) // 2)
            elif filter_type == 4:
                decoded = value + paeth(left, up, upper_left)
            else:
                raise ValueError(f"unsupported PNG filter: {filter_type}")
            current[index] = decoded & 0xFF
        rows.append(bytes(current))
        previous = current
    return rows


def parse_png(path: Path, data: bytes) -> ImageInfo:
    if not data.startswith(PNG_SIGNATURE):
        raise ValueError("invalid PNG signature")

    offset = len(PNG_SIGNATURE)
    width = height = bit_depth = color_type = interlace = None
    compressed = bytearray()
    transparency_chunk: bytes | None = None
    while offset + 12 <= len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        chunk_type = data[offset + 4:offset + 8]
        chunk_data = data[offset + 8:offset + 8 + length]
        offset += length + 12
        if chunk_type == b"IHDR":
            width, height, bit_depth, color_type, _, _, interlace = struct.unpack(">IIBBBBB", chunk_data)
        elif chunk_type == b"IDAT":
            compressed.extend(chunk_data)
        elif chunk_type == b"tRNS":
            transparency_chunk = chunk_data
        elif chunk_type == b"IEND":
            break

    if None in (width, height, bit_depth, color_type, interlace):
        raise ValueError("PNG is missing IHDR")

    has_transparency: bool | None = False
    if transparency_chunk is not None:
        has_transparency = any(value < 255 for value in transparency_chunk) or color_type in {0, 2}

    if color_type in {4, 6}:
        if bit_depth != 8 or interlace != 0:
            has_transparency = None
        else:
            channels = 2 if color_type == 4 else 4
            rows = unfilter_png_rows(zlib.decompress(bytes(compressed)), width, height, channels)
            alpha_index = channels - 1
            has_transparency = any(
                row[index] < 255
                for row in rows
                for index in range(alpha_index, len(row), channels)
            )

    return ImageInfo("PNG", width, height, has_transparency)


def parse_jpeg(path: Path, data: bytes) -> ImageInfo:
    if not data.startswith(b"\xFF\xD8"):
        raise ValueError("invalid JPEG signature")
    offset = 2
    while offset + 4 <= len(data):
        if data[offset] != 0xFF:
            offset += 1
            continue
        while offset < len(data) and data[offset] == 0xFF:
            offset += 1
        marker = data[offset]
        offset += 1
        if marker in {0xD8, 0xD9} or 0xD0 <= marker <= 0xD7:
            continue
        length = struct.unpack(">H", data[offset:offset + 2])[0]
        if marker in JPEG_SOF_MARKERS:
            height, width = struct.unpack(">HH", data[offset + 3:offset + 7])
            return ImageInfo("JPEG", width, height, False)
        offset += length
    raise ValueError("JPEG dimensions not found")


def read_image_info(path: Path) -> ImageInfo:
    data = path.read_bytes()
    if data.startswith(PNG_SIGNATURE):
        return parse_png(path, data)
    if data.startswith(b"\xFF\xD8"):
        return parse_jpeg(path, data)
    raise ValueError("only PNG and JPEG are supported")


def parse_size(raw: str | None) -> tuple[int, int] | None:
    if not raw:
        return None
    match = re.fullmatch(r"(\d+)x(\d+)", raw.strip().lower())
    if not match:
        raise ValueError(f"size must use WIDTHxHEIGHT: {raw}")
    return int(match.group(1)), int(match.group(2))


def validate_asset(
    kind: str,
    path: Path,
    screenshot_orientation: str = "any",
    screenshot_size: tuple[int, int] | None = None,
) -> ValidationResult:
    errors: list[str] = []
    warnings: list[str] = []
    normalized = kind.strip().lower().replace("_", "-")
    if normalized == "feature":
        normalized = "feature-graphic"
    if normalized not in {"icon", "screenshot", "feature-graphic"}:
        return ValidationResult([f"unknown asset type: {kind}"], [])
    if not path.is_file():
        return ValidationResult([f"asset does not exist: {path}"], [])

    try:
        info = read_image_info(path)
    except (OSError, ValueError, struct.error, zlib.error) as error:
        return ValidationResult([f"{path}: {error}"], [])

    if path.suffix.lower() not in {".png", ".jpg", ".jpeg"}:
        errors.append(f"{path}: extension must be .png, .jpg or .jpeg")
    if normalized == "icon" and (info.width, info.height) != (1024, 1024):
        errors.append(f"{path}: icon must be 1024x1024, got {info.width}x{info.height}")
    elif normalized == "feature-graphic" and (info.width, info.height) != (1024, 500):
        errors.append(f"{path}: feature graphic must be 1024x500, got {info.width}x{info.height}")
    elif normalized == "screenshot":
        if screenshot_orientation == "portrait" and info.width >= info.height:
            errors.append(f"{path}: expected a portrait screenshot, got {info.width}x{info.height}")
        elif screenshot_orientation == "landscape" and info.width <= info.height:
            errors.append(f"{path}: expected a landscape screenshot, got {info.width}x{info.height}")
        if screenshot_size and (info.width, info.height) != screenshot_size:
            errors.append(
                f"{path}: screenshot must be {screenshot_size[0]}x{screenshot_size[1]}, "
                f"got {info.width}x{info.height}"
            )

    if info.has_transparency is True:
        errors.append(f"{path}: transparent pixels are not allowed")
    elif info.has_transparency is None:
        errors.append(f"{path}: transparency could not be verified")

    return ValidationResult(errors, warnings)


def parse_asset_arg(raw: str) -> tuple[str, Path]:
    if "=" not in raw:
        raise ValueError(f"asset must use TYPE=PATH: {raw}")
    kind, value = raw.split("=", 1)
    if not kind.strip() or not value.strip():
        raise ValueError(f"asset must use TYPE=PATH: {raw}")
    return kind, Path(value)


def main() -> int:
    args = parse_args()
    if not args.asset:
        print("ERROR: provide at least one --asset TYPE=PATH")
        return 2

    errors: list[str] = []
    warnings: list[str] = []
    try:
        screenshot_size = parse_size(args.screenshot_size)
    except ValueError as error:
        print(f"ERROR: {error}")
        return 2
    for raw in args.asset:
        try:
            kind, path = parse_asset_arg(raw)
        except ValueError as error:
            errors.append(str(error))
            continue
        result = validate_asset(kind, path, args.screenshot_orientation, screenshot_size)
        errors.extend(result.errors)
        warnings.extend(result.warnings)

    for warning in warnings:
        print(f"WARN: {warning}")
    for error in errors:
        print(f"ERROR: {error}")
    if errors:
        print(f"play asset validation failed: {len(errors)} error(s), {len(warnings)} warning(s)")
        return 1
    print(f"play asset validation passed: {len(args.asset)} asset(s), {len(warnings)} warning(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
