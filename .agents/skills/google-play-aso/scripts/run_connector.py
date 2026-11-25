#!/usr/bin/env python3
"""所属项目的独立 ASO 入口；只调用 Skill 内置引擎。"""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import importlib
import json
from pathlib import Path
import re
import subprocess
import sys
import uuid
from googleplay_aso.aso import LISTING_LIMITS, validate_listing_text
from googleplay_aso.apps import load_app_config, load_reports_bucket, is_package_name
from project_context import locate_project, inspect_project


def positive(value: str) -> int:
    """限制周期、样本数和单 App 超时为正整数。"""
    number = int(value)
    if number <= 0:
        raise argparse.ArgumentTypeError("必须为正整数")
    return number


def emit(value: dict) -> None:
    """输出结构化结果，不包含认证路径或原始异常。"""
    print(json.dumps(value, ensure_ascii=False, indent=2))


def save(path: Path, value: dict) -> None:
    """原子替换本次运行的 JSON，保留已经完成的 App。"""
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(path)


def check_dependencies() -> dict:
    """实际导入在线所需依赖；不读取凭据、不发送网络请求。"""
    missing = []
    for module in ("googleapiclient.discovery", "google.oauth2.service_account", "google_auth_httplib2", "google.cloud.storage"):
        try:
            importlib.import_module(module)
        except ImportError:
            missing.append(module)
    return {"localAnalysisReady": True, "onlineDependenciesReady": not missing,
            "missingModules": missing, "remoteAuthorization": "not_checked"}


def validate_copy(path: str | None) -> int:
    """直接校验完整文案字段，不依赖子进程返回空内容。"""
    if not path:
        raise ValueError("validate-copy 需要 --copy-json")
    values = json.loads(Path(path).read_text(encoding="utf-8-sig"))
    if not isinstance(values, dict) or set(values) != set(LISTING_LIMITS):
        raise ValueError("文案必须包含且仅包含 title、shortDescription、fullDescription")
    if any(not isinstance(value, str) or not value.strip() for value in values.values()):
        raise ValueError("三个文案字段必须是非空字符串")
    checks = validate_listing_text(values)
    ok = all(value["withinLimit"] for value in checks.values())
    emit({"status": "ok" if ok else "over_limit", "fieldChecks": checks,
          "method": "Unicode code-point count; Play Console validation is authoritative"})
    return 0 if ok else 1


def selected_apps(args: argparse.Namespace, config: Path) -> list[dict]:
    """只使用项目确认的配置或显式包名，不发现账号中其他 App。"""
    apps, locales = load_app_config(config) if config.is_file() else ({}, {})
    packages = list(dict.fromkeys(args.app or apps.values()))
    if not packages:
        raise ValueError("尚无已确认发行包：先 inspect 核实源码，再传 --app 或填写项目配置")
    if any(not is_package_name(p) for p in packages):
        raise ValueError("发行包名格式无效")
    result = []
    for package in packages:
        locale = args.locale or locales.get(package) or "en-US"
        if not re.fullmatch(r"[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*", locale):
            raise ValueError("locale 格式无效")
        result.append({"packageName": package, "appName": next((n for n, p in apps.items() if p == package), package),
                       "locale": locale, "localeSource": "requested" if args.locale else "configured_default" if package in locales else "fallback"})
    return result


def validate_evidence(data: dict, request: dict) -> None:
    """阻止其他 App、语言、周期或历史运行的结果冒充本轮证据。"""
    if not isinstance(data, dict):
        raise ValueError("证据必须是 JSON 对象")
    for key in ("packageName", "locale", "analysisDays", "runId"):
        if data.get(key) != request[key]:
            raise ValueError("证据范围与本次请求不一致")
    if data.get("status") not in ("ok", "partial_failure") or not data.get("fetchedAt"):
        raise ValueError("证据状态或时间缺失")
    try:
        stamp = datetime.fromisoformat(data["fetchedAt"].replace("Z", "+00:00"))
        if stamp.tzinfo is None:
            raise ValueError("证据时间必须包含时区")
    except (TypeError, AttributeError):
        raise ValueError("证据时间格式无效") from None


def analyze(args: argparse.Namespace, project: Path, agent_dir: Path) -> int:
    """逐 App 隔离运行并检查证据，每个结果立即落盘。"""
    config = agent_dir / "config/google-play-aso.json"
    apps = selected_apps(args, config)
    bucket = args.reports_bucket or load_reports_bucket(config)
    scope = {"projectRoot": str(project), "apps": apps, "analysisDays": args.days,
             "reviewLimit": args.limit, "perAppTimeoutSeconds": args.timeout}
    if args.dry_run:
        emit({**scope, "executed": False})
        return 0
    base = project / ".google-play-aso"
    if base.is_symlink() or not base.resolve().is_relative_to(project.resolve()):
        raise ValueError("输出目录必须位于所属项目内")
    base.mkdir(exist_ok=True)
    (base / ".gitignore").write_text("*\n", encoding="utf-8")
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:10]
    run = base / "runs" / run_id
    run.mkdir(parents=True)
    save(run / "project.json", inspect_project(project))
    manifest = {**scope, "runId": run_id, "status": "running", "results": []}
    save(run / "manifest.json", manifest)
    for app in apps:
        folder = run / app["packageName"] / app["locale"]
        folder.mkdir(parents=True)
        request = {**app, "analysisDays": args.days, "limit": args.limit, "reportsBucket": bucket, "runId": run_id}
        save(folder / "request.json", request)
        outcome = {"packageName": app["packageName"], "locale": app["locale"], "status": "error"}
        try:
            result = subprocess.run([sys.executable, "-X", "utf8", "-B", str(Path(__file__).with_name("engine_worker.py")),
                                     str(folder / "request.json")], cwd=project, shell=False,
                                    capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=args.timeout)
            evidence = folder / "evidence.json"
            if result.returncode in (0, 1) and evidence.is_file():
                data = json.loads(evidence.read_text(encoding="utf-8"))
                validate_evidence(data, request)
                outcome.update(status=data["status"], evidenceFile=str(evidence), dataReport=str(folder / "数据分析.md"))
            else:
                outcome["reason"] = "engine_failed; 检查依赖、认证环境变量及授权；继续本地源码分析"
        except subprocess.TimeoutExpired:
            outcome.update(status="timeout", reason="单 App 超时，未自动重试；其他 App 继续")
        except (ValueError, OSError):
            outcome["reason"] = "invalid_evidence_or_process; 本轮证据未通过校验"
        manifest["results"].append(outcome)
        save(run / "manifest.json", manifest)
    manifest["status"] = "ok" if all(x["status"] == "ok" for x in manifest["results"]) else "partial_failure"
    save(run / "manifest.json", manifest)
    emit({"manifest": str(run / "manifest.json"), **manifest})
    return 0 if manifest["status"] == "ok" else 1


def main(argv: list[str] | None = None) -> int:
    """按命令完成项目取证、依赖检查、在线分析或本地文案校验。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("inspect", "check", "analyze", "validate-copy"))
    parser.add_argument("--project", "--workspace", dest="project", type=Path)
    parser.add_argument("--app", action="append")
    parser.add_argument("--locale")
    parser.add_argument("--days", type=positive, default=30)
    parser.add_argument("--limit", type=positive, default=500)
    parser.add_argument("--timeout", type=positive, default=300)
    parser.add_argument("--reports-bucket")
    parser.add_argument("--copy-json")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args(argv)
    try:
        if args.action == "validate-copy":
            return validate_copy(args.copy_json)
        project, agent_dir = locate_project(args.project)
        if args.action == "inspect":
            emit(inspect_project(project))
            return 0
        if args.action == "check":
            result = check_dependencies()
            emit({"projectRoot": str(project), **result})
            return 0 if result["onlineDependenciesReady"] else 1
        return analyze(args, project, agent_dir)
    except json.JSONDecodeError:
        emit({"error": "invalid_json", "message": "配置或文案不是有效 JSON"})
    except ValueError as exc:
        emit({"error": "invalid_input", "message": str(exc)})
    except OSError:
        emit({"error": "filesystem_error", "message": "检查项目目录及文件读写权限"})
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
