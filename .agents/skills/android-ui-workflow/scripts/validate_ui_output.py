#!/usr/bin/env python3
"""Validate common Android UI workflow gates."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


TEXTVIEW_RE = re.compile(r"<TextView\b[\s\S]*?(?=/?>)", re.I)
FIXED_DP_RE = re.compile(r'android:layout_(width|height)="(?:[1-9]\d*(?:\.\d+)?|0\.\d*[1-9]\d*)dp"', re.I)
SCROLLABLE_RE = re.compile(r"<(?:androidx\.[\w.]+\.|android\.[\w.]+\.|)(RecyclerView|ViewPager2|ScrollView|NestedScrollView|HorizontalScrollView)\b[\s\S]*?(?=/?>)", re.I)
VIEWPAGER_RE = re.compile(r"(ViewPager2|androidx\.viewpager2\.widget\.ViewPager2)")
FRAGMENT_STATE_ADAPTER_RE = re.compile(r"FragmentStateAdapter")
SYSTEM_BAR_RE = re.compile(r"(fitsSystemWindows|SYSTEM_UI_FLAG|hide\(|systemBars\(|decorFitsSystemWindows|setStatusBarColor|setNavigationBarColor)", re.I)
DIALOG_RE = re.compile(r"(Dialog\(|PopupWindow|BottomSheet|XPopup|FullScreenPopup|AttachPopup)", re.I)
WINDOW_INSETS_RE = re.compile(r"(WindowInsets|WindowInsetsCompat|setOnApplyWindowInsetsListener|enableEdgeToEdge)", re.I)
BOTTOM_INSET_RE = re.compile(r"(navigationBars\(\)|systemBars\(\))", re.I)
BOTTOM_PADDING_RE = re.compile(r"(updatePadding\s*\([^)]*bottom|setPadding\s*\(|paddingBottom)", re.I | re.S)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Android UI workflow output.")
    parser.add_argument("--module-src", action="append", default=[], help="Source directory to scan.")
    parser.add_argument("--module-res", action="append", default=[], help="Resource directory to scan.")
    parser.add_argument("--require-pager-navigation", action="store_true", help="Require ViewPager2 + FragmentStateAdapter.")
    return parser.parse_args()


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def iter_files(paths: list[str], suffixes: tuple[str, ...]) -> list[Path]:
    result: list[Path] = []
    for raw in paths:
        root = Path(raw)
        if root.is_file() and root.suffix.lower() in suffixes:
            result.append(root)
        elif root.is_dir():
            result.extend(path for path in root.rglob("*") if path.is_file() and path.suffix.lower() in suffixes)
    return result


def issue(items: list[str], level: str, path: Path | str, message: str) -> None:
    items.append(f"{level}: {path}: {message}")


def validate_xml(files: list[Path], errors: list[str], warnings: list[str]) -> None:
    for path in files:
        text = read_text(path)
        for match in TEXTVIEW_RE.finditer(text):
            if FIXED_DP_RE.search(match.group(0)):
                issue(errors, "ERROR", path, "TextView has fixed dp width/height risk.")
        for match in SCROLLABLE_RE.finditer(text):
            tag = match.group(0)
            if 'android:scrollbars="none"' not in tag:
                issue(warnings, "WARN", path, "Scrollable view should usually set android:scrollbars=\"none\".")
            if "RecyclerView" in tag and 'android:clipToPadding="false"' not in tag:
                issue(warnings, "WARN", path, "RecyclerView may need clipToPadding=false when used with bottom bars.")
        if "fitsSystemWindows" in text:
            issue(warnings, "WARN", path, "fitsSystemWindows found; verify this is a local compatibility decision.")


def validate_code(files: list[Path], errors: list[str], warnings: list[str]) -> None:
    all_text = "\n".join(read_text(path) for path in files)
    if DIALOG_RE.search(all_text) and not WINDOW_INSETS_RE.search(all_text):
        issue(warnings, "WARN", "source", "Dialog/Popup usage found without obvious WindowInsets handling.")
    bottom_inset_files: list[Path] = []
    for path in files:
        text = read_text(path)
        if SYSTEM_BAR_RE.search(text) and not WINDOW_INSETS_RE.search(text):
            issue(warnings, "WARN", path, "system bar API found; verify Edge-to-edge/Insets strategy is documented.")
        if "PopupWindow" in text and "XPopup" not in all_text:
            issue(warnings, "WARN", path, "PopupWindow found; verify project wrapper or XPopup cannot be reused.")
        if BOTTOM_INSET_RE.search(text) and BOTTOM_PADDING_RE.search(text):
            bottom_inset_files.append(path)
    if len(bottom_inset_files) > 1:
        joined = ", ".join(str(path) for path in bottom_inset_files)
        issue(
            warnings,
            "WARN",
            "source",
            "multiple files appear to consume bottom system insets; verify a single Insets owner contract. Files: " + joined,
        )


def validate_pager(files: list[Path], xml_files: list[Path], require: bool, errors: list[str], warnings: list[str]) -> None:
    text = "\n".join(read_text(path) for path in [*files, *xml_files])
    has_pager = bool(VIEWPAGER_RE.search(text))
    has_adapter = bool(FRAGMENT_STATE_ADAPTER_RE.search(text))
    if require:
        if not has_pager:
            issue(errors, "ERROR", "source/res", "required pager navigation is missing ViewPager2.")
        if not has_adapter:
            issue(errors, "ERROR", "source/res", "required pager navigation is missing FragmentStateAdapter.")
    elif has_pager and not has_adapter:
        issue(warnings, "WARN", "source/res", "ViewPager2 found without FragmentStateAdapter; verify this is not a Tab page.")


def main() -> int:
    args = parse_args()
    errors: list[str] = []
    warnings: list[str] = []
    xml_files = iter_files(args.module_res, (".xml",))
    src_files = iter_files(args.module_src, (".kt", ".java"))

    validate_xml(xml_files, errors, warnings)
    validate_code(src_files, errors, warnings)
    validate_pager(src_files, xml_files, args.require_pager_navigation, errors, warnings)

    for item in warnings:
        print(item)
    for item in errors:
        print(item)
    if errors:
        print(f"ui validation failed: {len(errors)} error(s), {len(warnings)} warning(s)")
        return 1
    print(f"ui validation passed: {len(warnings)} warning(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
