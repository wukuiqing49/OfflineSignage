#!/usr/bin/env python3
"""Validate common Figma-to-Android generation anti-patterns."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


SCREENSHOT_RE = re.compile(r"(screenshot|screen_shot|snapshot|page_capture|figma_reference)", re.I)
PLACEHOLDER_RE = re.compile(r"(placeholder|TODO|icon=\"@null\"|drawableTop)", re.I)
TEXTVIEW_RE = re.compile(r"<TextView\b[\s\S]*?(?=/?>)", re.I)
FIXED_DP_RE = re.compile(
    r'android:layout_(width|height)="(?:[1-9]\d*(?:\.\d+)?|0\.\d*[1-9]\d*)dp"',
    re.I,
)
DIMEN_XML_RE = re.compile(
    r'<dimen\s+name="(?P<name>[^"]+)">\s*(?P<value>-?\d+(?:\.\d+)?)\s*(?P<unit>dp|sp)\s*</dimen>',
    re.I,
)
DIMEN_TOKEN_RE = re.compile(r"`([^`]+)`")
TARGET_DIMENSION_RE = re.compile(
    r"(?P<start>\d+(?:\.\d+)?)(?:\s*-\s*(?P<end>\d+(?:\.\d+)?))?\s*(?P<unit>dp|sp)\b",
    re.I,
)
LIST_ADAPTER_RE = re.compile(r"(RecyclerView\.Adapter|ListAdapter<)")
RAW_FRAGMENT_RE = re.compile(r"(AppCompatActivity|:\s*Fragment\s*\()")
VIEWPAGER_RE = re.compile(r"(ViewPager2|androidx\.viewpager2\.widget\.ViewPager2)")
FRAGMENT_STATE_ADAPTER_RE = re.compile(r"FragmentStateAdapter")
FRAGMENT_DECL_RE = re.compile(r":\s*(?:BaseFragment|Fragment)\s*(?:<[^>]+>)?\s*\(|extends\s+(?:BaseFragment|Fragment)\b")
SYSTEM_BAR_RE = re.compile(
    r'(fitsSystemWindows="true"|SYSTEM_UI_FLAG|hide\(|systemBars\(|decorFitsSystemWindows|setStatusBarColor|setNavigationBarColor)',
    re.I,
)
TABLE_ROW_RE = re.compile(r"^\|(.+)\|$")
FIELD_RE = re.compile(r"^\s*[-*]\s*(?P<key>[^：:]+)[：:]\s*(?P<value>.*)$")
SYSTEM_BAR_STRATEGY_GROUPS = [
    ("Edge-to-edge/沉浸式", ("Edge-to-edge", "edge-to-edge", "沉浸式")),
    ("顶部状态栏/top inset", ("状态栏", "顶部安全区", "top inset", "status bar")),
    ("底部虚拟导航栏/bottom inset", ("底部虚拟导航栏", "虚拟导航栏", "bottom inset", "navigation bar")),
    ("Dialog/Popup 适配", ("Dialog", "Popup", "BottomSheet", "弹窗")),
    ("父子 Fragment Insets 分工", ("父子 Fragment Insets", "父子 Fragment", "父 Fragment", "子 Fragment")),
]
STRUCTURED_FIELDS = [
    "页面类型",
    "资源清单",
    "页面级资源清单",
    "项目基类 / 组件审计是否通过",
    "是否已扫描 core:core_base / AAR / 依赖基类",
    "是否已明确 Activity / Fragment / ViewModel / Adapter 基类采用结论",
    "页面入口链路合同是否通过",
    "Launcher / Host / Feature 职责是否明确",
    "是否已输出视觉高度拆分表",
    "是否已输出运行时视觉风险清单",
    "是否需要 ViewPager2 + Fragment",
    "是否需要 FragmentStateAdapter",
    "是否允许降级",
    "资源门禁是否通过",
    "是否存在缺失导航 / Tab / 工具栏图标",
]
BOOLEAN_FIELDS = [
    "项目基类 / 组件审计是否通过",
    "是否已扫描 core:core_base / AAR / 依赖基类",
    "是否已明确 Activity / Fragment / ViewModel / Adapter 基类采用结论",
    "页面入口链路合同是否通过",
    "Launcher / Host / Feature 职责是否明确",
    "是否已输出视觉高度拆分表",
    "是否已输出运行时视觉风险清单",
    "是否需要 ViewPager2 + Fragment",
    "是否需要 FragmentStateAdapter",
    "是否允许降级",
    "资源门禁是否通过",
    "是否存在缺失导航 / Tab / 工具栏图标",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate generated Android output from Figma workflows.")
    parser.add_argument("--module-src", action="append", default=[], help="Source directory to scan, e.g. app/src/main/java.")
    parser.add_argument("--module-res", action="append", default=[], help="Resource directory to scan, e.g. app/src/main/res.")
    parser.add_argument("--asset-manifest", help="Path to asset_manifest.md.")
    parser.add_argument("--analysis-report", help="Path to page analysis/task report markdown.")
    parser.add_argument("--figma-output-dir", help="Directory containing Figma screenshot, layer, normalization, and asset index artifacts.")
    parser.add_argument("--reference-screenshot", help="Explicit local Figma screenshot path. Defaults to figma_screenshot.* in the output directory.")
    parser.add_argument("--require-screen-adaptation", action="store_true", help="Require an Android screen adaptation plan in the analysis report.")
    parser.add_argument("--require-pager-navigation", action="store_true", help="Require ViewPager2 + FragmentStateAdapter + child Fragment implementation.")
    parser.add_argument("--dimension-tolerance-dp", type=float, default=2.0, help="Allowed absolute difference when landing Figma dimension contract into dimens.xml.")
    parser.add_argument("--allow-standard-adapter", action="store_true", help="Do not warn on RecyclerView.Adapter/ListAdapter usage.")
    parser.add_argument("--allow-raw-fragment", action="store_true", help="Do not warn on raw Fragment/AppCompatActivity usage.")
    return parser.parse_args()


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def iter_files(paths: list[str], suffixes: tuple[str, ...]) -> list[Path]:
    result: list[Path] = []
    for raw in paths:
        root = Path(raw)
        if root.is_file() and root.suffix.lower() in suffixes:
            result.append(root)
        elif root.is_dir():
            result.extend(path for path in root.rglob("*") if path.is_file() and path.suffix.lower() in suffixes)
    return result


def add_issue(issues: list[str], level: str, path: Path, message: str) -> None:
    issues.append(f"{level}: {path}: {message}")


def extract_fields(text: str) -> dict[str, str]:
    fields: dict[str, str] = {}
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped.startswith(("-", "*")):
            continue
        content = stripped[1:].strip()
        if "：" in content:
            key, value = content.split("：", 1)
        elif ": " in content:
            key, value = content.split(": ", 1)
        else:
            continue
        key = key.strip()
        value = value.strip().strip("`")
        fields.setdefault(key, value)
    return fields


def is_yes(value: str) -> bool:
    return value.strip().startswith(("是", "true", "True", "yes", "Yes"))


def is_no(value: str) -> bool:
    return value.strip().startswith(("否", "false", "False", "no", "No"))


def clean_path_value(value: str) -> str:
    return value.strip().strip("`").strip()


def validate_manifest(path: str | None, errors: list[str], warnings: list[str]) -> None:
    if not path:
        warnings.append("WARN: --asset-manifest was not provided; asset coverage was not checked.")
        return
    manifest = Path(path)
    if not manifest.exists():
        errors.append(f"ERROR: asset manifest does not exist: {manifest}")
        return
    text = read_text(manifest)
    required = ["Node ID", "Android", "状态"]
    for item in required:
        if item not in text:
            errors.append(f"ERROR: {manifest}: asset manifest is missing required column/content: {item}")


def parse_markdown_table(text: str) -> list[dict[str, str]]:
    tables: list[dict[str, str]] = []
    headers: list[str] | None = None
    for raw_line in text.splitlines():
        line = raw_line.strip()
        match = TABLE_ROW_RE.match(line)
        if not match:
            headers = None
            continue
        cells = [cell.strip().strip("`") for cell in match.group(1).split("|")]
        if headers is None:
            headers = cells
            continue
        if all(re.fullmatch(r":?-{3,}:?", cell) for cell in cells):
            continue
        if len(cells) != len(headers):
            continue
        tables.append(dict(zip(headers, cells)))
    return tables


def find_resource_file(module_res: list[str], target_dir: str, filename: str) -> Path | None:
    clean_filename = filename.strip().strip("`")
    if not clean_filename or any(token in clean_filename for token in ("示例", "*", "/")):
        return None
    if not re.search(r"\.(xml|png|webp|jpg|jpeg|svg)$", clean_filename, re.I):
        return None
    target_dirs = [part.strip().strip("`") for part in re.split(r"或|/", target_dir) if part.strip()]
    if not target_dirs:
        target_dirs = ["drawable", "drawable-nodpi", "mipmap-hdpi", "mipmap"]
    for raw_root in module_res:
        root = Path(raw_root)
        for directory in target_dirs:
            direct = root / directory / clean_filename
            if direct.exists():
                return direct
            matches = list(root.glob(f"{directory}*/{clean_filename}"))
            if matches:
                return matches[0]
    return None


def validate_manifest_resources(path: str | None, module_res: list[str], errors: list[str], warnings: list[str]) -> None:
    if not path:
        return
    manifest = Path(path)
    if not manifest.exists():
        return
    rows = parse_markdown_table(read_text(manifest))
    for row in rows:
        landed = row.get("是否已落地") or row.get("状态") or ""
        required = row.get("是否必需") or ""
        filename = row.get("Android 文件名") or row.get("Android 目标文件名") or ""
        target_dir = row.get("目标目录") or row.get("Android 目标目录") or ""
        if "已落地" not in landed and landed not in {"是", "true", "True"}:
            continue
        if not filename or "示例" in filename:
            continue
        found = find_resource_file(module_res, target_dir, filename)
        if not found:
            is_required = required in {"是", "必需", "true", "True"}
            level = errors if is_required else warnings
            prefix = "ERROR" if is_required else "WARN"
            level.append(f"{prefix}: {manifest}: landed resource was not found in module res dirs: {filename} ({target_dir})")


def validate_manifest_semantics(args: argparse.Namespace, errors: list[str]) -> None:
    if not args.asset_manifest or not args.analysis_report:
        return
    manifest = Path(args.asset_manifest)
    report = Path(args.analysis_report)
    if not manifest.exists() or not report.exists():
        return

    fields = extract_fields(read_text(report))
    downgrade_allowed = is_yes(fields.get("是否允许降级", ""))
    downgrade_plan = fields.get("用户确认的降级方案", "").strip()
    downgrade_confirmed = downgrade_allowed and bool(downgrade_plan) and downgrade_plan not in {"无", "未确认", "否"}
    missing_states = is_yes(fields.get("是否存在缺失导航 / Tab / 工具栏图标", ""))

    if missing_states and not downgrade_confirmed:
        errors.append(
            f"ERROR: {report}: required navigation/Tab/toolbar assets are missing and no confirmed downgrade is recorded."
        )

    for row in parse_markdown_table(read_text(manifest)):
        required = row.get("是否必需") or ""
        if required not in {"是", "必需", "true", "True"}:
            continue
        landed = row.get("是否已落地") or row.get("状态") or ""
        if landed not in {"是", "true", "True"} and "已落地" not in landed:
            errors.append(
                f"ERROR: {manifest}: required asset is not landed: {row.get('节点名称') or row.get('资源来源节点名称') or 'unknown'}"
            )
        note = " ".join(
            row.get(key, "") for key in ("处理建议", "缺失原因和处理建议", "说明", "状态")
        )
        if re.search(r"缺\s*(?:normal|selected|pressed)|缺失.*(?:状态|图标)", note, re.I) and not downgrade_confirmed:
            errors.append(
                f"ERROR: {manifest}: required state asset is incomplete without confirmed downgrade: "
                f"{row.get('Android 文件名') or row.get('节点名称') or 'unknown'}"
            )


def resolve_figma_output_dir(args: argparse.Namespace) -> Path | None:
    if args.figma_output_dir:
        return Path(args.figma_output_dir)
    if args.analysis_report:
        return Path(args.analysis_report).parent
    return None


def validate_reference_artifacts(args: argparse.Namespace, errors: list[str]) -> None:
    if not args.require_screen_adaptation:
        return
    output_dir = resolve_figma_output_dir(args)
    if not output_dir or not output_dir.exists():
        errors.append("ERROR: strong Figma validation requires an existing --figma-output-dir.")
        return

    required_files = ["figma_layer_report.md", "figma_normalize_report.md", "figma_asset_index.json"]
    for filename in required_files:
        if not (output_dir / filename).is_file():
            errors.append(f"ERROR: required Figma artifact is missing: {output_dir / filename}")

    if args.reference_screenshot:
        screenshot = Path(args.reference_screenshot)
        screenshots = [screenshot] if screenshot.is_file() else []
    else:
        screenshots = [
            path
            for pattern in ("figma_screenshot.png", "figma_screenshot.jpg", "figma_screenshot.jpeg", "figma_screenshot.webp")
            for path in [output_dir / pattern]
            if path.is_file()
        ]
    if not screenshots:
        errors.append(
            f"ERROR: local raster Figma screenshot is missing: {output_dir / 'figma_screenshot.*'}; "
            "inline-only screenshots cannot be validated."
        )


def parse_actual_dimens(module_res: list[str]) -> dict[str, tuple[float, str, Path]]:
    dimens: dict[str, tuple[float, str, Path]] = {}
    for raw_root in module_res:
        root = Path(raw_root)
        if not root.exists():
            continue
        for path in root.glob("values*/dimens.xml"):
            text = read_text(path)
            for match in DIMEN_XML_RE.finditer(text):
                name = match.group("name")
                value = float(match.group("value"))
                unit = match.group("unit").lower()
                dimens[name] = (value, unit, path)
    return dimens


def is_dimen_name(token: str) -> bool:
    token = token.strip()
    if not token or any(item in token for item in (".", "/", " ", "*")):
        return False
    if token in {"match_parent", "wrap_content", "无"}:
        return False
    if token.startswith(("TextAppearance", "Widget", "style")):
        return False
    return True


def expand_dimen_names(name_cell: str) -> list[str]:
    names: list[str] = []
    tokens = DIMEN_TOKEN_RE.findall(name_cell) or [name_cell.strip().strip("`")]
    for token in tokens:
        token = token.strip()
        if "/" in token:
            first, *suffixes = token.split("/")
            if is_dimen_name(first):
                names.append(first)
                prefix = first.rsplit("_", 1)[0] if "_" in first else ""
                names.extend(f"{prefix}_{suffix}" for suffix in suffixes if prefix and is_dimen_name(suffix))
        elif is_dimen_name(token):
            names.append(token)
    return names


def extract_target_dimensions(target_cell: str) -> list[tuple[float, float, str]]:
    pair = re.fullmatch(
        r"\s*(\d+(?:\.\d+)?)\s*[x×]\s*(\d+(?:\.\d+)?)\s*(dp|sp)\s*",
        target_cell,
        re.I,
    )
    if pair:
        unit = pair.group(3).lower()
        return [(float(pair.group(1)), float(pair.group(1)), unit), (float(pair.group(2)), float(pair.group(2)), unit)]
    return [
        (
            float(match.group("start")),
            float(match.group("end")) if match.group("end") else float(match.group("start")),
            match.group("unit").lower(),
        )
        for match in TARGET_DIMENSION_RE.finditer(target_cell)
    ]


def extract_dimen_contracts(text: str) -> list[dict[str, object]]:
    contracts: list[dict[str, object]] = []
    for row in parse_markdown_table(text):
        name_cell = row.get("dimen/style 名") or row.get("dimen 名") or row.get("资源名") or ""
        target_cell = row.get("Android dp/sp") or row.get("Android dp") or row.get("目标 dp/sp") or ""
        if not name_cell or not target_cell:
            continue
        names = expand_dimen_names(name_cell)
        if not names:
            names = [
                token.strip().strip("`")
                for token in re.split(r"\s*/\s*|或|,|，", name_cell)
                if is_dimen_name(token.strip().strip("`"))
            ]
        targets = extract_target_dimensions(target_cell)
        if not names or not targets:
            continue
        if len(targets) == len(names):
            pairs = zip(names, targets)
        elif len(targets) == 1:
            pairs = ((name, targets[0]) for name in names)
        else:
            pairs = zip(names, targets[-len(names):])
        for name, (min_value, max_value, unit) in pairs:
            contracts.append(
                {
                    "name": name,
                    "min": min(min_value, max_value),
                    "max": max(min_value, max_value),
                    "unit": unit,
                    "purpose": row.get("用途") or "",
                    "exception": row.get("固定尺寸例外") or "",
                    "note": row.get("说明") or "",
                }
            )
    return contracts


def validate_dimension_contracts(args: argparse.Namespace, errors: list[str], warnings: list[str]) -> None:
    if not args.require_screen_adaptation or not args.analysis_report:
        return
    report = Path(args.analysis_report)
    if not report.exists():
        return
    contracts = extract_dimen_contracts(read_text(report))
    if not contracts:
        errors.append(f"ERROR: {report}: no landed dimen contracts found in key dimension conversion table.")
        return
    actual_dimens = parse_actual_dimens(args.module_res)
    for contract in contracts:
        name = str(contract["name"])
        actual = actual_dimens.get(name)
        if not actual:
            warnings.append(f"WARN: {report}: dimen contract is declared but not found in provided res dirs: {name}")
            continue
        value, unit, path = actual
        expected_unit = str(contract["unit"])
        if unit != expected_unit:
            errors.append(f"ERROR: {path}: {name} uses {unit}, but Figma dimension contract expects {expected_unit}.")
            continue
        min_value = float(contract["min"]) - args.dimension_tolerance_dp
        max_value = float(contract["max"]) + args.dimension_tolerance_dp
        if value < min_value or value > max_value:
            errors.append(
                f"ERROR: {path}: {name}={value:g}{unit} does not match Figma dimension contract "
                f"expected {contract['min']:g}-{contract['max']:g}{unit} "
                f"(tolerance +/-{args.dimension_tolerance_dp:g})."
            )


def validate_analysis_report(args: argparse.Namespace, errors: list[str], warnings: list[str]) -> None:
    if not args.analysis_report:
        if args.require_screen_adaptation:
            errors.append("ERROR: --require-screen-adaptation needs --analysis-report.")
        return
    report = Path(args.analysis_report)
    if not report.exists():
        errors.append(f"ERROR: analysis report does not exist: {report}")
        return
    text = read_text(report)
    fields = extract_fields(text)
    if args.require_screen_adaptation:
        required = ["屏幕适配", "Figma", "Android 基准", "换算", "Insets"]
        for item in required:
            if item not in text:
                errors.append(f"ERROR: {report}: screen adaptation report is missing required content: {item}")
        required_groups = [
            ("Figma logical screen", ["Figma 逻辑", "逻辑屏幕"]),
            ("project base audit", ["项目基类 / 组件审计", "基类审计", "BaseFragment", "BaseActivity"]),
            ("entry chain contract", ["页面入口链路", "Launcher", "Host", "Feature 页面"]),
            ("immersive edge-to-edge", ["沉浸式", "Edge-to-edge"]),
            ("bottom navigation bar inset", ["底部虚拟导航栏", "虚拟导航栏"]),
            ("visual height split", ["视觉高度拆分", "业务视觉高度"]),
            ("insets owner contract", ["Insets owner", "Insets Owner", "唯一归属", "单 owner"]),
            ("runtime visual risk checklist", ["运行时视觉风险", "顶部 status inset", "navigation inset", "FAB"]),
            ("dimension conversion table", ["关键尺寸换算表", "Figma px"]),
            ("width baseline and height fill contract", ["宽度基准与高度填充", "高度填充", "填充剩余高度"]),
            ("layer bounds table", ["关键图层 Bounds", "Bounds 表"]),
            ("ui workflow alignment", ["UI 工作流对齐"]),
            ("ui workflow tab pager", ["Tab / ViewPager2", "Tab/ViewPager2", "ViewPager2"]),
            ("ui workflow recycler scroll", ["RecyclerView / Scroll", "RecyclerView/Scroll", "RecyclerView"]),
            ("ui workflow text overflow", ["文字字号 / 省略", "文字字号/省略", "字体放大", "ellipsize"]),
            ("ui workflow resource style", ["资源样式", "dimen", "style"]),
        ]
        for label, options in required_groups:
            if not any(item in text for item in options):
                errors.append(f"ERROR: {report}: screen adaptation report is missing required content group: {label}")
        required_fields = [
            ("figma logical screen field", "Figma 逻辑屏幕"),
            ("android baseline width field", "Android 基准宽度"),
            ("launcher activity field", "Launcher Activity"),
            ("host activity fragment field", "Host Activity"),
            ("target feature page field", "目标 Feature 页面"),
            ("route entry field", "创建 / 路由入口"),
            ("base audit field", "项目基类 / 组件审计"),
            ("width baseline height fill field", "宽度基准与高度填充"),
            ("immersive field", "沉浸式"),
            ("bottom navigation bar field", "底部虚拟导航栏"),
            ("visual height split field", "视觉高度拆分"),
            ("insets owner field", "Insets owner"),
            ("runtime visual risk field", "运行时视觉风险"),
            ("dialog popup field", "Dialog"),
            ("parent child fragment insets field", "父子 Fragment Insets"),
            ("ui workflow alignment field", "UI 工作流对齐"),
        ]
        for label, item in required_fields:
            if item not in text:
                errors.append(f"ERROR: {report}: page task is missing required field/content: {label}")
        height_fill_tokens = [
            "match_parent",
            "0dp",
            "约束",
            "剩余高度",
            "弹性内容区",
            "固定视觉块",
        ]
        for token in height_fill_tokens:
            if token not in text:
                errors.append(f"ERROR: {report}: width baseline / height fill contract is missing token: {token}")
        forbidden_height_patterns = [
            "Frame 高度等比",
            "写死 Frame 高度",
            "根布局固定高度",
        ]
        if any(pattern in text for pattern in forbidden_height_patterns) and "禁止" not in text:
            warnings.append(f"WARN: {report}: report mentions fixed/equal Frame height but does not clearly mark it as forbidden.")
        for field in STRUCTURED_FIELDS:
            value = fields.get(field, "")
            if not value:
                errors.append(f"ERROR: {report}: page task is missing structured field: {field}")
        for field in BOOLEAN_FIELDS:
            value = fields.get(field, "")
            if value and not (is_yes(value) or is_no(value)):
                errors.append(f"ERROR: {report}: structured field must start with 是/否: {field}={value}")
        needs_pager = fields.get("是否需要 ViewPager2 + Fragment", "")
        needs_adapter = fields.get("是否需要 FragmentStateAdapter", "")
        if is_yes(needs_pager) and is_no(needs_adapter):
            errors.append(f"ERROR: {report}: ViewPager2 + Fragment requires FragmentStateAdapter.")
        if is_no(fields.get("项目基类 / 组件审计是否通过", "")):
            errors.append(f"ERROR: {report}: project base/component audit did not pass.")
        if is_no(fields.get("页面入口链路合同是否通过", "")):
            errors.append(f"ERROR: {report}: page entry chain contract did not pass.")
        if is_no(fields.get("是否已输出运行时视觉风险清单", "")):
            errors.append(f"ERROR: {report}: runtime visual risk checklist is missing.")
        launcher_value = fields.get("Launcher Activity", "")
        host_value = fields.get("Host Activity / Fragment", "")
        target_value = fields.get("目标 Feature 页面", "")
        splash_requested = re.search(r"(新增\s*Splash|启动页|SplashActivity)", text, re.I)
        if splash_requested and launcher_value and "Splash" not in launcher_value:
            errors.append(f"ERROR: {report}: Splash is mentioned but Launcher Activity is not Splash.")
        if not host_value or host_value in {"不确定", "无", "TODO"}:
            errors.append(f"ERROR: {report}: Host Activity / Fragment must be explicitly declared.")
        if not target_value or target_value in {"不确定", "无", "TODO"}:
            errors.append(f"ERROR: {report}: target Feature page must be explicitly declared.")
        if is_yes(fields.get("是否允许降级", "")) and "用户确认的降级方案" not in text:
            errors.append(f"ERROR: {report}: downgrade is allowed but user confirmation field is missing.")
        if args.asset_manifest:
            declared_manifest = clean_path_value(fields.get("页面级资源清单") or fields.get("资源清单") or "")
            if declared_manifest and not declared_manifest.endswith(Path(args.asset_manifest).name):
                warnings.append(
                    f"WARN: {report}: analysis report declares resource manifest {declared_manifest}, "
                    f"but validator received {args.asset_manifest}."
                )


def get_system_bar_strategy_gaps(analysis_report: str | None) -> list[str]:
    if not analysis_report:
        return [label for label, _ in SYSTEM_BAR_STRATEGY_GROUPS]
    report = Path(analysis_report)
    if not report.exists():
        return [label for label, _ in SYSTEM_BAR_STRATEGY_GROUPS]
    text = read_text(report)
    gaps: list[str] = []
    for label, tokens in SYSTEM_BAR_STRATEGY_GROUPS:
        if not any(token in text for token in tokens):
            gaps.append(label)
    return gaps


def format_system_bar_warning(strategy_gaps: list[str]) -> str:
    if not strategy_gaps:
        return "system bar API/config found; Edge-to-edge/Insets strategy is documented."
    return "system bar API/config found; document Edge-to-edge/Insets strategy first. Missing: " + ", ".join(strategy_gaps)


def validate_xml(files: list[Path], errors: list[str], warnings: list[str], system_bar_strategy_gaps: list[str]) -> None:
    for path in files:
        text = read_text(path)
        if SCREENSHOT_RE.search(text):
            add_issue(errors, "ERROR", path, "screenshot-like resource name found; page body must not be implemented as a screenshot.")
        if PLACEHOLDER_RE.search(text):
            add_issue(warnings, "WARN", path, "placeholder/TODO/null icon pattern found; verify this is not silently replacing Figma assets.")
        if SYSTEM_BAR_RE.search(text) and system_bar_strategy_gaps:
            add_issue(warnings, "WARN", path, format_system_bar_warning(system_bar_strategy_gaps))
        for match in TEXTVIEW_RE.finditer(text):
            tag = match.group(0)
            if FIXED_DP_RE.search(tag):
                add_issue(errors, "ERROR", path, "TextView has fixed dp width/height risk.")


def validate_code(files: list[Path], args: argparse.Namespace, warnings: list[str], system_bar_strategy_gaps: list[str]) -> None:
    for path in files:
        text = read_text(path)
        if SCREENSHOT_RE.search(text):
            add_issue(warnings, "WARN", path, "screenshot/figma_reference naming found in code; verify it is only used for review.")
        if SYSTEM_BAR_RE.search(text) and system_bar_strategy_gaps:
            add_issue(warnings, "WARN", path, format_system_bar_warning(system_bar_strategy_gaps))
        if not args.allow_standard_adapter and LIST_ADAPTER_RE.search(text):
            add_issue(warnings, "WARN", path, "standard Adapter usage found; verify no project Adapter wrapper should be reused.")
        if not args.allow_raw_fragment and RAW_FRAGMENT_RE.search(text):
            add_issue(warnings, "WARN", path, "raw Fragment/AppCompatActivity usage found; verify no project page base class should be reused.")


def validate_pager_navigation(src_files: list[Path], res_files: list[Path], errors: list[str]) -> None:
    text = "\n".join(read_text(path) for path in [*src_files, *res_files])
    if not VIEWPAGER_RE.search(text):
        errors.append("ERROR: required pager navigation is missing ViewPager2.")
    if not FRAGMENT_STATE_ADAPTER_RE.search(text):
        errors.append("ERROR: required pager navigation is missing FragmentStateAdapter.")
    fragment_count = len(FRAGMENT_DECL_RE.findall(text))
    if fragment_count < 2:
        errors.append("ERROR: required pager navigation needs at least 2 child Fragment classes.")


def main() -> int:
    args = parse_args()
    errors: list[str] = []
    warnings: list[str] = []

    validate_manifest(args.asset_manifest, errors, warnings)
    validate_manifest_resources(args.asset_manifest, args.module_res, errors, warnings)
    validate_manifest_semantics(args, errors)
    validate_analysis_report(args, errors, warnings)
    validate_reference_artifacts(args, errors)
    validate_dimension_contracts(args, errors, warnings)
    system_bar_strategy_gaps = get_system_bar_strategy_gaps(args.analysis_report)
    res_files = iter_files(args.module_res, (".xml",))
    src_files = iter_files(args.module_src, (".kt", ".java"))
    validate_xml(res_files, errors, warnings, system_bar_strategy_gaps)
    validate_code(src_files, args, warnings, system_bar_strategy_gaps)
    if args.require_pager_navigation:
        validate_pager_navigation(src_files, res_files, errors)

    for item in warnings:
        print(item)
    for item in errors:
        print(item)

    if errors:
        print(f"validation failed: {len(errors)} error(s), {len(warnings)} warning(s)")
        return 1
    print(f"validation passed: {len(warnings)} warning(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
