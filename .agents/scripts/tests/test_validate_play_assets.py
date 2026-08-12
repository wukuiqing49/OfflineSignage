from __future__ import annotations

import importlib.util
import struct
import sys
import tempfile
import unittest
import zlib
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "validate_play_assets.py"
SPEC = importlib.util.spec_from_file_location("validate_play_assets", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def png_chunk(chunk_type: bytes, data: bytes) -> bytes:
    checksum = zlib.crc32(chunk_type)
    checksum = zlib.crc32(data, checksum)
    return struct.pack(">I", len(data)) + chunk_type + data + struct.pack(">I", checksum & 0xFFFFFFFF)


def write_png(path: Path, width: int, height: int, transparent: bool = False) -> None:
    color_type = 6 if transparent else 2
    pixel = b"\x10\x20\x30\x00" if transparent else b"\x10\x20\x30"
    rows = b"".join(b"\x00" + pixel * width for _ in range(height))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, color_type, 0, 0, 0)
    content = b"\x89PNG\r\n\x1a\n"
    content += png_chunk(b"IHDR", ihdr)
    content += png_chunk(b"IDAT", zlib.compress(rows, level=1))
    content += png_chunk(b"IEND", b"")
    path.write_bytes(content)


class ValidatePlayAssetsTest(unittest.TestCase):
    def test_accepts_standard_opaque_icon(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "icon.png"
            write_png(path, 1024, 1024)
            result = MODULE.validate_asset("icon", path)
            self.assertEqual([], result.errors)

    def test_rejects_wrong_icon_size(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "icon.png"
            write_png(path, 512, 512)
            result = MODULE.validate_asset("icon", path)
            self.assertTrue(any("1024x1024" in error for error in result.errors))

    def test_rejects_transparent_feature_graphic(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "feature.png"
            write_png(path, 1024, 500, transparent=True)
            result = MODULE.validate_asset("feature-graphic", path)
            self.assertTrue(any("transparent pixels" in error for error in result.errors))

    def test_warns_for_nonpreferred_portrait_screenshot(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "screenshot.png"
            write_png(path, 1080, 1920)
            result = MODULE.validate_asset("screenshot", path)
            self.assertEqual([], result.errors)
            self.assertTrue(any("1240x2208" in warning for warning in result.warnings))


if __name__ == "__main__":
    unittest.main()
