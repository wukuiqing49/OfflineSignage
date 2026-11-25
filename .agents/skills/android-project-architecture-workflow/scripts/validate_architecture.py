#!/usr/bin/env python3
"""Validate Android module boundaries and shared-capability anti-patterns."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


TOKEN_RE = re.compile(r'''"""[\s\S]*?"""|''' + "'''[\\s\\S]*?'''" + r'''|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|/\*[\s\S]*?\*/|//[^\n]*''')
LITERAL_RE = re.compile(r'''(?P<quote>["'])(?P<path>:[^"'\\$]+)(?P=quote)''')
UTILITY_NAME_RE = re.compile(
    r"(?:Network|Http|File|Uri|Permission|Router|Image|Download|Upload|Storage).*(?:Util|Utils)\.(?:kt|java)$"
    r"|(?:Network|Http|Permission|Router|Download|Storage)Manager\.(?:kt|java)$"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Android project architecture workflow gates.")
    parser.add_argument("--project-root", default=".", help="Project root.")
    return parser.parse_args()


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def gradle_text(text: str, mask_strings: bool = False) -> str:
    """屏蔽注释，并可屏蔽字符串，保留偏移与换行以定位真实调用。"""
    def replace(match: re.Match) -> str:
        """按词法片段保留字符串或替换为空白。"""
        value = match.group()
        if not mask_strings and not value.startswith(("//", "/*")):
            return value
        return re.sub(r"[^\n]", " ", value)
    return TOKEN_RE.sub(replace, text)


def gradle_calls(text: str, name: str) -> list[str]:
    """读取字面调用参数，支持多行括号和 Groovy 无括号语法。"""
    clean = gradle_text(text)
    masked = gradle_text(text, mask_strings=True)
    arguments: list[str] = []
    for match in re.finditer(r"(?<![\w.])" + re.escape(name) + r"\b", masked):
        start = match.end()
        while start < len(clean) and clean[start].isspace():
            start += 1
        if start < len(masked) and masked[start] == "(":
            depth, end = 1, start + 1
            while end < len(masked) and depth:
                depth += (masked[end] == "(") - (masked[end] == ")")
                end += 1
            arguments.append(clean[start + 1:end - 1] if depth == 0 else "<unresolved>")
        else:
            # 无括号调用按原文的单条语句读取，字符串中的标点不参与括号配对。
            arguments.append(re.split(r"[;\n]", clean[match.end():], maxsplit=1)[0].strip())
    return arguments


def incomplete(warnings: list[str] | None, path: Path, reason: str) -> None:
    """记录无法静态解析的图信息，不将未知关系当作不存在。"""
    if warnings is not None:
        message = f"WARN: {path}: module graph check is incomplete: {reason}."
        if message not in warnings:
            warnings.append(message)


def module_path_from_dir(root: Path, module_dir: Path) -> str:
    """优先按 settings 中的目录映射解析逻辑模块，兼容按职责分组的源码目录。"""
    for module, path in project_directory_mappings(root).items():
        if path.resolve() == module_dir.resolve():
            return module
    return ":" + module_dir.relative_to(root).as_posix().replace("/", ":")


def project_directory_mappings(root: Path) -> dict[str, Path]:
    """读取显式 projectDir 映射，未映射工程仍沿用标准目录约定。"""
    settings = next((root / name for name in ("settings.gradle", "settings.gradle.kts") if (root / name).is_file()), None)
    if settings is None:
        return {}
    raw = read_text(settings)
    text = gradle_text(raw)
    masked = gradle_text(raw, True)
    pattern = r"project\(\s*['\"](:[^'\"$\\]+)['\"]\s*\)\.projectDir\s*=\s*file\(\s*['\"]([^'\"$\\]+)['\"]\s*\)"
    return {match.group(1): root / match.group(2) for match in re.finditer(pattern, text)
            if masked[match.start():].startswith("project")}


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
    """将逻辑模块路径解析到实际源码目录。"""
    return project_directory_mappings(root).get(module, root / module.strip(":").replace(":", "/"))


def collect_settings_modules(root: Path, warnings: list[str] | None = None) -> set[str]:
    """收集每个 include 的静态模块参数，动态表达式保留未验证提示。"""
    settings = next((root / name for name in ("settings.gradle", "settings.gradle.kts") if (root / name).exists()), None)
    if settings is None:
        return set()
    text = read_text(settings)
    modules: set[str] = set()
    for arguments in gradle_calls(text, "include"):
        for argument in arguments.split(","):
            if not argument.strip():
                continue
            match = LITERAL_RE.fullmatch(argument.strip())
            if match:
                modules.add(match.group("path"))
            else:
                incomplete(warnings, settings, "dynamic or unsupported include")
    masked = gradle_text(text, mask_strings=True)
    if re.search(r"\b(for|if|while|apply|includeBuild)\b|\.forEach\b", masked):
        incomplete(warnings, settings, "conditional/external settings require Gradle verification")
    if masked.count("projectDir") != len(project_directory_mappings(root)):
        incomplete(warnings, settings, "unsupported projectDir mapping")
    return modules


def collect_project_dependencies(module_dir: Path, warnings: list[str] | None = None) -> set[str]:
    """读取普通及命名 path 依赖，对动态依赖给出范围限制。"""
    deps: set[str] = set()
    for name in ("build.gradle", "build.gradle.kts"):
        path = module_dir / name
        if path.exists():
            text = read_text(path)
            for arguments in gradle_calls(text, "project"):
                first = arguments.split(",", 1)[0].strip()
                first = re.sub(r"^path\s*[:=]\s*", "", first)
                match = LITERAL_RE.fullmatch(first)
                if match:
                    deps.add(match.group("path"))
                else:
                    incomplete(warnings, path, "dynamic or unsupported project dependency")
            if re.search(r"\bprojects\s*\.|\bapply\b|\b(for|if|while)\b|\.forEach\b", gradle_text(text, True)):
                incomplete(warnings, path, "accessors/conditional/external dependencies require Gradle verification")
    return deps


def validate_settings_modules(root: Path, modules: dict[str, Path], errors: list[str], warnings: list[str]) -> None:
    included = collect_settings_modules(root, warnings)
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
    """检查模块反向依赖，并报告静态依赖图中的循环路径。"""
    graph: dict[str, set[str]] = {}
    for module, path in modules.items():
        deps = collect_project_dependencies(path, warnings)
        graph[module] = deps & modules.keys()
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

    visited: set[str] = set()
    active: list[str] = []

    def visit(module: str) -> None:
        """深度遍历依赖，仅在遇到当前路径节点时判定循环。"""
        if module in active:
            cycle = active[active.index(module):] + [module]
            errors.append("ERROR: module dependency cycle: " + " -> ".join(cycle))
            return
        if module in visited:
            return
        active.append(module)
        for dependency in sorted(graph[module]):
            visit(dependency)
        active.pop()
        visited.add(module)

    for module in sorted(graph):
        visit(module)


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
    for module, path in modules.items():
        manifest = path / "src/main/AndroidManifest.xml"
        if not manifest.is_file():
            continue
        text = read_text(manifest)
        if "FileProvider" in text and module != ":app":
            errors.append(f"ERROR: {manifest}: FileProvider should be owned by app module.")
    core_utils_init = []
    sources = [source for path in modules.values() for pattern in ("**/*.kt", "**/*.java")
               for source in (path / "src/main").glob(pattern)]
    for source in sources:
        text = read_text(source)
        if "CoreUtils.init" in text:
            core_utils_init.append(source)
        shares_externally = any(
            marker in text
            for marker in ("Intent.ACTION_SEND", "Intent.ACTION_VIEW", "FLAG_GRANT_READ_URI_PERMISSION", "ClipData")
        )
        if "Uri.fromFile" in text and shares_externally:
            warnings.append(f"WARN: {source}: Uri.fromFile found; use FileProvider/content URI for external sharing.")
    if len(core_utils_init) > 1:
        warnings.append("WARN: CoreUtils.init appears in multiple files: " + ", ".join(str(path) for path in core_utils_init))
    for source in list((root / "feature").glob("**/*.kt")) + list((root / "feature").glob("**/*.java")):
        if UTILITY_NAME_RE.search(source.name):
            warnings.append(f"WARN: {source}: feature module appears to define shared utility; verify core/WKQ cannot be reused.")


def main() -> int:
    """执行静态架构检查，输出无法替代 Gradle 实际图解析的范围。"""
    args = parse_args()
    root = Path(args.project_root)
    errors: list[str] = []
    warnings: list[str] = []
    modules = collect_modules(root)
    validate_settings_modules(root, modules, errors, warnings)
    included = collect_settings_modules(root)
    modules = {name: path for name, path in modules.items() if name in included}
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
    print(f"architecture static validation passed: {len(warnings)} warning(s); Gradle-resolved graph NOT verified")
    return 0


if __name__ == "__main__":
    sys.exit(main())
