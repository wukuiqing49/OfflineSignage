#!/usr/bin/env python3
"""Validate Android build, manifest and high-target SDK compatibility gates."""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Android build workflow output.")
    parser.add_argument("--project-root", default=".", help="Android project root.")
    parser.add_argument("--app-module", default="app", help="App module path relative to project root.")
    return parser.parse_args()


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def add(items: list[str], level: str, path: Path | str, message: str) -> None:
    items.append(f"{level}: {path}: {message}")


def read_app_build_configuration(root: Path, app_build: Path, warnings: list[str]) -> str:
    text = read_text(app_build)
    applied_paths = re.findall(
        r"apply\s+from:\s*rootProject\.file\(\s*['\"](?P<path>[^'\"]+)['\"]\s*\)",
        text,
    )
    root_resolved = root.resolve()
    for raw in applied_paths:
        applied = (root / raw).resolve()
        if not applied.is_relative_to(root_resolved):
            add(warnings, "WARN", app_build, f"applied Gradle script is outside project root: {raw}")
            continue
        if not applied.is_file():
            add(warnings, "WARN", app_build, f"applied Gradle script was not found: {raw}")
            continue
        text += "\n" + read_text(applied)
    return text


def find_target_sdk(root: Path, app_build: Path) -> int | None:
    catalog = root / "gradle" / "libs.versions.toml"
    if catalog.exists():
        match = re.search(r'^\s*targetSdk\s*=\s*"?(?P<value>\d+)"?', read_text(catalog), re.M)
        if match:
            return int(match.group("value"))
    if app_build.exists():
        match = re.search(r"targetSdk\s*=?\s*(?:libs\.versions\.targetSdk\.get\(\)\.toInteger\(\)|(?P<value>\d+))", read_text(app_build))
        if match and match.groupdict().get("value"):
            return int(match.group("value"))
    root_build = next((root / name for name in ("build.gradle", "build.gradle.kts") if (root / name).exists()), None)
    if root_build is not None:
        match = re.search(r"\btargetSdk\s*(?::|=)\s*(?P<value>\d+)", read_text(root_build))
        if match:
            return int(match.group("value"))
    return None


def validate_versions_and_signing(root: Path, app_build: Path, errors: list[str], warnings: list[str]) -> None:
    if not app_build.exists():
        add(errors, "ERROR", app_build, "app build file does not exist.")
        return
    text = read_app_build_configuration(root, app_build, warnings)
    if re.search(r"applicationId\s*=\s*['\"][^'\"]+['\"]", text):
        add(errors, "ERROR", app_build, "applicationId appears hardcoded; use project property/provider.")
    if re.search(r"versionName\s*=\s*['\"][^'\"]+['\"]", text):
        add(errors, "ERROR", app_build, "versionName appears hardcoded; use single semantic version property.")
    if "calculatedVersionCode" not in text and "appConfig.versionCode" not in text:
        add(warnings, "WARN", app_build, "semantic versionCode calculation was not found.")
    if re.search(r"release\s*\{[\s\S]*signingConfig\s*=\s*signingConfigs\.debug", text):
        add(errors, "ERROR", app_build, "release buildType must not fall back to debug signing.")
    if "storePassword" in text and not re.search(r"environmentVariable|Properties|keystore\.properties", text):
        add(warnings, "WARN", app_build, "signing values found without obvious properties/env indirection.")
    release_match = re.search(r"release\s*\{(?P<body>[\s\S]*?)\n\s*\}", text)
    if release_match:
        release_body = release_match.group("body")
        minify_false = re.search(r"minifyEnabled\s*=\s*false|minifyEnabled\s+false", release_body)
        shrink_true = re.search(r"shrinkResources\s*=\s*true|shrinkResources\s+true", release_body)
        minify_true = re.search(r"minifyEnabled\s*=\s*true|minifyEnabled\s+true", release_body)
        if shrink_true and minify_false:
            add(errors, "ERROR", app_build, "shrinkResources=true requires minifyEnabled=true.")
        if minify_true and "proguardFiles" not in release_body:
            add(warnings, "WARN", app_build, "release minify is enabled without an obvious proguardFiles declaration.")
    else:
        add(warnings, "WARN", app_build, "release buildType was not found.")


def validate_settings(root: Path, errors: list[str], warnings: list[str]) -> None:
    settings = next((root / name for name in ("settings.gradle", "settings.gradle.kts") if (root / name).exists()), None)
    if settings is None:
        add(warnings, "WARN", "settings", "settings.gradle(.kts) was not found.")
        return
    text = read_text(settings)
    includes = set(re.findall(r"include\s*\(?\s*['\"](?P<module>:[^'\"]+)['\"]", text))
    for container in (":core", ":feature"):
        if container in includes:
            add(errors, "ERROR", settings, f"directory container {container} must not be registered as a real module.")
    if ":app" not in includes:
        add(warnings, "WARN", settings, "settings does not include :app.")


def validate_manifest(app_manifest: Path, errors: list[str], warnings: list[str]) -> None:
    if not app_manifest.exists():
        add(errors, "ERROR", app_manifest, "app manifest does not exist.")
        return
    text = read_text(app_manifest)
    if 'usesCleartextTraffic="true"' in text:
        add(warnings, "WARN", app_manifest, "cleartext traffic is enabled; verify this is a narrow business exception.")
    if "android:networkSecurityConfig" not in text:
        add(warnings, "WARN", app_manifest, "networkSecurityConfig is not declared.")
    try:
        root = ET.fromstring(text)
    except ET.ParseError as exc:
        add(errors, "ERROR", app_manifest, f"manifest XML parse failed: {exc}")
        return
    for provider in root.findall(".//provider"):
        authority = provider.attrib.get(ANDROID_NS + "authorities", "")
        name = provider.attrib.get(ANDROID_NS + "name", "")
        if "FileProvider" not in name:
            continue
        if "${applicationId}" not in authority:
            add(errors, "ERROR", app_manifest, "FileProvider authority must include ${applicationId}.")
        if provider.attrib.get(ANDROID_NS + "exported") != "false":
            add(errors, "ERROR", app_manifest, "FileProvider must set exported=false.")
        if provider.attrib.get(ANDROID_NS + "grantUriPermissions") != "true":
            add(errors, "ERROR", app_manifest, "FileProvider must grant temporary URI permissions.")


def allows_user_configured_cleartext(root: Path) -> bool:
    profile = root / ".agents" / "config" / "project-profile.yml"
    if not profile.is_file():
        return False
    return bool(re.search(r"^\s*allowUserConfiguredCleartextEndpoints:\s*true\s*$", read_text(profile), re.M | re.I))


def validate_network_security(root: Path, errors: list[str], warnings: list[str]) -> None:
    allow_cleartext = allows_user_configured_cleartext(root)
    for path in root.glob("**/src/main/res/xml/network_security_config.xml"):
        text = read_text(path)
        if re.search(r"<base-config[^>]*cleartextTrafficPermitted=\"true\"", text):
            target = warnings if allow_cleartext else errors
            level = "WARN" if allow_cleartext else "ERROR"
            message = (
                "global cleartext is enabled for user-configured endpoints; keep the in-app transport warning visible."
                if allow_cleartext
                else "base-config must not globally allow cleartext traffic."
            )
            add(target, level, path, message)
        if 'certificates src="user"' in text:
            add(warnings, "WARN", path, "user certificates are trusted; verify this does not enter release unintentionally.")


def validate_file_paths(root: Path, errors: list[str]) -> None:
    for path in root.glob("**/src/main/res/xml/file_paths.xml"):
        text = read_text(path)
        if "<root-path" in text:
            add(errors, "ERROR", path, "FileProvider paths must not expose root-path.")
        if re.search(r'path="\."|path="/"|path=""', text):
            add(errors, "ERROR", path, "FileProvider path is too broad.")


def validate_edge_to_edge(root: Path, target_sdk: int | None, errors: list[str], warnings: list[str]) -> None:
    if target_sdk is None or target_sdk < 35:
        return
    theme_text = "\n".join(read_text(path) for path in root.glob("**/src/main/res/values*/themes.xml"))
    code_text = "\n".join(read_text(path) for path in root.glob("**/src/main/**/*.kt"))
    code_text += "\n".join(read_text(path) for path in root.glob("**/src/main/**/*.java"))
    for item in ("statusBarColor", "navigationBarColor", "windowLightStatusBar"):
        if item not in theme_text:
            add(warnings, "WARN", "theme", f"targetSdk {target_sdk} should document theme system bar item: {item}.")
    if "enableEdgeToEdge" not in code_text and "WindowCompat.setDecorFitsSystemWindows" not in code_text:
        add(errors, "ERROR", "source", f"targetSdk {target_sdk} requires an Edge-to-edge runtime entry.")
    if "fitsSystemWindows=\"true\"" in theme_text:
        add(warnings, "WARN", "resources", "fitsSystemWindows should not be used as a global Edge-to-edge fallback.")


def validate_android17(root: Path, target_sdk: int | None, errors: list[str], warnings: list[str]) -> None:
    """Validate known API 37 tool constraints; runtime capability checks stay explicit."""
    catalog = root / "gradle/libs.versions.toml"
    text = read_text(catalog) if catalog.is_file() else ""
    def version(key: str) -> tuple[int, ...] | None:
        match = re.search(r'^\s*' + key + r'\s*=\s*"([0-9.]+)"', text, re.M)
        return tuple(map(int, match[1].split("."))) if match else None
    compile_sdk = version("compileSdk")
    if (target_sdk or 0) < 37 and (compile_sdk or (0,)) < (37,):
        return
    agp = version("agp")
    if agp is None:
        add(warnings, "WARN", catalog, "API 37: AGP version not resolved; verify official tool support manually.")
    elif agp < (9, 1, 1):
        add(errors, "ERROR", catalog, "API 37 requires AGP >= 9.1.1.")
    kotlin = version("kotlin")
    if agp and (9, 1, 1) <= agp < (9, 2) and kotlin and kotlin < (2, 2, 10):
        add(errors, "ERROR", catalog, "AGP 9.1 baseline requires Kotlin >= 2.2.10.")
    properties = root / "gradle.properties"
    if agp and agp >= (9,) and properties.is_file():
        flags = read_text(properties)
        disabled = [bool(re.search(r"^\s*" + re.escape(key) + r"\s*=\s*false\s*$", flags, re.M))
                    for key in ("android.builtInKotlin", "android.newDsl")]
        if disabled[0] and not disabled[1]:
            add(errors, "ERROR", properties, "Legacy Kotlin opt-out also requires android.newDsl=false on AGP 9.")
    if compile_sdk and target_sdk and compile_sdk < (target_sdk,):
        add(errors, "ERROR", catalog, "compileSdk must not be below targetSdk.")
    wrapper = root / "gradle/wrapper/gradle-wrapper.properties"
    match = re.search(r"gradle-([0-9.]+)-(?:bin|all)\.zip", read_text(wrapper)) if wrapper.is_file() else None
    if agp and (9, 1, 1) <= agp < (9, 2):
        if not match or tuple(map(int, match[1].split("."))) < (9, 3, 1):
            add(errors, "ERROR", wrapper, "AGP 9.1.x requires Gradle >= 9.3.1.")
    # Only app/feature declarations: preserve independent library manifests and report host overrides.
    for parent in (root / "app", root / "feature"):
        for manifest in parent.glob("**/src/main/AndroidManifest.xml"):
            value = read_text(manifest)
            if (target_sdk or 0) >= 37 and re.search(r'android:(?:screenOrientation|resizeableActivity)=\"(?:portrait|landscape|false)\"', value):
                add(warnings, "WARN", manifest, "API 37 large screens: orientation/resizability restrictions cannot replace adaptive layouts.")


def main() -> int:
    args = parse_args()
    root = Path(args.project_root)
    app = root / args.app_module
    errors: list[str] = []
    warnings: list[str] = []

    app_build = app / "build.gradle"
    if not app_build.exists():
        app_build = app / "build.gradle.kts"
    target_sdk = find_target_sdk(root, app_build)

    validate_android17(root, target_sdk, errors, warnings)
    validate_settings(root, errors, warnings)
    validate_versions_and_signing(root, app_build, errors, warnings)
    validate_manifest(app / "src/main/AndroidManifest.xml", errors, warnings)
    validate_network_security(root, errors, warnings)
    validate_file_paths(root, errors)
    validate_edge_to_edge(root, target_sdk, errors, warnings)

    for item in warnings:
        print(item)
    for item in errors:
        print(item)
    if errors:
        print(f"build validation failed: {len(errors)} error(s), {len(warnings)} warning(s)")
        return 1
    print(f"build validation passed: {len(warnings)} warning(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
