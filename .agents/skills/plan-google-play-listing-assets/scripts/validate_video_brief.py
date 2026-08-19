#!/usr/bin/env python3
"""Validate structure, timing, evidence gates, and policy markers in VIDEO_BRIEF.md."""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path


REQUIRED_SECTIONS = [
    "Executive Summary", "Product Analysis", "Verified Product Facts", "ASO / SEO / GEO Positioning",
    "Video Positioning", "Target Audience", "Video Type", "Orientation", "Production Resolution", "Duration",
    "Core Marketing Message", "Storyboard", "Required Screen Recordings", "Demo Data", "Text Overlay",
    "Visual Style", "Transition", "Audio", "App Icon Usage", "Google Play Badge Usage", "Localization",
    "Google Play Compliance Check", "Required Assets", "Final Execution Prompt",
]
SCENE_FIELDS = [
    "Purpose", "Real App Screen", "Starting State", "User Action", "Visible Result", "Demo Data", "Text Overlay",
    "Text Position", "Visual Focus", "Camera / Crop", "Transition", "Positioning Relationship",
    "Product Feature Evidence", "Recording Clip ID",
]
SCENE_RE = re.compile(r"^### Scene\s+(\d+)\s*\|\s*(\d{2}):(\d{2})-(\d{2}):(\d{2})\s*$", re.MULTILINE)
SECTION_RE = re.compile(r"^##\s+\d+\.\s+(.+?)\s*$", re.MULTILINE)
FIELD_RE = re.compile(r"^-\s+([^:\r\n]+):[ \t]*(.*?)[ \t]*$", re.MULTILINE)
BANNED_MARKETING = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in (r"\bbest app\b", r"\btop rated\b", r"\bmost popular\b", r"\baward winning\b", r"\bmillions? of users\b", r"(?<![A-Za-z0-9])#1\b", r"\bdownload now\b", r"\binstall now\b")
]


@dataclass(frozen=True)
class Scene:
    number: int
    start: int
    end: int
    body: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate a Google Play VIDEO_BRIEF.md file.")
    parser.add_argument("--brief", required=True, help="VIDEO_BRIEF.md path.")
    return parser.parse_args()


def timestamp_to_seconds(minutes: str, seconds: str) -> int:
    return int(minutes) * 60 + int(seconds)


def parse_scenes(text: str) -> list[Scene]:
    matches = list(SCENE_RE.finditer(text))
    scenes: list[Scene] = []
    for index, match in enumerate(matches):
        body_end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        scenes.append(Scene(
            number=int(match.group(1)),
            start=timestamp_to_seconds(match.group(2), match.group(3)),
            end=timestamp_to_seconds(match.group(4), match.group(5)),
            body=text[match.end():body_end],
        ))
    return scenes


def section_body(text: str, name: str) -> str:
    match = re.search(rf"^##\s+\d+\.\s+{re.escape(name)}\s*$", text, re.MULTILINE)
    if not match:
        return ""
    next_section = re.search(r"^##\s+\d+\.\s+", text[match.end():], re.MULTILINE)
    end = match.end() + next_section.start() if next_section else len(text)
    return text[match.end():end]


def extract_field(text: str, name: str) -> str:
    match = re.search(
        rf"^-\s+{re.escape(name)}:[ \t]*(.*?)[ \t]*$",
        text,
        re.MULTILINE,
    )
    return match.group(1).strip() if match else ""


def validate(path: Path) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []
    if not path.is_file():
        return [f"brief does not exist: {path}"], warnings
    text = path.read_text(encoding="utf-8", errors="replace")

    sections = SECTION_RE.findall(text)
    for required in REQUIRED_SECTIONS:
        if required not in sections:
            errors.append(f"missing section: {required}")

    summary = section_body(text, "Executive Summary")
    duration_raw = extract_field(summary, "Duration Seconds")
    duration = int(duration_raw) if duration_raw.isdigit() else None
    if duration is None or duration <= 0:
        errors.append("Executive Summary must contain a positive numeric Duration Seconds")
    video_type = extract_field(summary, "Video Type").casefold()
    if video_type not in {"preview", "external"}:
        errors.append("Video Type must be preview or external")
    status = extract_field(summary, "Status")
    if status not in {"DRAFT", "READY_FOR_CONCEPT", "READY_FOR_PRODUCTION", "BLOCKED_BY_MISSING_ASSETS"}:
        errors.append("Status must use a supported value")

    storyboard = section_body(text, "Storyboard")
    scenes = parse_scenes(storyboard)
    if not scenes:
        errors.append("Storyboard has no valid Scene headings")
    else:
        if scenes[0].start != 0:
            errors.append("Storyboard must start at 00:00")
        for index, scene in enumerate(scenes):
            if scene.end <= scene.start:
                errors.append(f"Scene {scene.number:02d} has invalid timing")
            if index and scene.start != scenes[index - 1].end:
                errors.append(f"Scene {scene.number:02d} does not start where the previous scene ends")
            if scene.number != index + 1:
                errors.append(f"Scene numbering must be continuous at Scene {scene.number:02d}")
            fields = {key.strip(): value.strip() for key, value in FIELD_RE.findall(scene.body)}
            for field in SCENE_FIELDS:
                if not fields.get(field):
                    errors.append(f"Scene {scene.number:02d} has empty or missing field: {field}")
            if re.search(r"\b(?:UNVERIFIED|CONTRADICTED|NOT_FOUND)\b", scene.body):
                errors.append(f"Scene {scene.number:02d} references a non-advertisable fact")
        if duration is not None and scenes[-1].end != duration:
            errors.append(f"Storyboard ends at {scenes[-1].end}s but Duration Seconds is {duration}")

    execution_prompt = section_body(text, "Final Execution Prompt")
    policy_surface = storyboard + execution_prompt
    for pattern in BANNED_MARKETING:
        if pattern.search(policy_surface):
            errors.append(f"banned marketing phrase found: {pattern.pattern}")
    compliance = section_body(text, "Google Play Compliance Check")
    for field in ("Official Sources Checked", "Checked At"):
        if not extract_field(compliance, field):
            errors.append(f"Compliance section has empty or missing field: {field}")
    if status == "READY_FOR_PRODUCTION" and ("| MISSING |" in text or "UNVERIFIED" in compliance):
        errors.append("READY_FOR_PRODUCTION cannot contain missing assets or unverified compliance")
    if status not in {"READY_FOR_CONCEPT", "READY_FOR_PRODUCTION"}:
        warnings.append(f"brief status is {status or 'unknown'}")
    return errors, warnings


def main() -> int:
    args = parse_args()
    errors, warnings = validate(Path(args.brief))
    for warning in warnings:
        print(f"WARN: {warning}")
    for error in errors:
        print(f"ERROR: {error}")
    if errors:
        print(f"video brief validation failed: {len(errors)} error(s), {len(warnings)} warning(s)")
        return 1
    print(f"video brief validation passed: {len(warnings)} warning(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
