#!/usr/bin/env python3
"""Export standalone downstream prompts from validated Google Play asset briefs."""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path

import inspect_keyword_inputs
import init_workspace
import validate_listing_briefs
import validate_video_brief


ASSET_TYPES = {"feature-graphic", "screenshots", "video"}
MANAGED_ASSET_ID_RE = re.compile(
    r"\b(?:SHOT|CLIP|BRAND|ICON|LOGO|ASSET|RECORDING)-[A-Za-z0-9_-]+\b",
    re.IGNORECASE,
)
PLACEHOLDER_PATTERNS = [
    re.compile(r"\[[^\]\r\n]+\]"),
    re.compile(r"\{\{[^}\r\n]+\}\}"),
    re.compile(r"\$\{[^}\r\n]+\}"),
    re.compile(r"<[A-Za-z][^>\r\n]*>"),
    re.compile(r"\b(?:TBD|TODO|TO_FILL|PLACEHOLDER)\b", re.IGNORECASE),
]
UI_PROHIBITION_RE = re.compile(
    r"(?:不要|禁止|never)\s*(?:生成|重绘|generate|redraw)",
    re.IGNORECASE,
)
GENERATED_MARKER = "- Generation: Derived from the validated project brief"
DEVICE_VARIANT_RE = re.compile(r"([A-Za-z][A-Za-z0-9 -]*?)\s*=\s*(\d+x\d+)")
CANVAS_DECLARATION_RE = re.compile(r"(\bCanvas:\s+exactly\s+)\d+x\d+(\s+pixels\b)", re.IGNORECASE)
SUPPORTED_OUTPUT_FORMAT_RE = re.compile(r"\b(?:PNG|JPEG)\b", re.IGNORECASE)


@dataclass(frozen=True)
class PromptArtifact:
    output_path: Path
    source_brief: Path
    title: str
    asset_type: str
    prompt: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export standalone prompt files from listing briefs.")
    parser.add_argument("--project-root", default=".", help="Android project root.")
    parser.add_argument(
        "--asset-types",
        default="feature-graphic,screenshots,video",
        help="Comma-separated branches: feature-graphic,screenshots,video.",
    )
    parser.add_argument("--feature-brief", help="Override FEATURE_GRAPHIC_BRIEF.md path.")
    parser.add_argument("--screenshots-brief", help="Override SCREENSHOT_BRIEF.md path.")
    parser.add_argument("--video-brief", help="Override VIDEO_BRIEF.md path.")
    parser.add_argument("--strategy", help="Override PLAY_ASSET_STRATEGY.md path.")
    parser.add_argument("--keyword-input-dir", help="Override keyword-tool CSV/XLSX directory.")
    parser.add_argument("--output-root", help="Override .ai-work/play-assets/output.")
    parser.add_argument("--check", action="store_true", help="Check generated files without writing.")
    parser.add_argument(
        "--prune-stale",
        action="store_true",
        help="Remove stale generated screenshot Prompt files; preserve unrecognized files.",
    )
    return parser.parse_args()


def numbered_section(text: str, name: str) -> str:
    match = re.search(rf"^##\s+\d+\.\s+{re.escape(name)}\s*$", text, re.MULTILINE)
    if not match:
        return ""
    next_section = re.search(r"^##\s+\d+\.\s+", text[match.end():], re.MULTILINE)
    end = match.end() + next_section.start() if next_section else len(text)
    return text[match.end():end]


def subsection(text: str, name: str) -> str:
    match = re.search(rf"^####\s+{re.escape(name)}\s*$", text, re.MULTILINE)
    if not match:
        return ""
    next_heading = re.search(r"^#{1,4}\s+", text[match.end():], re.MULTILINE)
    end = match.end() + next_heading.start() if next_heading else len(text)
    return text[match.end():end]


def prompt_content(section: str) -> str:
    fenced = re.search(r"```(?:text|markdown)?[ \t]*\r?\n(.*?)\r?\n```", section, re.DOTALL)
    return (fenced.group(1) if fenced else section).strip()


def field(text: str, name: str) -> str:
    match = re.search(rf"^-\s+{re.escape(name)}:[ \t]*(.*?)[ \t]*$", text, re.MULTILINE)
    return match.group(1).strip() if match else ""


def slug(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", value.casefold()).strip("-")


def device_variants(summary: str, fallback_device: str) -> list[tuple[str, str | None]]:
    raw = field(summary, "Device Sets")
    if raw:
        variants = [(match.group(1).strip(), match.group(2)) for match in DEVICE_VARIANT_RE.finditer(raw)]
        remainder = DEVICE_VARIANT_RE.sub("", raw).strip(" ,;|")
        if not variants or remainder:
            raise ValueError(
                "Device Sets must use comma-separated NAME=WIDTHxHEIGHT entries, "
                f"got: {raw}"
            )
        seen_names: set[str] = set()
        seen_paths: set[str] = set()
        for name, size in variants:
            width, height = (int(value) for value in size.split("x"))
            if width <= 0 or height <= 0:
                raise ValueError(f"Device Sets contains an invalid size: {name}={size}")
            path_slug = slug(name)
            if not path_slug or path_slug in seen_names or path_slug in seen_paths:
                raise ValueError(f"Device Sets contains duplicate device names: {name}")
            seen_names.add(path_slug)
            seen_paths.add(path_slug)
        return variants
    return [(fallback_device or "Phone", None)]


def screenshot_output_format(summary: str) -> str:
    raw = field(summary, "Output Format")
    if not raw:
        return "opaque PNG or JPEG"
    if "opaque" not in raw.casefold() or not SUPPORTED_OUTPUT_FORMAT_RE.search(raw):
        raise ValueError("Output Format must describe an opaque PNG or JPEG output")
    return raw


def replace_canvas_size(prompt: str, canvas_size: str) -> str:
    return CANVAS_DECLARATION_RE.sub(rf"\g<1>{canvas_size}\g<2>", prompt)


def require_prompt_values(prompt: str, label: str, values: dict[str, str]) -> list[str]:
    errors: list[str] = []
    normalized_prompt = re.sub(r"\s+", " ", prompt).casefold()
    for value_name, value in values.items():
        if not value or value.upper() in {"N/A", "NONE"}:
            continue
        normalized_value = re.sub(r"\s+", " ", value).casefold()
        if normalized_value not in normalized_prompt:
            errors.append(f"{label} does not include {value_name}: {value}")
    return errors


def validate_prompt(prompt: str, label: str) -> list[str]:
    errors: list[str] = []
    if not prompt:
        return [f"{label} is empty or missing"]
    for pattern in PLACEHOLDER_PATTERNS:
        placeholder = pattern.search(prompt)
        if placeholder:
            errors.append(f"{label} contains unresolved placeholder: {placeholder.group(0)}")
            break
    if "App UI" not in prompt or not UI_PROHIBITION_RE.search(prompt):
        errors.append(f"{label} must prohibit generating or redrawing App UI")
    if not MANAGED_ASSET_ID_RE.search(prompt):
        errors.append(f"{label} must reference at least one real Asset ID")
    return errors


def read_brief(path: Path, label: str, errors: list[str]) -> str:
    if not path.is_file():
        errors.append(f"{label} does not exist: {path}")
        return ""
    return path.read_text(encoding="utf-8", errors="replace")


def render(artifact: PromptArtifact) -> str:
    return (
        f"# {artifact.title}\n\n"
        f"- Source Brief: `{artifact.source_brief.name}`\n"
        f"- Asset Type: {artifact.asset_type}\n"
        f"{GENERATED_MARKER}\n\n"
        "## Execution Prompt\n\n"
        "```text\n"
        f"{artifact.prompt}\n"
        "```\n"
    )


def collect_feature(path: Path, output_root: Path, errors: list[str]) -> list[PromptArtifact]:
    text = read_brief(path, "feature graphic brief", errors)
    if not text:
        return []
    prompt = prompt_content(numbered_section(text, "Final Image Prompt"))
    errors.extend(validate_prompt(prompt, "feature graphic Final Image Prompt"))
    summary = numbered_section(text, "Executive Summary")
    composition = numbered_section(text, "Composition")
    errors.extend(require_prompt_values(
        prompt,
        "feature graphic Final Image Prompt",
        {
            "Canvas Size": field(summary, "Canvas Size"),
            "Real UI Asset ID": field(composition, "Real UI Asset ID"),
            "App Icon Asset ID": field(composition, "App Icon Asset ID"),
        },
    ))
    return [PromptArtifact(
        output_path=output_root / "feature-graphic" / "FEATURE_GRAPHIC_PROMPT.md",
        source_brief=path,
        title="FEATURE_GRAPHIC_PROMPT",
        asset_type="Feature Graphic",
        prompt=prompt,
    )]


def collect_screenshots(path: Path, output_root: Path, errors: list[str]) -> list[PromptArtifact]:
    text = read_brief(path, "screenshots brief", errors)
    if not text:
        return []
    screenshots = numbered_section(text, "Screenshots")
    matches = list(re.finditer(r"^### Screenshot\s+(\d+)\s*$", screenshots, re.MULTILINE))
    if not matches:
        errors.append("screenshots brief has no Screenshot entries")
        return []
    summary = numbered_section(text, "Executive Summary")
    default_device = field(summary, "Device Type")
    try:
        variants = device_variants(summary, default_device)
        output_format = screenshot_output_format(summary)
    except ValueError as error:
        errors.append(f"screenshots brief: {error}")
        return []
    multi_device = len(variants) > 1 or bool(field(summary, "Device Sets"))
    artifacts: list[PromptArtifact] = []
    for index, match in enumerate(matches):
        number = int(match.group(1))
        end = matches[index + 1].start() if index + 1 < len(matches) else len(screenshots)
        body = screenshots[match.end():end]
        locale = field(body, "Locale")
        orientation = field(body, "Orientation") or field(summary, "Orientation")
        base_prompt = prompt_content(subsection(body, "Final Image Prompt"))
        for device_type, canvas_size in variants:
            prompt = re.sub(r"^Device Type:[^\r\n]+\r?\n\r?\n", "", base_prompt)
            if canvas_size:
                prompt = replace_canvas_size(prompt, canvas_size)
                width, height = (int(value) for value in canvas_size.split("x"))
                if orientation.casefold() == "landscape" and width <= height:
                    errors.append(
                        f"{device_type} Screenshot {number:02d} has landscape orientation but canvas is {canvas_size}"
                    )
                elif orientation.casefold() == "portrait" and width >= height:
                    errors.append(
                        f"{device_type} Screenshot {number:02d} has portrait orientation but canvas is {canvas_size}"
                    )
            prompt = (
                f"Device Type: {device_type}. Locale: {locale or 'en-US'}."
                + (f" Canvas: {canvas_size}." if canvas_size else "")
                + (f" Orientation: {orientation}." if orientation else "")
                + f" Output: {output_format}."
                + f"\n\n{prompt}"
            )
            label = f"{device_type} Screenshot {number:02d} Final Image Prompt"
            errors.extend(validate_prompt(prompt, label))
            errors.extend(require_prompt_values(
                prompt,
                label,
                {
                    "Device Type": device_type,
                    "Orientation": field(body, "Orientation"),
                    "Headline": field(body, "Headline"),
                    "Supporting Text": field(body, "Supporting Text"),
                    "Source Screenshot ID": field(body, "Source Screenshot ID"),
                    "Canvas": canvas_size or "",
                    "Output Format": output_format,
                },
            ))
            suffix = f"/{slug(device_type)}" if multi_device else ""
            title_prefix = f"{slug(device_type).upper()}_" if multi_device else ""
            artifacts.append(PromptArtifact(
                output_path=output_root / "screenshots" / "prompts" / suffix.strip("/") / f"SCREENSHOT_{number:02d}_PROMPT.md",
                source_brief=path,
                title=f"{title_prefix}SCREENSHOT_{number:02d}_PROMPT",
                asset_type=f"{device_type} Screenshot {number:02d}" if multi_device else f"Screenshot {number:02d}",
                prompt=prompt,
            ))
    return artifacts


def collect_video(path: Path, output_root: Path, errors: list[str]) -> list[PromptArtifact]:
    text = read_brief(path, "video brief", errors)
    if not text:
        return []
    prompt = prompt_content(numbered_section(text, "Final Execution Prompt"))
    errors.extend(validate_prompt(prompt, "video Final Execution Prompt"))
    summary = numbered_section(text, "Executive Summary")
    clip_ids = sorted(set(re.findall(r"^-\s+Recording Clip ID:[ \t]*(\S+)", text, re.MULTILINE)))
    required_values = {
        "Duration Seconds": field(summary, "Duration Seconds"),
        "Orientation": field(summary, "Orientation"),
        "Production Resolution": field(summary, "Production Resolution"),
    }
    required_values.update({f"Recording Clip ID {index}": clip_id for index, clip_id in enumerate(clip_ids, 1)})
    errors.extend(require_prompt_values(prompt, "video Final Execution Prompt", required_values))
    return [PromptArtifact(
        output_path=output_root / "video" / "VIDEO_PROMPT.md",
        source_brief=path,
        title="VIDEO_PROMPT",
        asset_type="Preview Video",
        prompt=prompt,
    )]


def collect_artifacts(
    feature_brief: Path | None,
    screenshots_brief: Path | None,
    video_brief: Path | None,
    output_root: Path,
) -> tuple[list[PromptArtifact], list[str]]:
    errors: list[str] = []
    artifacts: list[PromptArtifact] = []
    if feature_brief is not None:
        artifacts.extend(collect_feature(feature_brief, output_root, errors))
    if screenshots_brief is not None:
        artifacts.extend(collect_screenshots(screenshots_brief, output_root, errors))
    if video_brief is not None:
        artifacts.extend(collect_video(video_brief, output_root, errors))
    if not artifacts and not errors:
        errors.append("no asset branch was selected")
    return artifacts, errors


def preflight(
    strategy_brief: Path | None,
    feature_brief: Path | None,
    screenshots_brief: Path | None,
    video_brief: Path | None,
    project_root: Path,
    keyword_input_dir: Path,
) -> list[str]:
    context_errors = init_workspace.validate_project_context(project_root)
    if context_errors:
        return [f"project context validation: {error}" for error in context_errors]
    if strategy_brief is None or not strategy_brief.is_file():
        return [f"strategy brief does not exist: {strategy_brief or 'not provided'}"]
    errors, _ = validate_listing_briefs.validate(
        strategy_brief,
        feature_brief,
        screenshots_brief,
        video_brief,
        require_complete_package=all((feature_brief, screenshots_brief, video_brief)),
        project_root=project_root,
    )
    prefixed = [f"source brief validation: {error}" for error in errors]
    strategy_text = strategy_brief.read_text(encoding="utf-8", errors="replace")
    strategy_summary = validate_listing_briefs.section_body(strategy_text, "Executive Summary")
    asset_mode = validate_listing_briefs.extract_field(strategy_summary, "Asset Mode").upper()
    concept_mode = asset_mode == "CONCEPT"
    expected_strategy_status = "CONCEPT_READY" if concept_mode else "READY"
    if validate_listing_briefs.extract_field(strategy_summary, "Status") != expected_strategy_status:
        prefixed.append(
            f"source strategy Status must be {expected_strategy_status} before Prompt export"
        )
    selected_keywords = validate_listing_briefs.section_body(strategy_text, "Selected Keywords").strip()
    if not selected_keywords or selected_keywords.upper() in {"N/A", "TBD"}:
        prefixed.append("source strategy must contain selected keyword research before Prompt export")
    keyword_report = inspect_keyword_inputs.inspect_directory(
        keyword_input_dir,
        require_metadata=not concept_mode,
    )
    keyword_ready = (
        keyword_report.get("data_status") == "ready"
        if concept_mode
        else keyword_report.get("status") == "ready"
    )
    if not keyword_ready:
        prefixed.append(
            "keyword-tool input must be ready before Prompt export: "
            f"{keyword_report.get('status', 'unknown')} ({keyword_input_dir})"
        )

    for path, label in (
        (feature_brief, "feature graphic"),
        (screenshots_brief, "screenshots"),
        (video_brief, "video"),
    ):
        if path is None or not path.is_file():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        summary = validate_listing_briefs.section_body(text, "Executive Summary")
        expected_branch_status = "READY_FOR_CONCEPT" if concept_mode else "READY_FOR_PRODUCTION"
        if validate_listing_briefs.extract_field(summary, "Status") != expected_branch_status:
            prefixed.append(f"source {label} Status must be {expected_branch_status} before Prompt export")
    if video_brief is not None and video_brief.is_file():
        video_errors, _ = validate_video_brief.validate(video_brief)
        prefixed.extend(f"source video validation: {error}" for error in video_errors)
    return prefixed


def stale_screenshot_prompts(artifacts: list[PromptArtifact], output_root: Path) -> list[Path]:
    prompt_dir = output_root / "screenshots" / "prompts"
    if not prompt_dir.is_dir():
        return []
    expected = {artifact.output_path.resolve() for artifact in artifacts}
    return sorted(
        path for path in prompt_dir.rglob("SCREENSHOT_*_PROMPT.md")
        if path.resolve() not in expected
    )


def process(
    feature_brief: Path | None,
    screenshots_brief: Path | None,
    video_brief: Path | None,
    output_root: Path,
    check: bool = False,
    strategy_brief: Path | None = None,
    project_root: Path | None = None,
    keyword_input_dir: Path | None = None,
    prune_stale: bool = False,
) -> tuple[list[str], list[str], list[Path]]:
    source_root = (project_root or (strategy_brief.parent if strategy_brief else output_root)).resolve()
    keyword_dir = keyword_input_dir or source_root / ".ai-work" / "play-assets" / "input" / "keywords"
    source_errors = preflight(
        strategy_brief,
        feature_brief,
        screenshots_brief,
        video_brief,
        source_root,
        keyword_dir,
    )
    if source_errors:
        return source_errors, [], []
    artifacts, errors = collect_artifacts(feature_brief, screenshots_brief, video_brief, output_root)
    warnings: list[str] = []
    written: list[Path] = []
    if errors:
        return errors, warnings, written

    stale = stale_screenshot_prompts(artifacts, output_root) if screenshots_brief is not None else []
    if check:
        for artifact in artifacts:
            expected = render(artifact)
            if not artifact.output_path.is_file():
                errors.append(f"prompt output is missing: {artifact.output_path}")
            elif artifact.output_path.read_text(encoding="utf-8", errors="replace") != expected:
                errors.append(f"prompt output differs from its source brief: {artifact.output_path}")
        errors.extend(f"stale screenshot prompt output exists: {path}" for path in stale)
        return errors, warnings, written

    for artifact in artifacts:
        artifact.output_path.parent.mkdir(parents=True, exist_ok=True)
        expected = render(artifact)
        if artifact.output_path.is_file() and artifact.output_path.read_text(encoding="utf-8") == expected:
            continue
        temporary = artifact.output_path.with_suffix(artifact.output_path.suffix + ".tmp")
        temporary.write_text(expected, encoding="utf-8", newline="\n")
        temporary.replace(artifact.output_path)
        written.append(artifact.output_path)
    for path in stale:
        if prune_stale and GENERATED_MARKER in path.read_text(encoding="utf-8", errors="replace"):
            path.unlink()
            warnings.append(f"stale generated screenshot prompt output was removed: {path}")
        else:
            warnings.append(f"stale screenshot prompt output was preserved: {path}")
    return errors, warnings, written


def main() -> int:
    args = parse_args()
    project_root = Path(args.project_root).resolve()
    workspace_output = project_root / ".ai-work" / "play-assets" / "output"
    output_root = Path(args.output_root).resolve() if args.output_root else workspace_output
    keyword_input_dir = (
        Path(args.keyword_input_dir).resolve()
        if args.keyword_input_dir
        else project_root / ".ai-work" / "play-assets" / "input" / "keywords"
    )
    selected = {item.strip() for item in args.asset_types.split(",") if item.strip()}
    unknown = sorted(selected - ASSET_TYPES)
    if unknown:
        print(f"ERROR: unsupported asset types: {', '.join(unknown)}")
        return 1

    def brief_path(override: str | None, relative: str) -> Path:
        return Path(override).resolve() if override else workspace_output / relative

    strategy = brief_path(args.strategy, "strategy/PLAY_ASSET_STRATEGY.md")
    feature = brief_path(args.feature_brief, "feature-graphic/FEATURE_GRAPHIC_BRIEF.md") if "feature-graphic" in selected else None
    screenshots = brief_path(args.screenshots_brief, "screenshots/SCREENSHOT_BRIEF.md") if "screenshots" in selected else None
    video = brief_path(args.video_brief, "video/VIDEO_BRIEF.md") if "video" in selected else None
    errors, warnings, written = process(
        feature,
        screenshots,
        video,
        output_root,
        args.check,
        strategy_brief=strategy,
        project_root=project_root,
        keyword_input_dir=keyword_input_dir,
        prune_stale=args.prune_stale,
    )
    for warning in warnings:
        print(f"WARN: {warning}")
    for error in errors:
        print(f"ERROR: {error}")
    if errors:
        print(f"prompt export {'check ' if args.check else ''}failed: {len(errors)} error(s)")
        return 1
    action = "checked" if args.check else "exported"
    print(f"prompt files {action}: {len(written) if not args.check else 'all'}")
    for path in written:
        print(f"WROTE: {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
