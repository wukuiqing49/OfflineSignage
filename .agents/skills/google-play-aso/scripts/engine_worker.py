"""单 App 在线取证进程；仅使用本 Skill 的引擎模块。"""
from __future__ import annotations
import json
from pathlib import Path
import sys
from googleplay_aso import cli
from googleplay_aso.reports import to_markdown


def safe_error(exc: Exception) -> str:
    """仅保留 HTTP 状态或异常类别，不写入响应正文、令牌或凭据路径。"""
    status = getattr(getattr(exc, "resp", None), "status", None)
    return f"HTTP {status}" if isinstance(status, int) else type(exc).__name__


class UnavailableClient:
    """Publisher 不可用时让来源级容错继续处理其他数据。"""

    def __init__(self, reason: str):
        """保存不含敏感内容的失败类别。"""
        self.reason = reason

    def list_reviews(self, *args, **kwargs):
        """将评价来源标记为不可用。"""
        raise RuntimeError(self.reason)

    def get_listing(self, *args, **kwargs):
        """将商店文案来源标记为不可用。"""
        raise RuntimeError(self.reason)


def main(request_path: Path) -> int:
    """独立获取一个 App 并原子保存证据；启动失败由父进程记录。"""
    request = json.loads(request_path.read_text(encoding="utf-8"))
    cli.api_error = safe_error
    try:
        client = cli.make_client(None)
    except Exception as exc:
        client = UnavailableClient(safe_error(exc))
    data = cli.analyze_one(client, request["appName"], request["packageName"],
                           locale=request["locale"], locale_source=request["localeSource"],
                           days=request["analysisDays"], limit=request["limit"],
                           reports_bucket=request.get("reportsBucket"))
    data["runId"] = request["runId"]
    temporary = request_path.with_name("evidence.tmp")
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(request_path.with_name("evidence.json"))
    request_path.with_name("数据分析.md").write_text(to_markdown(data), encoding="utf-8")
    return 0 if data["status"] == "ok" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main(Path(sys.argv[1])))
    except Exception:
        print('{"error":"engine_failed"}')
        raise SystemExit(2)
