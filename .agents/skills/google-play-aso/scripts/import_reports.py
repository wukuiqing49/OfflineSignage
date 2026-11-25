"""离线解析用户提供的 Play CSV，校验包名与维度，不访问网络。"""
from __future__ import annotations
import argparse
from datetime import date, datetime, timezone
import hashlib
from pathlib import Path
import uuid
from googleplay_aso.apps import is_package_name
from googleplay_aso.report_io import decode_report
from googleplay_aso.installs import read_install_rows, summarize_installs
from googleplay_aso.health_reports import read_overview_rows, summarize_overview
from googleplay_aso.store_performance import read_store_performance_rows, summarize_search_terms
from project_context import locate_project
from run_connector import positive, save, emit


def main(argv: list[str] | None = None) -> int:
    """解析同一种报表的多个月份，记录来源哈希和真实覆盖区间。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path)
    parser.add_argument("--app", required=True)
    parser.add_argument("--kind", required=True, choices=("installs", "search", "ratings", "crashes"))
    parser.add_argument("--csv", action="append", type=Path, required=True)
    parser.add_argument("--days", type=positive, default=30)
    parser.add_argument("--end-date", type=date.fromisoformat, default=date.today())
    args = parser.parse_args(argv)
    try:
        if not is_package_name(args.app):
            raise ValueError("包名格式无效")
        project, _ = locate_project(args.project)
        rows, sources = [], []
        for path in args.csv:
            raw = path.read_bytes()
            text = decode_report(raw)
            sources.append({"filename": path.name, "sha256": hashlib.sha256(raw).hexdigest()})
            if args.kind == "installs":
                rows.extend(read_install_rows(text, args.app))
            elif args.kind == "search":
                rows.extend(read_store_performance_rows(text, args.app))
            else:
                rows.extend(read_overview_rows(text, args.app, args.kind))
        options = {"days": args.days, "today": args.end_date}
        if args.kind == "installs":
            summary = summarize_installs(rows, **options)
        elif args.kind == "search":
            summary = summarize_search_terms(rows, **options)
        else:
            summary = summarize_overview(rows, args.kind, **options)
        summary["source"] = f"用户提供的 Play Console {args.kind} CSV；未进行在线取证"
        base = project / ".google-play-aso"
        if base.is_symlink() or not base.resolve().is_relative_to(project.resolve()):
            raise ValueError("输出目录必须位于所属项目内")
        base.mkdir(exist_ok=True)
        (base / ".gitignore").write_text("*\n", encoding="utf-8")
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:10]
        folder = base / "runs" / ("offline-" + stamp) / args.app
        folder.mkdir(parents=True)
        target = folder / (args.kind + ".json")
        save(target, {"packageName": args.app, "mode": "offline_import", "analysisDays": args.days,
                      "importedAt": datetime.now(timezone.utc).isoformat(), "sources": sources, "summary": summary})
        emit({"status": "ok", "evidenceFile": str(target), "mode": "offline_import"})
        return 0
    except (OSError, ValueError, KeyError):
        emit({"error": "invalid_export", "message": "检查文件编码、报表列、日期、包名及重复记录；不猜测缺失数据"})
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
