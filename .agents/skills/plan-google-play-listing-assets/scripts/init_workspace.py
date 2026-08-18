#!/usr/bin/env python3
"""Initialize local inputs and outputs for Google Play listing asset planning."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
from pathlib import Path


PROJECT_CONTEXT_FILE = "project-context.json"


def read_properties(path: Path) -> dict[str, str]:
    if not path.is_file():
        return {}
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8-sig", errors="replace").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def discover_project_context(project_root: Path) -> dict[str, str | int]:
    root = project_root.resolve()
    properties = read_properties(root / "app-config.properties")
    settings_text = ""
    for name in ("settings.gradle", "settings.gradle.kts"):
        path = root / name
        if path.is_file():
            settings_text = path.read_text(encoding="utf-8", errors="replace")
            break
    name_match = re.search(r"rootProject\.name\s*=\s*['\"]([^'\"]+)['\"]", settings_text)
    project_name = name_match.group(1).strip() if name_match else root.name
    application_id = properties.get("applicationId", "")
    namespace = properties.get("namespace", "")
    identity_source = "\n".join((str(root), project_name, application_id, namespace))
    project_id = hashlib.sha256(identity_source.encode("utf-8")).hexdigest()[:16]
    return {
        "schema_version": 1,
        "project_id": project_id,
        "project_name": project_name,
        "application_id": application_id,
        "namespace": namespace,
        "project_root": str(root),
    }


def ensure_project_context(project_root: Path) -> Path:
    root = project_root.resolve()
    path = root / ".ai-work" / "play-assets" / PROJECT_CONTEXT_FILE
    current = discover_project_context(root)
    if path.is_file():
        try:
            existing = json.loads(path.read_text(encoding="utf-8-sig"))
        except (OSError, UnicodeError, json.JSONDecodeError) as error:
            raise ValueError(f"invalid {PROJECT_CONTEXT_FILE}: {error}") from error
        if existing.get("project_id") != current["project_id"]:
            raise ValueError(
                f"{PROJECT_CONTEXT_FILE} belongs to another project: "
                f"{existing.get('project_id', 'unknown')} != {current['project_id']}"
            )
        return path
    path.write_text(json.dumps(current, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return path


def validate_project_context(project_root: Path) -> list[str]:
    root = project_root.resolve()
    path = root / ".ai-work" / "play-assets" / PROJECT_CONTEXT_FILE
    if not path.is_file():
        return [f"missing {PROJECT_CONTEXT_FILE}; run init_workspace.py in the current project"]
    try:
        existing = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        return [f"invalid {PROJECT_CONTEXT_FILE}: {error}"]
    current = discover_project_context(root)
    if existing.get("project_id") != current["project_id"]:
        return [
            f"{PROJECT_CONTEXT_FILE} project mismatch: "
            f"{existing.get('project_id', 'unknown')} != {current['project_id']}"
        ]
    return []


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Initialize Google Play listing asset planning workspace.")
    parser.add_argument("--project-root", default=".", help="Android project root.")
    parser.add_argument(
        "--refresh-readme",
        action="store_true",
        help="Replace the keyword input README with the Skill template.",
    )
    return parser.parse_args()


def initialize(project_root: Path, refresh_readme: bool = False) -> tuple[list[Path], Path, bool]:
    workspace = project_root.resolve() / ".ai-work" / "play-assets"
    directories = [
        workspace / "input" / "keywords",
        workspace / "input" / "brand",
        workspace / "input" / "screenshots",
        workspace / "input" / "recordings",
        workspace / "output" / "strategy",
        workspace / "output" / "feature-graphic",
        workspace / "output" / "screenshots",
        workspace / "output" / "screenshots" / "prompts",
        workspace / "output" / "video",
    ]
    for directory in directories:
        directory.mkdir(parents=True, exist_ok=True)
    ensure_project_context(project_root)

    template = Path(__file__).resolve().parents[1] / "assets" / "keyword-input-README.md"
    readme = workspace / "input" / "keywords" / "README.md"
    copied = refresh_readme or not readme.exists()
    if copied:
        shutil.copyfile(template, readme)
    metadata_template = Path(__file__).resolve().parents[1] / "assets" / "keyword-research-metadata.template.json"
    metadata = workspace / "input" / "keywords" / "keyword-research-metadata.json"
    if not metadata.exists():
        shutil.copyfile(metadata_template, metadata)
    return directories, readme, copied


def main() -> int:
    args = parse_args()
    try:
        directories, readme, copied = initialize(Path(args.project_root), args.refresh_readme)
    except ValueError as error:
        print(f"ERROR: {error}")
        return 1
    for directory in directories:
        print(f"READY: {directory}")
    action = "created" if copied else "preserved"
    print(f"README {action}: {readme}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
