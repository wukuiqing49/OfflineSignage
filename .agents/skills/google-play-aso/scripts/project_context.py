"""识别 Skill 所属项目并收集可复核的源码索引，不执行 Gradle。"""
from __future__ import annotations
import os
from pathlib import Path
import re
import subprocess


def locate_project(explicit: Path | None = None) -> tuple[Path, Path]:
    """以 Skill 所在 .agents/.agent 为归属，显式目标可覆盖；不依赖启动目录。"""
    if explicit is not None:
        root = explicit.expanduser().resolve()
        if not root.is_dir():
            raise ValueError("目标项目目录不存在")
        agent = root / (".agent" if (root / ".agent").is_dir() and not (root / ".agents").is_dir() else ".agents")
        return root, agent
    for parent in Path(__file__).resolve().parents:
        if parent.name in (".agents", ".agent"):
            if parent.parent == Path.home().resolve():
                raise ValueError("个人全局 Skill 必须通过 --project 指定目标项目")
            return parent.parent, parent
    raise ValueError("Skill 未安装在项目 .agents/.agent 中，请显式传 --project")


def inspect_project(root: Path) -> dict:
    """收集构建、Manifest 和语言资源位置；包名候选必须经发行变体核实。"""
    ignored = {".git", ".agents", ".agent", ".google-play-aso", ".gradle", ".idea", ".venv", "venv", "node_modules", "build", "dist", "credentials", ".ai-work"}
    builds, manifests, resources, candidates = [], [], [], []
    for directory, dirs, files in os.walk(root, followlinks=False):
        dirs[:] = sorted(d for d in dirs if d not in ignored and not (Path(directory) / d).is_symlink())
        for name in sorted(files):
            path = Path(directory) / name
            if path.is_symlink():
                continue
            relative = path.relative_to(root).as_posix()
            if name in ("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "libs.versions.toml"):
                builds.append(relative)
                for number, line in enumerate(path.read_text(encoding="utf-8-sig", errors="replace").splitlines(), 1):
                    match = re.search(r'\bapplicationId\s*(?:=\s*)?["\x27]([\w.]+)["\x27]', line)
                    if match and not line.strip().startswith(("//", "*")):
                        candidates.append({"packageName": match[1], "file": relative, "line": number, "verifiedRelease": False})
            elif name == "AndroidManifest.xml":
                manifests.append(relative)
            elif name == "strings.xml" and path.parent.name.startswith("values"):
                resources.append(relative)
    source = {"commit": None, "dirty": None}
    try:
        head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=root, capture_output=True, text=True, timeout=10)
        status = subprocess.run(["git", "status", "--porcelain"], cwd=root, capture_output=True, text=True, timeout=10)
        if head.returncode == 0:
            source = {"commit": head.stdout.strip(), "dirty": bool(status.stdout.strip()) if status.returncode == 0 else None}
    except (OSError, subprocess.TimeoutExpired):
        pass
    return {"projectRoot": str(root), "source": source, "buildFiles": builds, "manifests": manifests,
            "stringResources": resources, "applicationIdCandidates": candidates,
            "note": "静态索引不是产品功能审计；需由 Skill 阅读实际入口与源码。候选不是已确认发行包；动态配置、flavor、suffix 需进一步核实。"}
