#!/usr/bin/env python3
"""Validate Android string/plurals locale coverage and placeholder consistency."""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


PLACEHOLDER_RE = re.compile(r"%(?!%)(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z]")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Android i18n resources.")
    parser.add_argument("--res-dir", action="append", default=[], help="res directory to scan.")
    parser.add_argument("--strict-missing", action="store_true", help="Treat missing localized keys as errors.")
    return parser.parse_args()


def read_xml(path: Path) -> ET.Element | None:
    try:
        return ET.fromstring(path.read_text(encoding="utf-8", errors="replace"))
    except ET.ParseError as exc:
        print(f"ERROR: {path}: XML parse failed: {exc}")
        return None


def placeholders(value: str) -> tuple[str, ...]:
    return tuple(sorted(PLACEHOLDER_RE.findall(value or "")))


def text_of(element: ET.Element) -> str:
    return "".join(element.itertext())


def collect_values(values_dir: Path) -> dict[str, tuple[str, tuple[str, ...]]]:
    result: dict[str, tuple[str, tuple[str, ...]]] = {}
    for path in values_dir.glob("*.xml"):
        root = read_xml(path)
        if root is None:
            continue
        for item in root:
            name = item.attrib.get("name")
            if not name:
                continue
            if item.tag == "string":
                if item.attrib.get("translatable") == "false":
                    continue
                result[f"string/{name}"] = (text_of(item), placeholders(text_of(item)))
            elif item.tag == "plurals":
                quantities = []
                ph = []
                for quantity in item.findall("item"):
                    q_name = quantity.attrib.get("quantity", "")
                    quantities.append(q_name)
                    ph.extend(placeholders(text_of(quantity)))
                result[f"plurals/{name}"] = (",".join(sorted(quantities)), tuple(sorted(ph)))
    return result


def is_locale_values_dir(path: Path) -> bool:
    qualifier = path.name.removeprefix("values-")
    if qualifier.startswith("b+"):
        return True
    first = qualifier.split("-")[0]
    return bool(re.fullmatch(r"[a-z]{2,3}", first))


def validate_res_dir(res_dir: Path, errors: list[str], warnings: list[str], strict_missing: bool) -> None:
    default_dir = res_dir / "values"
    if not default_dir.is_dir():
        warnings.append(f"WARN: {res_dir}: missing default values directory.")
        return
    default_values = collect_values(default_dir)
    if not default_values:
        warnings.append(f"WARN: {default_dir}: no string/plurals resources found.")
        return
    locale_dirs = sorted(path for path in res_dir.glob("values-*") if path.is_dir() and is_locale_values_dir(path))
    for locale_dir in locale_dirs:
        localized = collect_values(locale_dir)
        missing = sorted(set(default_values) - set(localized))
        extra = sorted(set(localized) - set(default_values))
        if missing:
            target = errors if strict_missing else warnings
            prefix = "ERROR" if strict_missing else "WARN"
            target.append(f"{prefix}: {locale_dir}: missing {len(missing)} localized key(s): {', '.join(missing[:10])}")
        if extra:
            warnings.append(f"WARN: {locale_dir}: extra localized key(s) not in default values: {', '.join(extra[:10])}")
        for key in sorted(set(default_values) & set(localized)):
            default_meta = default_values[key]
            locale_meta = localized[key]
            if key.startswith("plurals/") and default_meta[0] != locale_meta[0]:
                errors.append(f"ERROR: {locale_dir}: plurals quantities mismatch for {key}: {locale_meta[0]} != {default_meta[0]}")
            if default_meta[1] != locale_meta[1]:
                errors.append(f"ERROR: {locale_dir}: placeholder mismatch for {key}: {locale_meta[1]} != {default_meta[1]}")


def validate_raw_html(res_dir: Path, errors: list[str], warnings: list[str]) -> None:
    raw_dirs = [path for path in res_dir.glob("raw*") if path.is_dir()]
    html_files = [path for raw_dir in raw_dirs for path in raw_dir.glob("*.html")]
    if not html_files:
        return
    node = shutil.which("node")
    script_re = re.compile(r"<script[^>]*>(?P<script>[\s\S]*?)</script>", re.I)
    for path in html_files:
        text = path.read_text(encoding="utf-8", errors="replace")
        scripts = [match.group("script").strip() for match in script_re.finditer(text) if match.group("script").strip()]
        if not scripts:
            continue
        if node is None:
            warnings.append(f"WARN: {path}: script tag found but Node.js is unavailable; raw HTML script syntax was not checked.")
            continue
        for index, script in enumerate(scripts, start=1):
            result = subprocess.run(
                [node, "--check", "-"],
                input=script,
                capture_output=True,
                text=True,
                encoding="utf-8",
            )
            if result.returncode != 0:
                errors.append(f"ERROR: {path}: script #{index} syntax check failed: {result.stderr.strip()}")


def main() -> int:
    args = parse_args()
    errors: list[str] = []
    warnings: list[str] = []
    res_dirs = [Path(path) for path in args.res_dir]
    if not res_dirs:
        res_dirs = [
            Path("app/src/main/res"),
            Path("feature/feature_app/src/main/res"),
            Path("feature/feature_res/src/main/res"),
        ]
    for res_dir in res_dirs:
        if res_dir.exists():
            validate_res_dir(res_dir, errors, warnings, args.strict_missing)
            validate_raw_html(res_dir, errors, warnings)
    for item in warnings:
        print(item)
    for item in errors:
        print(item)
    if errors:
        print(f"i18n validation failed: {len(errors)} error(s), {len(warnings)} warning(s)")
        return 1
    print(f"i18n validation passed: {len(warnings)} warning(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
