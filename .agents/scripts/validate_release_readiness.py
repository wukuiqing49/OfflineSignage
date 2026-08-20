#!/usr/bin/env python3
"""Validate release-facing locale packaging and Android recovery configuration."""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
SUPPORTED_LOCALES = ("en", "zh-rCN", "de", "es", "fr", "ja", "ko", "pt")
LOCALE_CONFIG_TO_RESOURCE = {"zh-CN": "zh-rCN"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Android release readiness contracts.")
    parser.add_argument("--project-root", default=".", help="Android project root.")
    parser.add_argument(
        "--apk",
        default="app/build/outputs/apk/debug/app-debug.apk",
        help="APK to inspect for packaged locales.",
    )
    parser.add_argument("--require-apk", action="store_true", help="Fail when the APK is missing.")
    return parser.parse_args()


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def find_aapt2() -> Path | None:
    candidates = []
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        value = os.environ.get(key)
        if value:
            candidates.append(Path(value))
    candidates.extend(
        [
            Path.home() / "AppData/Local/Android/Sdk",
            Path.home() / "Android/Sdk",
        ]
    )
    for sdk in candidates:
        build_tools = sdk / "build-tools"
        if not build_tools.is_dir():
            continue
        found = sorted(build_tools.glob("*/aapt2*"))
        if found:
            return found[-1]
    return Path(shutil.which("aapt2")) if shutil.which("aapt2") else None


def check_android_config(root: Path, errors: list[str]) -> None:
    build_file = root / "app/build.gradle"
    build_text = read(build_file)
    match = re.search(r"resourceConfigurations\s*\+=\s*\[(?P<items>[^]]+)\]", build_text)
    configured = set(re.findall(r"['\"]([^'\"]+)['\"]", match.group("items"))) if match else set()
    missing_from_build = set(SUPPORTED_LOCALES) - configured
    if missing_from_build:
        errors.append(f"app/build.gradle omits packaged locales: {sorted(missing_from_build)}")

    locale_file = root / "app/src/main/res/xml/locales_config.xml"
    locale_root = ET.fromstring(read(locale_file))
    declared = {
        LOCALE_CONFIG_TO_RESOURCE.get(node.attrib.get(ANDROID_NS + "name", ""), node.attrib.get(ANDROID_NS + "name", ""))
        for node in locale_root.findall("locale")
    }
    missing_from_config = set(SUPPORTED_LOCALES) - declared
    if missing_from_config:
        errors.append(f"locales_config.xml omits locales: {sorted(missing_from_config)}")

    manifest = read(root / "app/src/main/AndroidManifest.xml")
    if 'android:allowBackup="false"' not in manifest:
        errors.append("AndroidManifest.xml must keep android:allowBackup=\"false\"")
    if "android:dataExtractionRules=\"@xml/data_extraction_rules\"" not in manifest:
        errors.append("AndroidManifest.xml must declare dataExtractionRules")


def check_apk_locales(apk: Path, errors: list[str], warnings: list[str]) -> None:
    if not apk.is_file():
        warnings.append(f"APK not found, skipped packaged-locale inspection: {apk}")
        return
    aapt2 = find_aapt2()
    if aapt2 is None:
        warnings.append("aapt2 not found, skipped packaged-locale inspection")
        return
    result = subprocess.run(
        [str(aapt2), "dump", "resources", str(apk)],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if result.returncode != 0:
        errors.append(f"aapt2 failed to inspect APK: {result.stderr.strip()}")
        return
    dump = result.stdout
    missing = [locale for locale in SUPPORTED_LOCALES if not re.search(rf"\({re.escape(locale)}\)", dump)]
    if missing:
        errors.append(f"APK is missing packaged locales: {missing}")


def main() -> int:
    args = parse_args()
    root = Path(args.project_root).resolve()
    apk = (root / args.apk).resolve()
    errors: list[str] = []
    warnings: list[str] = []
    check_android_config(root, errors)
    if args.require_apk and not apk.is_file():
        errors.append(f"required APK does not exist: {apk}")
    else:
        check_apk_locales(apk, errors, warnings)
    for warning in warnings:
        print(f"WARN: {warning}")
    for error in errors:
        print(f"ERROR: {error}")
    if errors:
        print(f"release readiness failed: {len(errors)} error(s), {len(warnings)} warning(s)")
        return 1
    print(f"release readiness passed: {len(warnings)} warning(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
