#!/usr/bin/env python3
"""Static auditor for Android R8/ProGuard keep rules and consumer-rules.pro.

Scans an Android workspace for overly broad keep rules that block R8 shrinking,
optimization, or obfuscation under Google Play's DEX optimization thresholds.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable

EXCLUDED_DIRS = {
    ".git",
    ".gradle",
    ".idea",
    ".cxx",
    ".kotlin",
    "build",
    "node_modules",
    "tmp",
    "__pycache__",
    ".ai-work",
}

PRO_FILE_PATTERNS = (
    re.compile(r"^proguard.*\.pro$", re.IGNORECASE),
    re.compile(r"^consumer.*\.pro$", re.IGNORECASE),
    re.compile(r"^r8.*\.pro$", re.IGNORECASE),
)

# Well-known third-party namespaces that ship their own consumer keep rules
# or should never be kept wholesale by an app/library.
THIRD_PARTY_PREFIXES = (
    "com.google.android.gms.",
    "com.google.firebase.",
    "com.google.gson.",
    "androidx.",
    "android.support.",
    "kotlin.",
    "kotlinx.",
    "coil.",
    "coil3.",
    "okhttp3.",
    "okio.",
    "retrofit2.",
    "com.bumptech.glide.",
    "com.squareup.",
    "io.reactivex.",
    "org.jetbrains.",
    "com.lxj.xpopup.",
)

SOFT_MODIFIERS = ("allowshrinking", "allowoptimization", "allowobfuscation")


@dataclass
class Finding:
    severity: str  # CRITICAL | HIGH | INFO
    rule_id: str
    file: str
    line: int
    statement: str
    message: str


def iter_pro_files(project_root: Path) -> Iterable[Path]:
    for dirpath, dirnames, filenames in os.walk(project_root):
        dirnames[:] = [
            d for d in dirnames
            if d not in EXCLUDED_DIRS and not d.startswith(".agents")
        ]
        for fname in sorted(filenames):
            if any(p.match(fname) for p in PRO_FILE_PATTERNS):
                yield Path(dirpath) / fname


def parse_statements(path: Path) -> list[tuple[int, str]]:
    """Collapse multi-line ProGuard/R8 directives into (start_line, text)."""
    try:
        lines = path.read_text(encoding="utf-8", errors="ignore").splitlines()
    except OSError:
        return []

    statements: list[tuple[int, str]] = []
    buf: list[str] = []
    start_line = 0
    brace_depth = 0

    def flush() -> None:
        nonlocal buf, start_line
        if buf:
            joined = " ".join(part.strip() for part in buf if part.strip())
            if joined:
                statements.append((start_line, joined))
        buf = []
        start_line = 0

    for idx, raw in enumerate(lines, start=1):
        # Strip inline comments
        line = re.sub(r"#.*$", "", raw).strip()
        if not line:
            continue

        if line.startswith("-") and brace_depth == 0:
            flush()
            start_line = idx
            buf = [line]
        elif buf:
            buf.append(line)
        else:
            start_line = idx
            buf = [line]

        brace_depth += line.count("{") - line.count("}")
        if brace_depth < 0:
            brace_depth = 0
        if brace_depth == 0 and (";" in line or not line.endswith("{")):
            # Keep single-line directives flushed immediately unless next line starts a block
            pass

    flush()
    return statements


def extract_class_target(stmt: str) -> str | None:
    m = re.search(
        r"\b(?:class|interface|enum|@interface)\s+([A-Za-z0-9_$.*]+)",
        stmt,
    )
    return m.group(1) if m else None


def extract_keep_modifiers(stmt: str) -> str:
    m = re.match(r"^(-keep[a-z,]*)\b", stmt)
    return m.group(1).lower() if m else ""


def collect_consumer_packages(project_root: Path, pro_files: list[Path]) -> dict[str, str]:
    """Map package prefixes owned by library modules that already ship consumer-rules.pro."""
    owned: dict[str, str] = {}
    for pf in pro_files:
        if "consumer" not in pf.name.lower():
            continue
        rel = pf.relative_to(project_root).as_posix()
        for _, stmt in parse_statements(pf):
            if not stmt.startswith("-keep"):
                continue
            target = extract_class_target(stmt)
            if not target or target.startswith("*"):
                continue
            parts = [p for p in target.split(".") if p and "*" not in p]
            if len(parts) >= 3:
                prefix = ".".join(parts[:3]) + "."
                owned.setdefault(prefix, rel)
    return owned


def audit_project(project_root: Path) -> dict:
    pro_files = list(iter_pro_files(project_root))
    consumer_owned = collect_consumer_packages(project_root, pro_files)
    findings: list[Finding] = []

    for pf in pro_files:
        rel = pf.relative_to(project_root).as_posix()
        is_host_file = (
            rel.startswith("app/")
            or "/app/" in f"/{rel}"
        ) and "consumer" not in pf.name.lower()

        for line_no, stmt in parse_statements(pf):
            low = stmt.lower()

            # 1. Global R8 disable flags
            if re.match(r"^-(dontoptimize|dontobfuscate|dontshrink)\b", low):
                findings.append(
                    Finding(
                        severity="CRITICAL",
                        rule_id="DISABLE_R8_GLOBAL_FLAG",
                        file=rel,
                        line=line_no,
                        statement=stmt,
                        message="全局关闭 R8 裁剪/优化/混淆会直接拉垮 Google Play DEX 优化指标，严禁用于 Release。",
                    )
                )
                continue

            # 2. -keepnames abuse
            if re.match(r"^-keepnames\b", low):
                target = extract_class_target(stmt) or ""
                if "**" in target or "*" in target:
                    findings.append(
                        Finding(
                            severity="HIGH",
                            rule_id="KEEPNAMES_OBFUSCATION_BLOCK",
                            file=rel,
                            line=line_no,
                            statement=stmt,
                            message="使用 -keepnames 会阻止重命名混淆并拉低 Obfuscation 指标；堆栈还原应依赖上传 mapping.txt。",
                        )
                    )
                continue

            if not re.match(r"^-keep(?:classeswithmembers|classmembers)?(?:,|\s)", low):
                continue

            target = extract_class_target(stmt)
            if not target:
                continue

            mods = extract_keep_modifiers(stmt)
            has_soft = any(m in mods for m in SOFT_MODIFIERS)
            keeps_all_members = bool(re.search(r"\{\s*\*\s*;\s*\}", stmt))
            is_wildcard_pkg = target.endswith(".**") or target.endswith(".*") or target.startswith("**.")

            # 3. Unprefixed global wildcard package (e.g. **.model.**)
            if target.startswith("**.") or target == "**":
                findings.append(
                    Finding(
                        severity="CRITICAL",
                        rule_id="GLOBAL_WILDCARD_PACKAGE",
                        file=rel,
                        line=line_no,
                        statement=stmt,
                        message=(
                            f"无前缀全局通配符 `{target}` 会污染全工程与第三方依赖（如 GMS/Firebase），"
                            "应改为 @SerializedName、Codegen 或精确包名前缀。"
                        ),
                    )
                )
                continue

            # 4. Third-party namespace package-wide keep
            if is_wildcard_pkg and any(target.startswith(tp) for tp in THIRD_PARTY_PREFIXES):
                findings.append(
                    Finding(
                        severity="CRITICAL",
                        rule_id="THIRD_PARTY_PACKAGE_KEEP",
                        file=rel,
                        line=line_no,
                        statement=stmt,
                        message=(
                            f"对主流三方库命名空间 `{target}` 整包保留会覆盖并吞没上游自带的精细 consumer rules。"
                        ),
                    )
                )
                continue

            # 5. Host file duplicating/subsuming a module's consumer-rules.pro
            if is_host_file and is_wildcard_pkg and not has_soft:
                matched_consumer = None
                for prefix, owner_file in consumer_owned.items():
                    if target.startswith(prefix) or prefix.startswith(target.rstrip("*")):
                        matched_consumer = owner_file
                        break
                if matched_consumer:
                    findings.append(
                        Finding(
                            severity="HIGH",
                            rule_id="HOST_DUPLICATES_CONSUMER_PACKAGE",
                            file=rel,
                            line=line_no,
                            statement=stmt,
                            message=(
                                f"宿主整包保留 `{target}` 会覆盖子模块 `{matched_consumer}` 的精细规则；"
                                "应由库模块 consumer-rules.pro 自治。"
                            ),
                        )
                    )
                    continue

            # 6. Broad package-wide keep without soft modifiers
            if is_wildcard_pkg and not has_soft and (keeps_all_members or "{" not in stmt):
                findings.append(
                    Finding(
                        severity="HIGH",
                        rule_id="BROAD_PACKAGE_KEEP",
                        file=rel,
                        line=line_no,
                        statement=stmt,
                        message=(
                            f"宽泛整包保留 `{target}` 锁死了 R8 裁剪、内联与混淆；"
                            "应收敛到具体反射/JNI 入口点，或补充 allowshrinking/allowoptimization/allowobfuscation。"
                        ),
                    )
                )

    counts = {
        "CRITICAL": sum(1 for f in findings if f.severity == "CRITICAL"),
        "HIGH": sum(1 for f in findings if f.severity == "HIGH"),
        "INFO": sum(1 for f in findings if f.severity == "INFO"),
    }
    return {
        "project_root": str(project_root.resolve()),
        "scanned_files": [p.relative_to(project_root).as_posix() for p in pro_files],
        "counts": counts,
        "findings": [asdict(f) for f in findings],
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Audit Android R8/ProGuard keep rules against Google Play DEX optimization best practices."
    )
    parser.add_argument(
        "--project-root",
        default=".",
        help="Path to Android project root (default: current directory).",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Emit structured JSON report.",
    )
    parser.add_argument(
        "--fail-on-critical",
        action="store_true",
        help="Exit with code 2 if any CRITICAL finding is detected.",
    )
    args = parser.parse_args(argv)

    root = Path(args.project_root).resolve()
    if not root.is_dir():
        print(f"Error: project root not found: {root}", file=sys.stderr)
        return 1

    report = audit_project(root)
    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        c = report["counts"]
        print(
            f"R8 Keep Rules Audit: scanned {len(report['scanned_files'])} files | "
            f"CRITICAL={c['CRITICAL']} HIGH={c['HIGH']} INFO={c['INFO']}"
        )
        for item in report["findings"]:
            print(
                f"[{item['severity']}] {item['rule_id']} {item['file']}:{item['line']}\n"
                f"  Rule: {item['statement']}\n"
                f"  Note: {item['message']}"
            )

    if args.fail_on_critical and report["counts"]["CRITICAL"] > 0:
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
