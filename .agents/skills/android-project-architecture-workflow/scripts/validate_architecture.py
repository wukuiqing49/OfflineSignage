#!/usr/bin/env python3
"""Validate Android module boundaries and shared-capability anti-patterns."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


PROJECT_DEP_RE = re.compile(r"project\(['\"](?P<path>:[^'\"]+)['\"]\)")
INCLUDE_RE = re.compile(r"include\s*\(?\s*['\"](?P<module>:[^'\"]+)['\"]")
UTILITY_NAME_RE = re.compile(r"(Network|Http|File|Uri|Permission|Router|Dialog|Popup|Image|Download|Upload|Storage).*(Util|Utils|Helper|Manager)\.(kt|java)$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Android project architecture workflow gates.")
    parser.add_argument("--project-root", default=".", help="Project root.")
    return parser.parse_args()


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def module_path_from_dir(root: Path, module_dir: Path) -> str:
    return ":" + module_dir.relative_to(root).as_posix().replace("/", ":")


def collect_modules(root: Path) -> dict[str, Path]:
    modules: dict[str, Path] = {}
    for build in list(root.glob("**/build.gradle")) + list(root.glob("**/build.gradle.kts")):
        if ".gradle" in build.parts or "build" in build.parts:
            continue
        module_dir = build.parent
        if module_dir == root:
            continue
        modules[module_path_from_dir(root, module_dir)] = module_dir
    return modules


def module_dir_from_path(root: Path, module: str) -> Path:
    return root / module.strip(":").replace(":", "/")


def collect_settings_modules(root: Path) -> set[str]:
    settings = next((root / name for name in ("settings.gradle", "settings.gradle.kts") if (root / name).exists()), None)
    if settings is None:
        return set()
    return {match.group("module") for match in INCLUDE_RE.finditer(read_text(settings))}


def collect_project_dependencies(module_dir: Path) -> set[str]:
    deps: set[str] = set()
    for name in ("build.gradle", "build.gradle.kts"):
        path = module_dir / name
        if path.exists():
            deps.update(match.group("path") for match in PROJECT_DEP_RE.finditer(read_text(path)))
    return deps


def validate_settings_modules(root: Path, modules: dict[str, Path], errors: list[str], warnings: list[str]) -> None:
    included = collect_settings_modules(root)
    if not included:
        warnings.append("WARN: settings.gradle(.kts) includes were not found; module graph check is incomplete.")
        return
    for container in (":core", ":feature"):
        if container in included:
            errors.append(f"ERROR: settings: directory container {container} must not be registered as a module.")
    for module in sorted(included):
        if module in (":core", ":feature"):
            continue
        module_dir = module_dir_from_path(root, module)
        if not ((module_dir / "build.gradle").exists() or (module_dir / "build.gradle.kts").exists()):
            errors.append(f"ERROR: settings: included module {module} has no build.gradle(.kts) at {module_dir}.")
    for module in sorted(set(modules) - included):
        warnings.append(f"WARN: {modules[module]}: module build file exists but module is not included in settings.")


def validate_dependencies(modules: dict[str, Path], errors: list[str], warnings: list[str]) -> None:
    for module, path in modules.items():
        deps = collect_project_dependencies(path)
        if module.startswith(":core:"):
            bad = sorted(dep for dep in deps if dep == ":app" or dep.startswith(":feature:"))
            if bad:
                errors.append(f"ERROR: {path}: core module must not depend on app/feature modules: {', '.join(bad)}")
        if module == ":feature:feature_res":
            bad = sorted(dep for dep in deps if dep.startswith(":feature:") or dep == ":app")
            if bad:
                errors.append(f"ERROR: {path}: feature_res must not depend on app/feature modules: {', '.join(bad)}")
        if module.startswith(":feature:") and module != ":feature:feature_res":
            if ":feature:feature_res" not in deps:
                warnings.append(f"WARN: {path}: feature UI module does not depend on :feature:feature_res.")


def validate_wkq_api(modules: dict[str, Path], warnings: list[str]) -> None:
    for module in (":core:core_base", ":core:core_utils"):
        path = modules.get(module)
        if not path:
            continue
        build_file = next((path / name for name in ("build.gradle", "build.gradle.kts") if (path / name).exists()), None)
        if not build_file:
            continue
        text = read_text(build_file)
        if "wkq-core-base" in text and not re.search(r"\bapi\s+libs\.wkq\.core\.base|\bapi\s*\(?", text):
            warnings.append(f"WARN: {build_file}: AndroidCoreBase wrapper usually needs api when downstream extends Base types.")
        if "wkq-core-utils" in text and not re.search(r"\bapi\s+libs\.wkq\.core\.utils|\bapi\s*\(?", text):
            warnings.append(f"WARN: {build_file}: AndroidCoreUtils wrapper may need api if utility entry points are exposed.")


def validate_runtime_boundaries(root: Path, modules: dict[str, Path], errors: list[str], warnings: list[str]) -> None:
    for manifest in root.glob("**/src/main/AndroidManifest.xml"):
        text = read_text(manifest)
        module = module_path_from_dir(root, manifest.parents[2])
        if "FileProvider" in text and module != ":app":
            errors.append(f"ERROR: {manifest}: FileProvider should be owned by app module.")
    core_utils_init = []
    for source in list(root.glob("**/src/main/**/*.kt")) + list(root.glob("**/src/main/**/*.java")):
        text = read_text(source)
        if "CoreUtils.init" in text:
            core_utils_init.append(source)
        if "Uri.fromFile" in text:
            warnings.append(f"WARN: {source}: Uri.fromFile found; use FileProvider/content URI for external sharing.")
    if len(core_utils_init) > 1:
        warnings.append("WARN: CoreUtils.init appears in multiple files: " + ", ".join(str(path) for path in core_utils_init))
    for source in list((root / "feature").glob("**/*.kt")) + list((root / "feature").glob("**/*.java")):
        if UTILITY_NAME_RE.search(source.name):
            warnings.append(f"WARN: {source}: feature module appears to define shared utility; verify core/WKQ cannot be reused.")


def main() -> int:
    args = parse_args()
    root = Path(args.project_root)
    errors: list[str] = []
    warnings: list[str] = []
    modules = collect_modules(root)
    validate_settings_modules(root, modules, errors, warnings)
    validate_dependencies(modules, errors, warnings)
    validate_wkq_api(modules, warnings)
    validate_runtime_boundaries(root, modules, errors, warnings)
    for item in warnings:
        print(item)
    for item in errors:
        print(item)
    if errors:
        print(f"architecture validation failed: {len(errors)} error(s), {len(warnings)} warning(s)")
        return 1
    print(f"architecture validation passed: {len(warnings)} warning(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
