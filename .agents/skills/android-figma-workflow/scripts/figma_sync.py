#!/usr/bin/env python3
"""Fetch Figma node data and create layer/resource reports.

This script intentionally stays dependency-free so the workflow can travel
between Android projects. It uses the official Figma REST API when a token is
available, and can also regenerate reports from existing JSON with
--report-existing.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any


FIGMA_API = "https://api.figma.com/v1"
IMAGE_NODE_TYPES = {
    "VECTOR",
    "BOOLEAN_OPERATION",
    "STAR",
    "LINE",
    "ELLIPSE",
    "POLYGON",
    "REGULAR_POLYGON",
}


@dataclass
class Options:
    file_key: str | None
    node_id: str | None
    figma_url: str | None
    out_dir: Path
    asset_dir: Path
    report_existing: bool
    image_format: str
    export_node_assets: bool
    token: str | None


def parse_args() -> Options:
    parser = argparse.ArgumentParser(description="Fetch Figma node data and generate Android handoff reports.")
    parser.add_argument("--figma-url", help="Figma file/frame URL. file key and node id will be parsed when possible.")
    parser.add_argument("--file-key", help="Figma file key. Defaults to FIGMA_FILE_KEY.")
    parser.add_argument("--node-id", help="Figma node id. Defaults to FIGMA_NODE_ID or node-id in --figma-url.")
    parser.add_argument("--out-dir", default=".ai-work/figma/output", help="Directory for JSON and markdown reports.")
    parser.add_argument("--asset-dir", default=".ai-work/figma/output/assets", help="Directory for downloaded images/SVGs.")
    parser.add_argument("--report-existing", action="store_true", help="Generate reports from existing figma_node.json.")
    parser.add_argument("--image-format", default="png", choices=("png", "jpg", "svg", "pdf"), help="Export format for target node screenshot/resource.")
    parser.add_argument(
        "--no-export-node-assets",
        action="store_true",
        help="Skip PNG exports for image-fill and icon-like child nodes.",
    )
    args = parser.parse_args()

    file_key, node_id = parse_figma_url(args.figma_url)
    return Options(
        file_key=args.file_key or os.getenv("FIGMA_FILE_KEY") or file_key,
        node_id=args.node_id or os.getenv("FIGMA_NODE_ID") or node_id,
        figma_url=args.figma_url,
        out_dir=Path(args.out_dir),
        asset_dir=Path(args.asset_dir),
        report_existing=args.report_existing,
        image_format=args.image_format,
        export_node_assets=not args.no_export_node_assets,
        token=os.getenv("FIGMA_TOKEN"),
    )


def parse_figma_url(url: str | None) -> tuple[str | None, str | None]:
    if not url:
        return None, None
    parsed = urllib.parse.urlparse(url)
    parts = [part for part in parsed.path.split("/") if part]
    file_key = None
    for index, part in enumerate(parts):
        if part in {"file", "design"} and index + 1 < len(parts):
            file_key = parts[index + 1]
            break
    query = urllib.parse.parse_qs(parsed.query)
    node_id = query.get("node-id", [None])[0]
    if node_id:
        node_id = node_id.replace("-", ":")
    return file_key, node_id


def api_get(path: str, token: str) -> Any:
    request = urllib.request.Request(
        f"{FIGMA_API}{path}",
        headers={"X-Figma-Token": token},
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Figma API error {exc.code}: {body}") from exc


def download(url: str, dest: Path) -> str:
    dest.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(url, timeout=60) as response:
        content = response.read()
    dest.write_bytes(content)
    return hashlib.sha256(content).hexdigest()


def fetch_figma(options: Options) -> dict[str, Any]:
    if not options.token:
        raise SystemExit("Missing FIGMA_TOKEN. Set it or use --report-existing with existing JSON.")
    if not options.file_key:
        raise SystemExit("Missing Figma file key. Pass --file-key or --figma-url.")

    if options.node_id:
        node_path = f"/files/{options.file_key}/nodes?ids={urllib.parse.quote(options.node_id)}"
    else:
        node_path = f"/files/{options.file_key}"
    data = api_get(node_path, options.token)
    write_json(options.out_dir / "figma_node.json", data)

    if options.node_id:
        image_path = (
            f"/images/{options.file_key}?ids={urllib.parse.quote(options.node_id)}"
            f"&format={options.image_format}&use_absolute_bounds=true"
        )
        images = api_get(image_path, options.token)
        write_json(options.out_dir / "figma_images.json", images)
        image_url = images.get("images", {}).get(options.node_id)
        if image_url:
            suffix = "svg" if options.image_format == "svg" else options.image_format
            screenshot_path = options.out_dir / f"figma_screenshot.{suffix}"
            digest = download(image_url, screenshot_path)
            write_json(
                options.out_dir / "figma_capture.json",
                {
                    "fileKey": options.file_key,
                    "nodeId": options.node_id,
                    "format": options.image_format,
                    "path": screenshot_path.name,
                    "sha256": digest,
                },
            )

    return data


def write_json(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def load_existing(out_dir: Path) -> dict[str, Any]:
    path = out_dir / "figma_node.json"
    if not path.exists():
        raise SystemExit(f"Missing existing JSON: {path}")
    return json.loads(path.read_text(encoding="utf-8-sig"))


def iter_nodes(data: Any) -> list[dict[str, Any]]:
    roots: list[dict[str, Any]] = []
    if isinstance(data, dict) and "nodes" in data:
        for item in data.get("nodes", {}).values():
            document = item.get("document")
            if isinstance(document, dict):
                roots.append(document)
    elif isinstance(data, dict) and "document" in data:
        roots.append(data["document"])
    elif isinstance(data, dict):
        roots.append(data)

    result: list[dict[str, Any]] = []

    def walk(node: dict[str, Any], depth: int = 0) -> None:
        node["_depth"] = depth
        result.append(node)
        for child in node.get("children", []) or []:
            if isinstance(child, dict):
                walk(child, depth + 1)

    for root in roots:
        walk(root)
    return result


def node_bounds(node: dict[str, Any]) -> str:
    box = node.get("absoluteBoundingBox") or node.get("absoluteRenderBounds") or {}
    if not isinstance(box, dict):
        return ""
    width = box.get("width")
    height = box.get("height")
    x = box.get("x")
    y = box.get("y")
    if width is None or height is None:
        return ""
    return f"{width:.1f}x{height:.1f} @ {x:.1f},{y:.1f}" if x is not None and y is not None else f"{width:.1f}x{height:.1f}"


def has_image_fill(node: dict[str, Any]) -> bool:
    for fill in node.get("fills", []) or []:
        if isinstance(fill, dict) and fill.get("type") == "IMAGE":
            return True
    return False


def looks_like_icon(node: dict[str, Any]) -> bool:
    name = (node.get("name") or "").lower()
    node_type = node.get("type")
    box = node.get("absoluteBoundingBox") or {}
    width = float(box.get("width") or 0)
    height = float(box.get("height") or 0)
    name_hint = any(word in name for word in ("icon", "ic_", "nav", "tab", "arrow", "close", "search"))
    size_hint = 0 < width <= 96 and 0 < height <= 96
    return node_type in IMAGE_NODE_TYPES and (name_hint or size_hint)


def safe_name(value: str) -> str:
    value = value.strip().lower()
    value = re.sub(r"[^a-z0-9_:-]+", "_", value)
    value = value.replace(":", "_").replace("-", "_")
    return value.strip("_") or "node"


def android_name(node: dict[str, Any], prefix: str) -> str:
    return f"{prefix}_{safe_name(node.get('name') or node.get('id') or 'asset')}"


def export_node_assets(options: Options, nodes: list[dict[str, Any]]) -> None:
    if not options.export_node_assets or not options.token or not options.file_key:
        return

    candidates = [node for node in nodes if node.get("id") and (has_image_fill(node) or looks_like_icon(node))]
    index: list[dict[str, Any]] = []
    for offset in range(0, len(candidates), 50):
        chunk = candidates[offset : offset + 50]
        ids = ",".join(str(node["id"]) for node in chunk)
        path = f"/images/{options.file_key}?ids={urllib.parse.quote(ids, safe=',')}&format=png&use_absolute_bounds=true"
        exported = api_get(path, options.token).get("images", {})
        for node in chunk:
            node_id = str(node["id"])
            url = exported.get(node_id)
            if not url:
                index.append(asset_index_entry(node, None, None, "导出 URL 缺失"))
                continue
            filename = f"{safe_name(node.get('name') or 'asset')}__{safe_name(node_id)}.png"
            destination = options.asset_dir / filename
            try:
                digest = download(url, destination)
                index.append(asset_index_entry(node, destination, digest, "已导出"))
            except (OSError, urllib.error.URLError) as exc:
                index.append(asset_index_entry(node, destination, None, f"导出失败: {exc}"))

    write_json(options.out_dir / "figma_asset_index.json", index)
    write_asset_index_report(options.out_dir, index)


def asset_index_entry(
    node: dict[str, Any], path: Path | None, digest: str | None, status: str
) -> dict[str, Any]:
    box = node.get("absoluteBoundingBox") or node.get("absoluteRenderBounds") or {}
    return {
        "nodeId": node.get("id"),
        "nodeName": node.get("name"),
        "nodeType": node.get("type"),
        "width": box.get("width") if isinstance(box, dict) else None,
        "height": box.get("height") if isinstance(box, dict) else None,
        "hasImageFill": has_image_fill(node),
        "iconLike": looks_like_icon(node),
        "exportPath": str(path) if path else None,
        "sha256": digest,
        "status": status,
    }


def write_asset_index_report(out_dir: Path, index: list[dict[str, Any]]) -> None:
    lines = [
        "# Figma 节点资源索引",
        "",
        "| Node ID | 节点名称 | 类型 | 原始尺寸 | 导出路径 | SHA-256 | 状态 |",
        "| --- | --- | --- | --- | --- | --- | --- |",
    ]
    for item in index:
        width = item.get("width")
        height = item.get("height")
        size = f"{width}x{height}" if width is not None and height is not None else ""
        digest = str(item.get("sha256") or "")
        lines.append(
            "| `{node_id}` | {name} | {node_type} | {size} | `{path}` | `{digest}` | {status} |".format(
                node_id=item.get("nodeId") or "",
                name=str(item.get("nodeName") or "").replace("|", "\\|"),
                node_type=item.get("nodeType") or "",
                size=size,
                path=item.get("exportPath") or "",
                digest=digest[:16],
                status=str(item.get("status") or "").replace("|", "\\|"),
            )
        )
    (out_dir / "figma_asset_index.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_layer_report(nodes: list[dict[str, Any]], out_dir: Path) -> None:
    lines = [
        "# Figma 图层结构报告",
        "",
        "| 深度 | Node ID | 类型 | 名称 | 尺寸 | 文本 |",
        "| --- | --- | --- | --- | --- | --- |",
    ]
    for node in nodes:
        text = (node.get("characters") or "").replace("\n", " ")
        if len(text) > 80:
            text = text[:77] + "..."
        lines.append(
            "| {depth} | `{id}` | {type} | {name} | {bounds} | {text} |".format(
                depth=node.get("_depth", 0),
                id=node.get("id", ""),
                type=node.get("type", ""),
                name=(node.get("name") or "").replace("|", "\\|"),
                bounds=node_bounds(node),
                text=text.replace("|", "\\|"),
            )
        )
    (out_dir / "figma_layer_report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_asset_manifest(nodes: list[dict[str, Any]], out_dir: Path) -> None:
    lines = [
        "# Figma 资源清单",
        "",
        "| 节点名称 | Node ID | 类型 | Android 文件名 | 目标目录 | 使用位置 | 状态 | 处理建议 |",
        "| --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    count = 0
    for node in nodes:
        resource_type = None
        target_dir = "drawable"
        prefix = "figma_asset"
        if has_image_fill(node):
            resource_type = "Image"
            target_dir = "drawable-nodpi"
            prefix = "figma_img"
        elif looks_like_icon(node):
            resource_type = "Vector/Icon"
            prefix = "figma_ic"

        if not resource_type:
            continue
        count += 1
        name = android_name(node, prefix)
        lines.append(
            "| {node_name} | `{node_id}` | {resource_type} | `{android}.xml/png/webp` | `{target_dir}` | 待确认 | 待落地 | 从 Figma 导出，禁止静默占位替代 |".format(
                node_name=(node.get("name") or "").replace("|", "\\|"),
                node_id=node.get("id", ""),
                resource_type=resource_type,
                android=name,
                target_dir=target_dir,
            )
        )
    if count == 0:
        lines.append("| 无自动识别资源 | - | - | - | - | - | - | 人工检查导航、Tab、工具栏和操作按钮图标 |")
    (out_dir / "asset_manifest.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_summary(options: Options, nodes: list[dict[str, Any]]) -> None:
    frame_count = sum(1 for node in nodes if node.get("type") in {"FRAME", "COMPONENT", "INSTANCE"})
    text_count = sum(1 for node in nodes if node.get("type") == "TEXT")
    icon_count = sum(1 for node in nodes if looks_like_icon(node))
    image_count = sum(1 for node in nodes if has_image_fill(node))
    lines = [
        "# Figma 拉取摘要",
        "",
        f"- file key: `{options.file_key or ''}`",
        f"- node id: `{options.node_id or ''}`",
        f"- node count: {len(nodes)}",
        f"- frame/component count: {frame_count}",
        f"- text count: {text_count}",
        f"- icon-like vector count: {icon_count}",
        f"- image fill count: {image_count}",
        "",
        "## 下一步",
        "",
        "1. 先阅读 `figma_layer_report.md` 和 `asset_manifest.md`。",
        "2. 确认导航、Tab、工具栏和操作按钮图标是否完整。",
        "3. 只有资源门禁通过后，才进入代码生成。",
    ]
    (options.out_dir / "figma_extract_report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    options = parse_args()
    options.out_dir.mkdir(parents=True, exist_ok=True)
    options.asset_dir.mkdir(parents=True, exist_ok=True)

    data = load_existing(options.out_dir) if options.report_existing else fetch_figma(options)
    nodes = iter_nodes(data)
    if not options.report_existing:
        export_node_assets(options, nodes)
    write_layer_report(nodes, options.out_dir)
    write_asset_manifest(nodes, options.out_dir)
    write_summary(options, nodes)

    print(f"wrote {options.out_dir / 'figma_node.json'}")
    print(f"wrote {options.out_dir / 'figma_layer_report.md'}")
    print(f"wrote {options.out_dir / 'asset_manifest.md'}")
    print(f"wrote {options.out_dir / 'figma_extract_report.md'}")
    screenshot_files = list(options.out_dir.glob("figma_screenshot.*"))
    if screenshot_files:
        print(f"wrote {screenshot_files[0]}")
    if (options.out_dir / "figma_asset_index.md").exists():
        print(f"wrote {options.out_dir / 'figma_asset_index.md'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
