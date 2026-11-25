"""Small, deterministic JSON and Markdown report helpers."""

from __future__ import annotations

import json
import re
from datetime import date
from pathlib import Path
from typing import Any


def to_markdown(data: dict[str, Any]) -> str:
    if "fieldChecks" in data:
        lines = [
            "# Listing text validation",
            "",
            f"- 状态：{data.get('status', 'unknown')}",
            f"- 计数口径：{data.get('method', '')}",
        ]
        labels = {"title": "标题", "shortDescription": "简短说明", "fullDescription": "详细说明"}
        for field, check in data["fieldChecks"].items():
            result = "合规" if check["withinLimit"] else "超出上限"
            lines.append(f"- {labels[field]}：{check['characters']}/{check['limit']} 字符，{result}")
        return "\n".join(lines) + "\n"

    if "apps" in data and "reviewsAccess" in data:
        lines = [
            "# Google Play API Doctor",
            "",
            f"- 状态：{data.get('status', 'unknown')}",
            f"- 凭据解析：{'成功' if data.get('credentialParsed') else '失败'}",
        ]
        access = data.get("reviewsAccess") or {}
        if access:
            lines.append(f"- reviews.list：{access.get('succeeded', 0)}/{access.get('total', 0)} 个 App 可用")
        if data.get("error"):
            lines.append(f"- 错误：{data['error']}")
        for app in data.get("apps") or []:
            error = f"；{app['error']}" if app.get("error") else ""
            lines.append(f"- {app['appName']} (`{app['packageName']}`)：{app['reviewsAccess']}{error}")
        return "\n".join(lines) + "\n"

    if "apps" in data and "analysisDays" in data:
        lines = ["# Google Play ASO 多 App 分析", "", f"- 状态：{data['status']}"]
        for report in data["apps"]:
            lines.extend(["", "---", "", to_markdown(report)])
        return "\n".join(lines) + "\n"

    if "apps" in data:
        lines = ["# Accessible apps", ""]
        lines.extend(f"- {app['name']} (`{app['packageName']}`)" for app in data["apps"])
        return "\n".join(lines) + "\n"

    if data.get("status") in {"ok", "not_found", "error"} and "locale" in data and "facts" not in data:
        lines = [
            f"# Store listing: {data.get('appName', 'Unknown app')}",
            "",
            f"- 包名：`{data.get('packageName', 'unknown')}`",
            f"- Locale：`{data['locale']}`",
            f"- 状态：{data['status']}",
        ]
        if data.get("dataSource"):
            lines.append(f"- 数据来源：{data['dataSource']}")
        if data.get("editExpiryTimeSeconds"):
            lines.append(f"- 临时 edit 过期时间（Unix 秒）：`{data['editExpiryTimeSeconds']}`")
        listing = data.get("listing") or {}
        for field, label in (("title", "标题"), ("shortDescription", "简短说明"), ("fullDescription", "详细说明")):
            if listing.get(field):
                lines.extend(["", f"## {label}（{len(listing[field])} 字符）", "", listing[field]])
        if data.get("error"):
            lines.extend(["", f"错误：{data['error']}"])
        return "\n".join(lines) + "\n"

    if "filters" in data and "reviews" in data:
        lines = [
            f"# Reviews: {data.get('appName', 'Unknown app')}",
            "",
            f"- 包名：`{data.get('packageName', 'unknown')}`",
            f"- 抓取时间：{data.get('fetchedAt', 'unknown')}",
            f"- 取回 {data.get('fetchedReviewCount', 0)} 条；筛选后 {data.get('returnedReviewCount', 0)} 条",
            f"- 本地筛选：`{json.dumps(data['filters'], ensure_ascii=False)}`",
        ]
        if data.get("dataSource"):
            lines.append(f"- 数据来源：{data['dataSource']}")
        if not data["reviews"]:
            lines.extend(["", "没有符合条件的评价。"])
        for review in data["reviews"]:
            text = (review.get("text") or "（无文字评论）").replace("\n", " ")
            lines.extend(["", f"- {review.get('rating', '?')} 星 | {review.get('language') or '语言未知'} | {text}"])
            if review.get("developerReply"):
                lines.append(f"  - 开发者回复：{review['developerReply']}")
        return "\n".join(lines) + "\n"

    lines = [
        f"# Google Play ASO 数据报告：{data.get('appName', 'Unknown app')}",
        "",
        "## 分析范围与数据源",
        "",
        f"- 包名：`{data.get('packageName', 'unknown')}`",
        f"- 抓取时间：{data.get('fetchedAt', 'unknown')}",
        f"- 状态：{data.get('status', 'unknown')}",
    ]
    if data.get("locale"):
        locale_source = data.get("localeSource", "requested")
        lines.append(f"- Locale：`{data['locale']}`（来源：{locale_source}）")
    if data.get("analysisDays") is not None:
        lines.append(f"- 评价范围：最近 {data['analysisDays']} 天（对本次取回样本本地筛选）")
    if data.get("dataSources"):
        lines.append(f"- 数据来源：{'；'.join(data['dataSources'])}")

    lines.extend(["", "## 当前商店资料"])
    current = data.get("currentListing")
    if current is None:
        lines.append("- 未能读取当前商店资料；见缺失数据。")
    else:
        for field, label in (("title", "标题"), ("shortDescription", "简短说明"), ("fullDescription", "详细说明")):
            item = current[field]
            count = f"{item['characters']}/{item['limit']} 字符" if item["characters"] is not None else "未提供"
            lines.extend(["", f"### {label}（{count}）", "", f"````text\n{item['text']}\n````" if item["text"] is not None else "未提供"])

    rating = data.get("reviewRating") or {}
    lines.extend(["", "## 评分与评价"])
    rating_stats = data.get("ratingStats")
    if rating_stats and rating_stats.get("latestTotalAverageRating") is not None:
        lines.append(f"- 评分 overview 截至 {rating_stats['latestRatingDate']} 的总平均评分：{rating_stats['latestTotalAverageRating']}/5；评分人数未提供。")
    else:
        lines.append("- 商店整体评分：未取得；不能用样本均分代替。")
    lines.append(f"- 本次最多取回 {data.get('fetchedReviewCount', 0)} 条，按最近 {data.get('analysisDays', '?')} 天本地筛选后 {data.get('filteredReviewCount', 0)} 条。")
    if rating.get("average") is not None:
        lines.append(f"- 样本均分：{rating['average']}/5（有星级的 {rating['ratedCount']} 条，仅限本次样本）。")
        lines.append("- 样本星级分布：" + "、".join(f"{stars} 星 {rating['distribution'][str(stars)]} 条" for stars in range(1, 6)))
    else:
        lines.append("- 样本均分：无可用星级。")

    lines.extend(["", "## 安装与卸载"])
    stats = data.get("installStats")
    if stats is not None:
        lines.append(f"- 来源：{stats['source']}；覆盖 {stats['daysWithData']}/{data['analysisDays']} 天。")
        lines.append(f"- 已覆盖日期每日用户安装合计：{stats['totalUserInstalls']}；卸载合计：{stats['totalUserUninstalls']}。")
        if not stats["completeWindow"]:
            lines.append("- 报表日期不完整，以上不是完整周期总量。")
            if stats.get("missingFiles"):
                lines.append(f"- 未找到月报文件：{'、'.join(stats['missingFiles'])}")
            if stats.get("missingDates"):
                lines.append(f"- 缺失日期：{'、'.join(stats['missingDates'])}")
        lines.extend(f"- {item['date']}：用户安装 {item['userInstalls']}，卸载 {item['userUninstalls']}" for item in stats["daily"])
    else:
        lines.append("- 安装量：未取得；见缺失数据。")
    lines.append("- 日活用户（DAU）：未接入；活跃设备安装数不是 DAU。")

    lines.extend(["", "## 崩溃与 ANR"])
    crashes = data.get("crashStats")
    if crashes and crashes.get("daysWithRows"):
        lines.append(f"- 已取得 overview 文件 {len(crashes['foundFiles'])} 个，窗口内有记录 {crashes['daysWithRows']} 天；记录中的崩溃 {crashes['reportedCrashes']} 次、ANR {crashes['reportedANRs']} 次。")
        lines.append("- overview 可能只包含发生事件的日期；未出现的日期不能解释为零事件。")
    else:
        lines.append("- 当前窗口未取得崩溃/ANR overview 记录；无法判断事件数是否为零。")
    if crashes and crashes.get("missingFiles"):
        lines.append(f"- 缺失月报：{'、'.join(crashes['missingFiles'])}")

    lines.extend(["", "## 商店访问与获客"])
    keywords = data.get("keywordPerformance")
    if keywords is None:
        lines.append("- 搜索词表现：未取得；见缺失数据。")
    else:
        lines.append(f"- 来源：{keywords['source']}；覆盖 {keywords['daysWithData']}/{data['analysisDays']} 天。")
        lines.append(f"- Google Play 搜索词访问合计：{keywords['totalVisitors']}；获客合计：{keywords['totalAcquisitions']}。")
        if keywords.get("otherTrafficSourceVisitors") or keywords.get("otherTrafficSourceAcquisitions"):
            lines.append(f"- 流量来源 Other：访问 {keywords.get('otherTrafficSourceVisitors', 0)}，获客 {keywords.get('otherTrafficSourceAcquisitions', 0)}；不能归因到 Google Play 搜索或具体搜索词。")
        if not keywords["completeWindow"]:
            lines.append("- 报表覆盖不完整，以下不是完整周期结果。")
            if keywords.get("missingFiles"):
                lines.append(f"- 未找到月报文件：{'、'.join(keywords['missingFiles'])}")
        for item in keywords["searchTerms"]:
            rate = f"{item['conversionRate']:.2%}" if item["conversionRate"] is not None else "不可计算"
            privacy = "（Google Play 搜索词聚合项）" if item["privacyAggregated"] else ""
            lines.append(f"- `{item['searchTerm']}`{privacy}：访问 {item['visitors']}，获客 {item['acquisitions']}，转化率 {rate}")
        if not keywords["searchTerms"]:
            lines.append("- 已找到的报表中没有达到披露阈值的具体 Google Play 搜索词。")

    lines.extend(["", "## 数据结论"])
    lines.extend(f"- {item}" for item in [*(data.get("facts") or []), *(data.get("inferences") or [])])
    lines.extend(["", "## 数据限制"])
    lines.extend(f"- {item}" for item in (data.get("missingData") or ["暂无"]))
    return "\n".join(lines) + "\n"


def render(data: dict[str, Any], output_format: str) -> str:
    if output_format == "json":
        return json.dumps(data, ensure_ascii=False, indent=2, default=str)
    return to_markdown(data)


def write_output(output_dir: Path, requested_name: str, content: str) -> Path:
    requested = Path(requested_name)
    if requested.is_absolute() or len(requested.parts) != 1 or requested.name in {".", ".."}:
        raise ValueError("--output must be a filename inside the local output/ directory.")
    output_dir.mkdir(parents=True, exist_ok=True)
    target = output_dir / requested.name
    suffix = target.suffix
    stem = target.stem
    counter = 2
    while target.exists():
        target = output_dir / f"{stem}-{counter}{suffix}"
        counter += 1
    target.write_text(content, encoding="utf-8")
    return target


def write_data_reports(output_dir: Path, reports: list[dict[str, Any]], *, day: date | None = None) -> list[Path]:
    stamp = (day or date.today()).isoformat()
    targets = []
    for report in reports:
        package = report["packageName"]
        if not re.fullmatch(r"[A-Za-z0-9_.]+", package):
            raise ValueError("Invalid package name for report filename.")
        targets.append(write_output(output_dir, f"{package}-数据分析-{stamp}.md", to_markdown(report)))
    return targets
