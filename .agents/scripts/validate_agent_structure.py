#!/usr/bin/env python3
"""Validate the repository-level Agent loading graph."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

import yaml


REQUIRED: dict[str, set[str]] = {
    "rules": {
        "android.md",
        "architecture.md",
        "build.md",
        "execution.md",
        "figma.md",
        "i18n.md",
        "play-assets.md",
        "ui.md",
    },
    "workflows": {
        "change-architecture.md",
        "change-build.md",
        "create-project.md",
        "figma-code.md",
        "figma-process.md",
        "fix-bug.md",
        "implement-page.md",
        "localize-content.md",
        "prepare-play-assets.md",
    },
    "prompts": {
        "change-architecture.md",
        "change-build.md",
        "create-project.md",
        "figma-page.md",
        "fix-bug.md",
        "implement-page.md",
        "localize-content.md",
        "prepare-play-assets.md",
    },
}

EXTERNAL_SKILLS = {"figma", "imagegen"}
FRONTMATTER_RE = re.compile(r"^---\s*\n(.*?)\n---\s*\n", re.DOTALL)
SKILL_REF_RE = re.compile(r"\$([a-z0-9-]+)")
MARKDOWN_LINK_RE = re.compile(r"\]\(([^)#]+)(?:#[^)]+)?\)")
AGENT_PATH_RE = re.compile(r"`(\.agents/[A-Za-z0-9_./*-]+)`")
SCRIPT_REF_RE = re.compile(r"(?:\.agents/[A-Za-z0-9_./-]+/)?[A-Za-z0-9_-]+\.py")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Agent rules, workflows, prompts and skills.")
    parser.add_argument("--project-root", default=".", help="Project root.")
    return parser.parse_args()


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def validate_required_files(root: Path, errors: list[str]) -> None:
    agents = root / ".agents"
    for directory, expected in REQUIRED.items():
        actual_dir = agents / directory
        if not actual_dir.is_dir():
            errors.append(f"missing directory: {actual_dir.relative_to(root)}")
            continue
        actual = {path.name for path in actual_dir.glob("*.md")}
        for missing in sorted(expected - actual):
            errors.append(f"missing {directory} file: .agents/{directory}/{missing}")
        for extra in sorted(actual - expected):
            errors.append(f"unregistered {directory} file: .agents/{directory}/{extra}")


def validate_profile(root: Path, errors: list[str]) -> None:
    profile_path = root / ".agents/config/project-profile.yml"
    if not profile_path.is_file():
        errors.append("missing .agents/config/project-profile.yml")
        return
    profile = yaml.safe_load(read_text(profile_path)) or {}
    for key in ("project", "android", "modules", "sourceOfTruth", "validation"):
        if key not in profile:
            errors.append(f"project-profile.yml missing key: {key}")
    for raw in (profile.get("sourceOfTruth") or {}).values():
        value = str(raw)
        if "*" in value:
            if not list(root.glob(value)):
                errors.append(f"project profile glob has no matches: {value}")
        elif not (root / value).exists():
            errors.append(f"project profile source does not exist: {value}")


def validate_client_bridges(root: Path, errors: list[str]) -> None:
    for name in ("CLAUDE.md", "GEMINI.md"):
        path = root / name
        if not path.is_file():
            errors.append(f"missing client bridge: {name}")
            continue
        text = read_text(path)
        if "@./AGENTS.md" not in text or "AGENTS.md" not in text:
            errors.append(f"client bridge must import AGENTS.md: {name}")
        if ".agents/rules/" in text or ".agents/workflows/" in text:
            errors.append(f"client bridge must not duplicate routing or rules: {name}")
        if len(text.splitlines()) > 24:
            errors.append(f"client bridge must stay concise: {name}")


def validate_prompts(root: Path, errors: list[str]) -> None:
    for path in sorted((root / ".agents/prompts").glob("*.md")):
        text = read_text(path)
        if len(text.splitlines()) > 20:
            errors.append(f"prompt must stay at or below 20 lines: {path.relative_to(root)}")
        if ".agents/workflows/" not in text:
            errors.append(f"prompt does not select a workflow: {path.relative_to(root)}")
        if re.search(r"^要求：\s*$", text, re.MULTILINE) or "## 加载" in text:
            errors.append(f"prompt contains rules or loading instructions: {path.relative_to(root)}")


def validate_skills(root: Path, errors: list[str]) -> set[str]:
    names: set[str] = set()
    skills_dir = root / ".agents/skills"
    for skill_dir in sorted(path for path in skills_dir.iterdir() if path.is_dir()):
        skill_path = skill_dir / "SKILL.md"
        if not skill_path.is_file():
            errors.append(f"skill missing SKILL.md: {skill_dir.relative_to(root)}")
            continue
        text = read_text(skill_path)
        if ".agents/workflows/" in text:
            errors.append(f"skill must not load a top-level workflow: {skill_path.relative_to(root)}")
        match = FRONTMATTER_RE.match(text)
        if not match:
            errors.append(f"invalid frontmatter: {skill_path.relative_to(root)}")
            continue
        metadata = yaml.safe_load(match.group(1)) or {}
        if set(metadata) != {"name", "description"}:
            errors.append(f"frontmatter must contain only name and description: {skill_path.relative_to(root)}")
        name = str(metadata.get("name", ""))
        if name != skill_dir.name:
            errors.append(f"skill name does not match directory: {skill_path.relative_to(root)}")
        if not metadata.get("description"):
            errors.append(f"skill description is empty: {skill_path.relative_to(root)}")
        names.add(name)

        openai_path = skill_dir / "agents/openai.yaml"
        if not openai_path.is_file():
            errors.append(f"skill missing agents/openai.yaml: {skill_dir.relative_to(root)}")
        else:
            interface = (yaml.safe_load(read_text(openai_path)) or {}).get("interface") or {}
            for key in ("display_name", "short_description", "default_prompt"):
                if not interface.get(key):
                    errors.append(f"{openai_path.relative_to(root)} missing interface.{key}")
            if f"${name}" not in str(interface.get("default_prompt", "")):
                errors.append(f"default_prompt does not mention ${name}: {openai_path.relative_to(root)}")
            if name == "android-figma-workflow":
                prompt = str(interface.get("default_prompt", ""))
                if "等待确认" not in prompt or "不实现代码" not in prompt:
                    errors.append("android-figma-workflow default_prompt must stop after analysis")

        for raw in SCRIPT_REF_RE.findall(text):
            target = root / raw if raw.startswith(".agents/") else skill_dir / "scripts" / raw
            if not target.is_file():
                errors.append(f"missing script referenced by {skill_path.relative_to(root)}: {raw}")

        for link in MARKDOWN_LINK_RE.findall(text):
            if "://" in link or link.startswith("#"):
                continue
            if not (skill_dir / link).exists():
                errors.append(f"broken skill link in {skill_path.relative_to(root)}: {link}")

        references = skill_dir / "references"
        if references.is_dir():
            for reference in references.glob("*.md"):
                content = read_text(reference)
                if len(content.splitlines()) > 100 and "## 目录" not in content:
                    errors.append(f"reference over 100 lines needs a contents section: {reference.relative_to(root)}")
    return names


def validate_workflows(root: Path, skill_names: set[str], errors: list[str]) -> None:
    for path in sorted((root / ".agents/workflows").glob("*.md")):
        text = read_text(path)
        if "## 加载" not in text or "## 流程" not in text:
            errors.append(f"workflow must contain loading and process sections: {path.relative_to(root)}")
        if ".agents/rules/" not in text:
            errors.append(f"workflow does not load rules: {path.relative_to(root)}")
        for name in SKILL_REF_RE.findall(text):
            if name not in skill_names and name not in EXTERNAL_SKILLS:
                errors.append(f"workflow references unknown skill ${name}: {path.relative_to(root)}")


def validate_static_agent_paths(root: Path, errors: list[str]) -> None:
    files = [root / "AGENTS.md", root / "CLAUDE.md", root / "GEMINI.md"]
    files.extend((root / ".agents").rglob("*.md"))
    for path in files:
        if not path.is_file():
            continue
        for raw in AGENT_PATH_RE.findall(read_text(path)):
            if "*" in raw or "<" in raw:
                continue
            if not (root / raw).exists():
                errors.append(f"broken Agent path in {path.relative_to(root)}: {raw}")


def main() -> int:
    args = parse_args()
    root = Path(args.project_root).resolve()
    errors: list[str] = []

    validate_required_files(root, errors)
    validate_profile(root, errors)
    validate_client_bridges(root, errors)
    validate_prompts(root, errors)
    skill_names = validate_skills(root, errors)
    validate_workflows(root, skill_names, errors)
    validate_static_agent_paths(root, errors)

    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        print(f"agent structure validation failed: {len(errors)} error(s)")
        return 1
    print("agent structure validation passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
