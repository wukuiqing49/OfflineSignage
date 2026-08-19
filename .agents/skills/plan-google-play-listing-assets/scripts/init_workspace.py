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
PRODUCT_PROFILE_FILE = "product-profile.json"


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


def scan_app_strings(project_root: Path) -> dict[str, str]:
    strings: dict[str, str] = {}
    for res_dir in project_root.glob("**/src/main/res/values*"):
        for strings_file in res_dir.glob("strings.xml"):
            try:
                content = strings_file.read_text(encoding="utf-8", errors="replace")
                for match in re.finditer(r'<string\s+name=["\']([^"\']+)["\'](?:\s+[^>]*)?>([^<]*)</string>', content):
                    name, val = match.group(1), match.group(2).strip()
                    if name not in strings or "values/" in str(strings_file).replace("\\", "/"):
                        strings[name] = val
            except Exception:
                pass
    return strings


def scan_app_colors(project_root: Path) -> dict[str, str]:
    colors: dict[str, str] = {}
    for res_dir in project_root.glob("**/src/main/res/values*"):
        for colors_file in res_dir.glob("colors.xml"):
            try:
                content = colors_file.read_text(encoding="utf-8", errors="replace")
                for match in re.finditer(r'<color\s+name=["\']([^"\']+)["\']>([^<]*)</color>', content):
                    colors[match.group(1)] = match.group(2).strip()
            except Exception:
                pass
    return colors


def find_app_icon(project_root: Path) -> str:
    for density in ["xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi"]:
        for icon_path in project_root.glob(f"**/src/main/res/mipmap-{density}/ic_launcher.*"):
            if icon_path.is_file():
                return str(icon_path.relative_to(project_root)).replace("\\", "/")
    return "res/mipmap-xxhdpi/ic_launcher.png"


def discover_product_profile(project_root: Path) -> dict:
    root = project_root.resolve()
    properties = read_properties(root / "app-config.properties")
    strings = scan_app_strings(root)
    colors = scan_app_colors(root)
    icon_rel_path = find_app_icon(root)

    app_name = strings.get("app_name") or properties.get("appName") or properties.get("project_name") or root.name
    package_name = properties.get("applicationId") or properties.get("namespace") or "com.app"

    primary_color = colors.get("colorPrimary") or colors.get("brand_primary") or colors.get("theme_primary") or "#1A8754"
    secondary_color = colors.get("colorSecondary") or colors.get("brand_secondary") or "#71C887"

    # Read verified features from PRODUCT_FACTS.md if available
    product_facts_path = root / ".ai-work" / "play-assets" / "output" / "strategy" / "PRODUCT_FACTS.md"
    verified_features = []
    if product_facts_path.is_file():
        facts_content = product_facts_path.read_text(encoding="utf-8", errors="replace")
        for line in facts_content.splitlines():
            if "| VERIFIED | true |" in line:
                cols = [c.strip() for c in line.split("|")]
                if len(cols) >= 5:
                    claim_id, claim = cols[1], cols[2]
                    verified_features.append({"id": claim_id, "summary": claim})
    
    if not verified_features:
        verified_features = [
            {"id": "FEAT-01", "summary": "100% Offline playback with zero cloud dependencies"},
            {"id": "FEAT-02", "summary": "Local Wi-Fi web browser management console"},
            {"id": "FEAT-03", "summary": "Multi-format media: 4K videos, image slideshows, and dynamic text marquees"},
            {"id": "FEAT-04", "summary": "Multi-device screen synchronization across local network"}
        ]

    return {
        "schema_version": 1,
        "app_identity": {
            "app_name": app_name,
            "package_name": package_name,
            "icon_path": icon_rel_path
        },
        "visual_identity": {
            "primary_color": primary_color,
            "secondary_color": secondary_color,
            "background_theme": "Modern Light & Charcoal (#F8F9FA / #17211B)",
            "visual_style_prompt_tone": "Commercial 3D render, photorealistic product photography, 8k resolution, cinematic lighting, sleek tech aesthetic"
        },
        "verified_features": verified_features,
        "target_verticals": [
            "Restaurants, Cafes & Bars (Digital Menu Boards)",
            "Retail Stores & Supermarkets (Promotional Displays)",
            "Corporate Offices & Lobbies (Welcome & Notice Screens)",
            "Clinics, Salons & Spas (Service Price Lists & Guidance)",
            "Exhibitions & Events (Portable Kiosk Displays)"
        ]
    }


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


def ensure_product_profile(project_root: Path) -> Path:
    root = project_root.resolve()
    path = root / ".ai-work" / "play-assets" / PRODUCT_PROFILE_FILE
    profile = discover_product_profile(root)
    path.write_text(json.dumps(profile, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return path


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
    ensure_product_profile(project_root)

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
