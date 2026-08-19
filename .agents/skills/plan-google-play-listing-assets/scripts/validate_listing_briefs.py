#!/usr/bin/env python3
"""Validate shared strategy and Feature Graphic, Screenshot, and Video brief links."""

from __future__ import annotations

import argparse
import csv
import re
from pathlib import Path


SECTION_RE = re.compile(r"^##\s+\d+\.\s+(.+?)\s*$", re.MULTILINE)
FIELD_RE = re.compile(r"^-\s+([^:\r\n]+):[ \t]*(.*?)[ \t]*$", re.MULTILINE)
SCREENSHOT_RE = re.compile(r"^### Screenshot\s+(\d+)\s*$", re.MULTILINE)
VIDEO_SCENE_RE = re.compile(r"^### Scene\s+(\d+)\s*\|.*$", re.MULTILINE)
TABLE_SEPARATOR_RE = re.compile(r"^:?-{3,}:?$")
MANAGED_ASSET_ID_RE = re.compile(
    r"\b(?:SHOT|CLIP|BRAND|ICON|LOGO|ASSET|RECORDING)-[A-Za-z0-9_-]+\b",
    re.IGNORECASE,
)
BANNED_MARKETING = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in (
        r"\bbest app\b", r"\btop rated\b", r"\bmost popular\b", r"\baward winning\b",
        r"\bmillions? of users\b", r"(?<![A-Za-z0-9])#1\b", r"\bdownload now\b", r"\binstall now\b",
    )
]

STRATEGY_SECTIONS = [
    "Executive Summary", "Product Analysis", "Verified Product Facts", "ASO / SEO / GEO Positioning",
    "Selected Keywords", "Rejected Keywords", "Listing Asset Positioning", "Cross-Asset Message Map",
    "Shared Visual Style", "Demo Data System", "Localization", "Shared Prohibited Claims", "Required Assets",
    "Official Sources Checked", "Blocking Issues",
]
FEATURE_SECTIONS = [
    "Executive Summary", "Objective", "Product Positioning", "Target Audience", "Message", "Composition",
    "Visual Style", "Preview Video Cover Relationship", "Required Assets", "Prohibited Elements",
    "Compliance Check", "Final Image Prompt",
]
SCREENSHOT_SECTIONS = [
    "Executive Summary", "Screenshot Sequence Strategy", "Shared Visual Style", "Screenshots", "Localization",
    "Required Assets", "Compliance Check", "Final Production Notes",
]
SCREENSHOT_FIELDS = [
    "Purpose", "Device Type", "Locale", "Orientation", "Real App Screen", "Starting State", "Demo Data",
    "Headline", "Supporting Text", "Text Position", "Visual Focus", "Crop", "Device Frame",
    "Background Direction", "Product Feature Evidence", "Keyword Relationship", "Source Screenshot ID",
    "Required Assets",
]
SELECTED_KEYWORD_COLUMNS = ["Keyword", "Classification", "Source", "Metrics", "Product Fact", "Reason"]
ALLOWED_KEYWORD_CLASSIFICATIONS = {"SELECTED_PRIMARY", "SELECTED_SECONDARY"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Google Play listing planning briefs.")
    parser.add_argument("--strategy", required=True, help="PLAY_ASSET_STRATEGY.md path.")
    parser.add_argument("--feature-graphic", help="FEATURE_GRAPHIC_BRIEF.md path.")
    parser.add_argument("--screenshots", help="SCREENSHOT_BRIEF.md path.")
    parser.add_argument("--video", help="VIDEO_BRIEF.md path for cross-document checks.")
    parser.add_argument("--keyword-analysis", help="Override keyword-research-analysis.csv path.")
    parser.add_argument("--project-root", default=".", help="Root used to resolve asset paths.")
    parser.add_argument(
        "--require-complete-package",
        action="store_true",
        help="Require Feature Graphic, Screenshot, and Video briefs.",
    )
    return parser.parse_args()


def read(path: Path, label: str, errors: list[str]) -> str:
    if not path.is_file():
        errors.append(f"{label} does not exist: {path}")
        return ""
    return path.read_text(encoding="utf-8", errors="replace")


def section_body(text: str, name: str) -> str:
    match = re.search(rf"^##\s+\d+\.\s+{re.escape(name)}\s*$", text, re.MULTILINE)
    if not match:
        return ""
    next_section = re.search(r"^##\s+\d+\.\s+", text[match.end():], re.MULTILINE)
    end = match.end() + next_section.start() if next_section else len(text)
    return text[match.end():end]


def subsection_body(text: str, name: str) -> str:
    match = re.search(rf"^####\s+{re.escape(name)}\s*$", text, re.MULTILINE)
    if not match:
        return ""
    next_heading = re.search(r"^#{1,4}\s+", text[match.end():], re.MULTILINE)
    end = match.end() + next_heading.start() if next_heading else len(text)
    return text[match.end():end]


def extract_field(text: str, name: str) -> str:
    match = re.search(
        rf"^-\s+{re.escape(name)}:[ \t]*(.*?)[ \t]*$",
        text,
        re.MULTILINE,
    )
    return match.group(1).strip() if match else ""


def parse_markdown_table(text: str) -> list[dict[str, str]]:
    lines = [line.strip() for line in text.splitlines() if line.strip().startswith("|")]
    for index in range(len(lines) - 1):
        headers = [cell.strip() for cell in lines[index].strip("|").split("|")]
        separators = [cell.strip() for cell in lines[index + 1].strip("|").split("|")]
        if len(headers) != len(separators) or not all(TABLE_SEPARATOR_RE.fullmatch(cell) for cell in separators):
            continue
        rows: list[dict[str, str]] = []
        for line in lines[index + 2:]:
            cells = [cell.strip() for cell in line.strip("|").split("|")]
            if len(cells) != len(headers):
                break
            rows.append(dict(zip(headers, cells)))
        return rows
    return []


def resolve_asset_path(raw_path: str, strategy_path: Path, project_root: Path) -> Path | None:
    if not raw_path or raw_path.upper() in {"N/A", "MISSING", "TBD"}:
        return None
    path = Path(raw_path)
    if path.is_absolute():
        return path if path.is_file() else None
    candidates = [project_root / path, strategy_path.parent / path]
    return next((candidate for candidate in candidates if candidate.is_file()), None)


def collect_strategy_inventory(
    text: str,
    strategy_path: Path,
    project_root: Path,
    errors: list[str],
) -> tuple[set[str], dict[str, dict[str, str]]]:
    valid_claims: set[str] = set()
    claim_rows = parse_markdown_table(section_body(text, "Verified Product Facts"))
    if not claim_rows:
        errors.append("strategy Verified Product Facts must contain a claim table row")
    for row in claim_rows:
        claim_id = row.get("Claim ID", "")
        status = row.get("Status", "").upper()
        advertisable = row.get("Advertisable", "").casefold() in {"true", "yes", "1"}
        evidence = row.get("Evidence", "")
        if not claim_id:
            errors.append("strategy claim row is missing Claim ID")
            continue
        if status == "VERIFIED" and advertisable and evidence and evidence.upper() not in {"N/A", "MISSING", "TBD"}:
            valid_claims.add(claim_id)
    if not valid_claims:
        errors.append("strategy has no VERIFIED, advertisable claim with evidence")

    available_assets: dict[str, dict[str, str]] = {}
    seen_asset_ids: set[str] = set()
    asset_rows = parse_markdown_table(section_body(text, "Required Assets"))
    if not asset_rows:
        errors.append("strategy Required Assets must contain an asset table row")
    for row in asset_rows:
        asset_id = row.get("Asset ID", "")
        asset_type = row.get("Type", "")
        status = row.get("Status", "").upper()
        raw_path = row.get("Path", "")
        if not asset_id:
            errors.append("strategy asset row is missing Asset ID")
            continue
        if asset_id in seen_asset_ids:
            errors.append(f"strategy contains duplicate Asset ID: {asset_id}")
            continue
        seen_asset_ids.add(asset_id)
        resolved = resolve_asset_path(raw_path, strategy_path, project_root)
        if status not in {"READY", "PROVIDED", "VERIFIED", "AVAILABLE"}:
            errors.append(f"strategy asset {asset_id} is not ready: {status or 'empty status'}")
        elif not asset_type:
            errors.append(f"strategy asset {asset_id} has no Type")
        elif resolved is None:
            errors.append(f"strategy asset {asset_id} does not resolve to a file: {raw_path or 'empty path'}")
        else:
            available_assets[asset_id] = {**row, "Resolved Path": str(resolved)}
    return valid_claims, available_assets


def validate_claim_reference(value: str, label: str, valid_claims: set[str], errors: list[str]) -> None:
    referenced = re.findall(r"\b(?:PF|CLAIM)-[A-Za-z0-9_-]+\b", value, re.IGNORECASE)
    if not referenced:
        errors.append(f"{label} must reference a VERIFIED, advertisable Claim ID from the strategy")
        return
    for claim_id in referenced:
        if claim_id not in valid_claims:
            errors.append(f"{label} references a non-advertisable or unknown Claim ID: {claim_id}")


def validate_asset_reference(
    asset_id: str,
    label: str,
    assets: dict[str, dict[str, str]],
    expected_types: tuple[str, ...],
    errors: list[str],
    allow_na: bool = False,
) -> None:
    if allow_na and asset_id.upper() == "N/A":
        return
    asset = assets.get(asset_id)
    if not asset:
        errors.append(f"{label} does not reference an available strategy Asset ID: {asset_id or 'empty'}")
        return
    asset_type = asset.get("Type", "").casefold()
    if not any(expected in asset_type for expected in expected_types):
        errors.append(f"{label} references {asset_id} with incompatible Type: {asset.get('Type', '')}")


def validate_sections(text: str, required: list[str], label: str, errors: list[str]) -> None:
    sections = SECTION_RE.findall(text)
    for name in required:
        if name not in sections:
            errors.append(f"{label} missing section: {name}")


def validate_prompt(text: str, label: str, errors: list[str]) -> None:
    if "[Self-contained prompt" in text or "[Use SHOT" in text:
        errors.append(f"{label} still contains a template placeholder")


def validate_prompt_asset_references(
    text: str,
    label: str,
    assets: dict[str, dict[str, str]],
    required_ids: set[str],
    errors: list[str],
) -> None:
    referenced = set(MANAGED_ASSET_ID_RE.findall(text))
    if not referenced:
        errors.append(f"{label} must reference at least one real Asset ID")
        return
    for asset_id in sorted(referenced):
        if asset_id not in assets:
            errors.append(f"{label} references an unavailable or unknown Asset ID: {asset_id}")
    for asset_id in sorted(required_ids):
        if asset_id and asset_id.upper() != "N/A" and asset_id not in referenced:
            errors.append(f"{label} must include structured Asset ID: {asset_id}")


def validate_strategy(text: str, errors: list[str], warnings: list[str]) -> None:
    validate_sections(text, STRATEGY_SECTIONS, "strategy", errors)
    summary = section_body(text, "Executive Summary")
    status = extract_field(summary, "Status")
    mode = extract_field(summary, "Asset Mode").upper()
    if mode not in {"CONCEPT", "PRODUCTION"}:
        errors.append("strategy Asset Mode must be CONCEPT or PRODUCTION")
    if status not in {"DRAFT", "CONCEPT_READY", "READY", "BLOCKED"}:
        errors.append("strategy Status must be DRAFT, CONCEPT_READY, READY, or BLOCKED")
    if mode == "CONCEPT" and status == "READY":
        errors.append("CONCEPT strategy must use CONCEPT_READY, not READY")
    if mode == "PRODUCTION" and status == "CONCEPT_READY":
        errors.append("PRODUCTION strategy cannot use CONCEPT_READY")
    if not extract_field(summary, "Primary Marketing Message"):
        errors.append("strategy Primary Marketing Message is empty")
    message_map = section_body(text, "Cross-Asset Message Map")
    for asset in ("Feature Graphic", "Screenshots", "Preview Video"):
        if asset not in message_map:
            errors.append(f"strategy Cross-Asset Message Map is missing {asset}")
    sources = section_body(text, "Official Sources Checked")
    for field in ("Sources", "Checked At", "Current Policy Status"):
        if not extract_field(sources, field):
            errors.append(f"strategy Official Sources Checked is missing {field}")
    policy_status = extract_field(sources, "Current Policy Status").upper()
    if policy_status and policy_status not in {"VERIFIED", "UNVERIFIED_CURRENT_POLICY"}:
        errors.append(f"strategy Current Policy Status is invalid: {policy_status}")
    if status == "READY" and policy_status != "VERIFIED":
        errors.append("strategy READY requires Current Policy Status: VERIFIED")
    if status not in {"READY", "CONCEPT_READY"}:
        warnings.append(f"strategy status is {status or 'unknown'}")


def read_keyword_analysis(path: Path, errors: list[str]) -> dict[str, dict[str, str]]:
    if not path.is_file():
        errors.append(f"keyword analysis does not exist: {path}")
        return {}
    try:
        with path.open("r", encoding="utf-8-sig", newline="") as handle:
            rows = list(csv.DictReader(handle))
    except (OSError, UnicodeError, csv.Error) as error:
        errors.append(f"unable to read keyword analysis: {error}")
        return {}
    required = {
        "keyword", "source_file", "product_evidence", "recommendation", "intent_risk",
        "semantic_status", "semantic_reason",
        "average_monthly_searches", "competition", "competition_index",
    }
    columns = set(rows[0].keys()) if rows else set()
    missing = sorted(required - columns)
    if missing:
        errors.append(f"keyword analysis is missing columns: {', '.join(missing)}")
        return {}
    return {
        " ".join((row.get("keyword") or "").casefold().split()): {
            str(key): str(value or "").strip() for key, value in row.items()
        }
        for row in rows
        if (row.get("keyword") or "").strip()
    }


def validate_strategy_keywords(
    text: str,
    analysis_path: Path,
    valid_claims: set[str],
    errors: list[str],
) -> None:
    summary = section_body(text, "Executive Summary")
    if extract_field(summary, "Status") not in {"READY", "CONCEPT_READY"}:
        return
    analysis = read_keyword_analysis(analysis_path, errors)
    selected = parse_markdown_table(section_body(text, "Selected Keywords"))
    if not selected:
        errors.append("strategy Selected Keywords must contain a table row")
        return
    primary_count = 0
    seen: set[str] = set()
    for index, row in enumerate(selected, start=1):
        missing = [column for column in SELECTED_KEYWORD_COLUMNS if not row.get(column, "").strip()]
        if missing:
            errors.append(f"strategy selected keyword row {index} is missing: {', '.join(missing)}")
            continue
        keyword = row["Keyword"].strip()
        identity = " ".join(keyword.casefold().split())
        if identity in seen:
            errors.append(f"strategy contains duplicate selected keyword: {keyword}")
        seen.add(identity)
        classification = row["Classification"].strip().upper()
        if classification not in ALLOWED_KEYWORD_CLASSIFICATIONS:
            errors.append(f"strategy keyword {keyword} has invalid classification: {classification}")
        if classification == "SELECTED_PRIMARY":
            primary_count += 1
        source = analysis.get(identity)
        if source is None:
            errors.append(f"strategy keyword is absent from keyword analysis: {keyword}")
            continue
        if source.get("intent_risk"):
            errors.append(f"strategy keyword has unresolved intent risk: {keyword} ({source['intent_risk']})")
        if source.get("semantic_status") != "SELECTABLE":
            errors.append(
                f"strategy keyword has not passed current-project semantic review: "
                f"{keyword} ({source.get('semantic_status') or 'empty'})"
            )
        if not source.get("semantic_reason"):
            errors.append(f"strategy keyword has no semantic review reason: {keyword}")
        if source.get("source_file") not in row["Source"]:
            errors.append(f"strategy keyword {keyword} does not cite source file: {source.get('source_file', '')}")
        metrics = row["Metrics"]
        expected_metrics = [
            source.get("average_monthly_searches", ""),
            source.get("competition", ""),
            source.get("competition_index", ""),
        ]
        if not any(value and value in metrics for value in expected_metrics):
            errors.append(f"strategy keyword {keyword} does not cite a source metric")
        validate_claim_reference(row["Product Fact"], f"strategy keyword {keyword} Product Fact", valid_claims, errors)
        source_claims = set(re.findall(r"\b(?:PF|CLAIM)-[A-Za-z0-9_-]+\b", source.get("product_evidence", "")))
        selected_claims = set(re.findall(r"\b(?:PF|CLAIM)-[A-Za-z0-9_-]+\b", row["Product Fact"]))
        if source_claims and not selected_claims.issubset(source_claims):
            errors.append(f"strategy keyword {keyword} Product Fact does not match keyword analysis")
    if primary_count != 1:
        errors.append(f"strategy must select exactly one SELECTED_PRIMARY keyword; found {primary_count}")


def validate_feature(
    text: str,
    valid_claims: set[str],
    assets: dict[str, dict[str, str]],
    errors: list[str],
    warnings: list[str],
    concept_mode: bool = False,
) -> None:
    validate_sections(text, FEATURE_SECTIONS, "feature graphic", errors)
    summary = section_body(text, "Executive Summary")
    status = extract_field(summary, "Status")
    if status not in {"DRAFT", "READY_FOR_CONCEPT", "READY_FOR_PRODUCTION", "BLOCKED_BY_MISSING_ASSETS"}:
        errors.append("feature graphic Status uses an unsupported value")
    if concept_mode and status == "READY_FOR_PRODUCTION":
        errors.append("CONCEPT feature graphic cannot use READY_FOR_PRODUCTION")
    if not concept_mode and status == "READY_FOR_CONCEPT":
        errors.append("PRODUCTION feature graphic cannot use READY_FOR_CONCEPT")
    size = re.sub(r"\s+", "", extract_field(summary, "Canvas Size")).lower()
    if size not in {"1024x500", "1024×500"}:
        errors.append("feature graphic Canvas Size must be 1024x500")
    if extract_field(summary, "Strategy Reference") != "PLAY_ASSET_STRATEGY.md":
        errors.append("feature graphic must reference PLAY_ASSET_STRATEGY.md")
    message = section_body(text, "Message")
    validate_claim_reference(
        extract_field(message, "Product Feature Evidence"),
        "feature graphic Product Feature Evidence",
        valid_claims,
        errors,
    )
    composition = section_body(text, "Composition")
    real_ui_id = extract_field(composition, "Real UI Asset ID")
    icon_id = extract_field(composition, "App Icon Asset ID")
    validate_asset_reference(
        real_ui_id,
        "feature graphic Real UI Asset ID",
        assets,
        ("screenshot", "ui"),
        errors,
        allow_na=True,
    )
    validate_asset_reference(
        icon_id,
        "feature graphic App Icon Asset ID",
        assets,
        ("icon", "logo", "brand"),
        errors,
    )
    final_prompt = section_body(text, "Final Image Prompt")
    validate_prompt(final_prompt, "feature graphic Final Image Prompt", errors)
    validate_prompt_asset_references(
        final_prompt,
        "feature graphic Final Image Prompt",
        assets,
        {real_ui_id, icon_id},
        errors,
    )
    compliance = section_body(text, "Compliance Check")
    for field in ("Official Sources Checked", "Checked At"):
        if not extract_field(compliance, field):
            errors.append(f"feature graphic Compliance Check is missing {field}")
    if status == "READY_FOR_PRODUCTION" and ("UNVERIFIED" in compliance or "| MISSING |" in text):
        errors.append("feature graphic READY_FOR_PRODUCTION cannot contain missing or unverified items")
    expected_ready = "READY_FOR_CONCEPT" if concept_mode else "READY_FOR_PRODUCTION"
    if status != expected_ready:
        warnings.append(f"feature graphic status is {status or 'unknown'}")


def validate_screenshots(
    text: str,
    valid_claims: set[str],
    assets: dict[str, dict[str, str]],
    errors: list[str],
    warnings: list[str],
    concept_mode: bool = False,
) -> None:
    validate_sections(text, SCREENSHOT_SECTIONS, "screenshots", errors)
    summary = section_body(text, "Executive Summary")
    status = extract_field(summary, "Status")
    if status not in {"DRAFT", "READY_FOR_CONCEPT", "READY_FOR_PRODUCTION", "BLOCKED_BY_MISSING_ASSETS"}:
        errors.append("screenshots Status uses an unsupported value")
    if concept_mode and status == "READY_FOR_PRODUCTION":
        errors.append("CONCEPT screenshots cannot use READY_FOR_PRODUCTION")
    if not concept_mode and status == "READY_FOR_CONCEPT":
        errors.append("PRODUCTION screenshots cannot use READY_FOR_CONCEPT")
    if extract_field(summary, "Strategy Reference") != "PLAY_ASSET_STRATEGY.md":
        errors.append("screenshots must reference PLAY_ASSET_STRATEGY.md")
    count_raw = extract_field(summary, "Screenshot Count")
    count = int(count_raw) if count_raw.isdigit() else None
    if count is None or count <= 0:
        errors.append("Screenshot Count must be a positive integer")

    screenshot_section = section_body(text, "Screenshots")
    matches = list(SCREENSHOT_RE.finditer(screenshot_section))
    if not matches:
        errors.append("screenshots brief has no Screenshot entries")
    for index, match in enumerate(matches):
        number = int(match.group(1))
        body_end = matches[index + 1].start() if index + 1 < len(matches) else len(screenshot_section)
        body = screenshot_section[match.end():body_end]
        fields = {key.strip(): value.strip() for key, value in FIELD_RE.findall(body)}
        if number != index + 1:
            errors.append(f"Screenshot numbering must be continuous at {number:02d}")
        for field in SCREENSHOT_FIELDS:
            if not fields.get(field):
                errors.append(f"Screenshot {number:02d} has empty or missing field: {field}")
        source_id = fields.get("Source Screenshot ID", "")
        if source_id and source_id.upper() != "N/A" and not re.fullmatch(r"SHOT-[A-Za-z0-9_-]+", source_id):
            errors.append(f"Screenshot {number:02d} has invalid Source Screenshot ID")
        validate_claim_reference(
            fields.get("Product Feature Evidence", ""),
            f"Screenshot {number:02d} Product Feature Evidence",
            valid_claims,
            errors,
        )
        validate_asset_reference(
            source_id,
            f"Screenshot {number:02d} Source Screenshot ID",
            assets,
            ("screenshot", "ui"),
            errors,
            allow_na=concept_mode,
        )
        if re.search(r"\b(?:UNVERIFIED|CONTRADICTED|NOT_FOUND)\b", body):
            errors.append(f"Screenshot {number:02d} references a non-advertisable fact")
        final_prompt = subsection_body(body, "Final Image Prompt")
        validate_prompt(final_prompt, f"Screenshot {number:02d} Final Image Prompt", errors)
        validate_prompt_asset_references(
            final_prompt,
            f"Screenshot {number:02d} Final Image Prompt",
            assets,
            {source_id},
            errors,
        )
    if count is not None and count != len(matches):
        errors.append(f"Screenshot Count is {count} but {len(matches)} entries were found")

    compliance = section_body(text, "Compliance Check")
    for field in ("Official Sources Checked", "Checked At"):
        if not extract_field(compliance, field):
            errors.append(f"screenshots Compliance Check is missing {field}")
    if status == "READY_FOR_PRODUCTION" and ("UNVERIFIED" in compliance or "| MISSING |" in text):
        errors.append("screenshots READY_FOR_PRODUCTION cannot contain missing or unverified items")
    expected_ready = "READY_FOR_CONCEPT" if concept_mode else "READY_FOR_PRODUCTION"
    if status != expected_ready:
        warnings.append(f"screenshots status is {status or 'unknown'}")


def validate_video_links(
    text: str,
    valid_claims: set[str],
    assets: dict[str, dict[str, str]],
    errors: list[str],
    concept_mode: bool = False,
) -> None:
    summary = section_body(text, "Executive Summary")
    if extract_field(summary, "Strategy Reference") != "PLAY_ASSET_STRATEGY.md":
        errors.append("video must reference PLAY_ASSET_STRATEGY.md")
    storyboard = section_body(text, "Storyboard")
    matches = list(VIDEO_SCENE_RE.finditer(storyboard))
    if not matches:
        errors.append("video cross-document validation found no Scene entries")
    clip_ids: set[str] = set()
    for index, match in enumerate(matches):
        number = int(match.group(1))
        body_end = matches[index + 1].start() if index + 1 < len(matches) else len(storyboard)
        fields = {key.strip(): value.strip() for key, value in FIELD_RE.findall(storyboard[match.end():body_end])}
        validate_claim_reference(
            fields.get("Product Feature Evidence", ""),
            f"Scene {number:02d} Product Feature Evidence",
            valid_claims,
            errors,
        )
        clip_id = fields.get("Recording Clip ID", "")
        if clip_id:
            clip_ids.add(clip_id)
        validate_asset_reference(
            clip_id,
            f"Scene {number:02d} Recording Clip ID",
            assets,
            ("recording", "video", "clip"),
            errors,
            allow_na=concept_mode,
        )
    final_prompt = section_body(text, "Final Execution Prompt")
    validate_prompt_asset_references(
        final_prompt,
        "video Final Execution Prompt",
        assets,
        clip_ids,
        errors,
    )


def validate_banned(texts: list[tuple[str, str]], errors: list[str]) -> None:
    for label, text in texts:
        for pattern in BANNED_MARKETING:
            if pattern.search(text):
                errors.append(f"{label} contains banned marketing phrase: {pattern.pattern}")


def validate(
    strategy_path: Path,
    feature_path: Path | None = None,
    screenshots_path: Path | None = None,
    video_path: Path | None = None,
    require_complete_package: bool = False,
    project_root: Path | None = None,
    keyword_analysis_path: Path | None = None,
) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []
    if require_complete_package:
        if feature_path is None:
            errors.append("complete package requires --feature-graphic")
        if screenshots_path is None:
            errors.append("complete package requires --screenshots")
        if video_path is None:
            errors.append("complete package requires --video")

    strategy = read(strategy_path, "strategy", errors)
    feature = read(feature_path, "feature graphic", errors) if feature_path else ""
    screenshots = read(screenshots_path, "screenshots", errors) if screenshots_path else ""
    video = read(video_path, "video", errors) if video_path else ""
    valid_claims: set[str] = set()
    assets: dict[str, dict[str, str]] = {}
    concept_mode = False
    if strategy:
        validate_strategy(strategy, errors, warnings)
        strategy_summary = section_body(strategy, "Executive Summary")
        concept_mode = extract_field(strategy_summary, "Asset Mode").upper() == "CONCEPT"
        valid_claims, assets = collect_strategy_inventory(
            strategy,
            strategy_path,
            (project_root or strategy_path.parent).resolve(),
            errors,
        )
        validate_strategy_keywords(
            strategy,
            keyword_analysis_path or strategy_path.parent / "keyword-research-analysis.csv",
            valid_claims,
            errors,
        )
    if feature:
        validate_feature(feature, valid_claims, assets, errors, warnings, concept_mode)
    if screenshots:
        validate_screenshots(screenshots, valid_claims, assets, errors, warnings, concept_mode)
    if video:
        validate_video_links(video, valid_claims, assets, errors, concept_mode)
    validate_banned(
        [("strategy", strategy), ("feature graphic", feature), ("screenshots", screenshots), ("video", video)],
        errors,
    )
    return errors, warnings


def main() -> int:
    args = parse_args()
    errors, warnings = validate(
        Path(args.strategy),
        Path(args.feature_graphic) if args.feature_graphic else None,
        Path(args.screenshots) if args.screenshots else None,
        Path(args.video) if args.video else None,
        args.require_complete_package,
        Path(args.project_root),
        Path(args.keyword_analysis) if args.keyword_analysis else None,
    )
    for warning in warnings:
        print(f"WARN: {warning}")
    for error in errors:
        print(f"ERROR: {error}")
    if errors:
        print(f"listing brief validation failed: {len(errors)} error(s), {len(warnings)} warning(s)")
        return 1
    print(f"listing brief validation passed: {len(warnings)} warning(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
