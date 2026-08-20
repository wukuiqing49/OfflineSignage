#!/usr/bin/env python3
"""Run Android workflow validators from one entry point."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

try:
    import yaml
except ImportError:  # pragma: no cover - optional for portability
    yaml = None


FIELD_RE = re.compile(r"^\s*[-*]\s*(?P<key>[^：:]+)[：:]\s*(?P<value>.*)$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Android workflow validators.")
    parser.add_argument("--project-root", default=".", help="Android target project root.")
    parser.add_argument(
        "--tool-root",
        help="Workflow repository root or its .agents directory. Defaults to the location of this script.",
    )
    parser.add_argument("--scaffold-config", help="Optional project-scaffold.yml used to resolve module paths.")
    parser.add_argument("--strict-i18n", action="store_true", help="Treat missing localized strings as errors.")
    parser.add_argument("--skip-figma", action="store_true", help="Skip Figma output validation.")
    return parser.parse_args()


def run_step(name: str, command: list[str], cwd: Path) -> int:
    print(f"\n== {name} ==")
    result = subprocess.run(command, cwd=cwd, text=True)
    if result.returncode == 0:
        print(f"{name}: ok")
    else:
        print(f"{name}: failed ({result.returncode})")
    return result.returncode


def existing_args(root: Path, flag: str, paths: list[str]) -> list[str]:
    result: list[str] = []
    for raw in paths:
        if (root / raw).exists():
            result.extend([flag, raw])
    return result


def load_scaffold_modules(root: Path, tool_root: Path, config_override: str | None) -> dict[str, str]:
    defaults = {
        "app": "app",
        "featureApp": "feature/feature_app",
        "featureRes": "feature/feature_res",
    }
    config = Path(config_override).resolve() if config_override else root / ".agents/config/project-scaffold.yml"
    if not config.exists():
        config = tool_root / "config/project-scaffold.yml"
    if yaml is None or not config.exists():
        return defaults
    loaded = yaml.safe_load(config.read_text(encoding="utf-8")) or {}
    modules = loaded.get("modules") or {}
    return {
        "app": str(modules.get("app", defaults["app"])),
        "featureApp": str(modules.get("featureApp", defaults["featureApp"])),
        "featureRes": str(modules.get("featureRes", defaults["featureRes"])),
    }


def source_dirs_for(module: str) -> list[str]:
    return [f"{module}/src/main/kotlin", f"{module}/src/main/java"]


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def extract_fields(text: str) -> dict[str, str]:
    fields: dict[str, str] = {}
    for line in text.splitlines():
        match = FIELD_RE.match(line)
        if not match:
            continue
        fields.setdefault(match.group("key").strip(), match.group("value").strip().strip("`"))
    return fields


def is_yes(value: str) -> bool:
    return value.strip().startswith(("是", "true", "True", "yes", "Yes"))


def is_no(value: str) -> bool:
    return value.strip().startswith(("否", "false", "False", "no", "No"))


def find_figma_reports(root: Path) -> list[Path]:
    output = root / ".ai-work/figma/output"
    if not output.exists():
        return []
    return sorted(output.glob("page_task_*.md"))


def report_requires_pager(report: Path) -> bool:
    text = read_text(report)
    fields = extract_fields(text)
    needs_pager = fields.get("是否需要 ViewPager2 + Fragment", "")
    needs_adapter = fields.get("是否需要 FragmentStateAdapter", "")
    if is_yes(needs_pager) or is_yes(needs_adapter):
        return True
    if is_no(needs_pager):
        return False
    negative_markers = [
        "是否需要 ViewPager2 + Fragment：否",
        "是否必须使用 ViewPager2：否",
        "是否主页容器 / 主导航页：否",
    ]
    if any(marker in text for marker in negative_markers):
        return False
    positive_markers = [
        "是否需要 ViewPager2 + Fragment：是",
        "是否必须使用 ViewPager2：是",
        "是否已规划 FragmentStateAdapter：是",
        "页面类型：Tab + ViewPager2",
        "主页容器",
        "主导航页",
        "底部主导航",
        "顶部 Tab",
        "同级页面切换",
    ]
    return any(marker in text for marker in positive_markers)


def resolve_task_manifest(root: Path, report: Path) -> Path:
    output = root / ".ai-work/figma/output"
    fields = extract_fields(read_text(report))
    declared = fields.get("页面级资源清单") or fields.get("资源清单") or ""
    if declared:
        raw = declared.strip().strip("`")
        declared_path = Path(raw)
        if not declared_path.is_absolute():
            declared_path = root / declared_path
        return declared_path
    page_name = report.stem.removeprefix("page_task_")
    page_manifest = output / f"asset_manifest_{page_name}.md"
    if page_manifest.exists():
        return page_manifest
    return output / "asset_manifest.md"


def main() -> int:
    args = parse_args()
    root = Path(args.project_root).resolve()
    if args.tool_root:
        tool_root = Path(args.tool_root).resolve()
        if (tool_root / ".agents").is_dir():
            tool_root /= ".agents"
    else:
        tool_root = Path(__file__).resolve().parents[1]
    if not (tool_root / "skills").is_dir():
        raise FileNotFoundError(f"Workflow tool root does not contain skills: {tool_root}")
    py = sys.executable
    modules = load_scaffold_modules(root, tool_root, args.scaffold_config)

    src_args = existing_args(root, "--module-src", [
        *source_dirs_for(modules["app"]),
        *source_dirs_for(modules["featureApp"]),
    ])
    res_args = existing_args(root, "--module-res", [
        f"{modules['app']}/src/main/res",
        f"{modules['featureApp']}/src/main/res",
        f"{modules['featureRes']}/src/main/res",
    ])
    i18n_res_args = existing_args(root, "--res-dir", [
        f"{modules['app']}/src/main/res",
        f"{modules['featureApp']}/src/main/res",
        f"{modules['featureRes']}/src/main/res",
    ])

    steps: list[tuple[str, list[str]]] = [
        ("agent-structure", [py, str(tool_root / "scripts/validate_agent_structure.py"), "--project-root", str(tool_root.parent)]),
        ("build", [py, str(tool_root / "skills/android-build-workflow/scripts/validate_build_output.py"), "--project-root", ".", "--app-module", modules["app"]]),
        ("architecture", [py, str(tool_root / "skills/android-project-architecture-workflow/scripts/validate_architecture.py"), "--project-root", "."]),
        ("ui", [py, str(tool_root / "skills/android-ui-workflow/scripts/validate_ui_output.py"), *src_args, *res_args]),
        ("i18n", [py, str(tool_root / "skills/android-i18n-workflow/scripts/validate_i18n_resources.py"), *i18n_res_args, *(["--strict-missing"] if args.strict_i18n else [])]),
    ]

    figma_reports = find_figma_reports(root)
    if not args.skip_figma and figma_reports:
        for figma_report in figma_reports:
            figma_manifest = resolve_task_manifest(root, figma_report)
            command = [
                py,
                str(tool_root / "skills/android-figma-workflow/scripts/validate_figma_output.py"),
                *src_args,
                *res_args,
                "--asset-manifest",
                str(figma_manifest.relative_to(root)),
                "--analysis-report",
                str(figma_report.relative_to(root)),
                "--require-screen-adaptation",
                "--allow-standard-adapter",
                "--allow-raw-fragment",
            ]
            if report_requires_pager(figma_report):
                command.append("--require-pager-navigation")
            page_name = figma_report.stem.removeprefix("page_task_")
            steps.append((f"figma:{page_name}", command))

    failed = 0
    for name, command in steps:
        if run_step(name, command, root) != 0:
            failed += 1
    if failed:
        print(f"\nworkflow validation failed: {failed} step(s)")
        return 1
    print("\nworkflow validation passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
