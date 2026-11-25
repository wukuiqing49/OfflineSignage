"""Evidence-only ASO report assembly."""

from __future__ import annotations

from collections import Counter
from datetime import datetime, timezone
from typing import Any

from .reviews import normalize_review

LISTING_LIMITS = {
    "title": 30,
    "shortDescription": 80,
    "fullDescription": 4000,
}


def validate_listing_text(values: dict[str, str]) -> dict[str, dict[str, Any]]:
    """Return Unicode code-point counts and target-limit checks for listing fields."""
    return {
        field: {
            "characters": len(text),
            "limit": LISTING_LIMITS[field],
            "withinLimit": len(text) <= LISTING_LIMITS[field],
        }
        for field, text in values.items()
        if field in LISTING_LIMITS
    }


def build_analysis(
    *,
    app_name: str,
    package_name: str,
    locale: str,
    days: int,
    fetched_reviews: list[dict[str, Any]],
    filtered_reviews: list[dict[str, Any]],
    listing_result: dict[str, Any] | None,
    locale_source: str = "requested",
    reviews_error: str | None = None,
    listing_error: str | None = None,
    install_stats: dict[str, Any] | None = None,
    install_error: str | None = None,
    keyword_stats: dict[str, Any] | None = None,
    keyword_error: str | None = None,
    rating_stats: dict[str, Any] | None = None,
    rating_error: str | None = None,
    crash_stats: dict[str, Any] | None = None,
    crash_error: str | None = None,
) -> dict[str, Any]:
    normalized = [normalize_review(review) for review in filtered_reviews]
    ratings = Counter(
        review["rating"] for review in normalized if isinstance(review.get("rating"), int)
    )
    rated_count = sum(ratings.values())
    review_rating = {
        "scope": "filtered_review_sample",
        "sampleCount": len(normalized),
        "ratedCount": rated_count,
        "average": round(sum(stars * count for stars, count in ratings.items()) / rated_count, 2)
        if rated_count else None,
        "distribution": {str(stars): ratings[stars] for stars in range(1, 6)},
        "storeAverageRating": rating_stats.get("latestTotalAverageRating") if rating_stats else None,
    }
    facts = [
        f"本次最多取回 {len(fetched_reviews)} 条评价；本地筛选后 {len(filtered_reviews)} 条。",
    ]
    missing: list[str] = []
    current_listing: dict[str, dict[str, Any]] | None = None
    if listing_result is not None:
        listing = listing_result.get("listing") or {}
        current_listing = {
            field: {
                "text": listing.get(field),
                "characters": len(listing[field]) if isinstance(listing.get(field), str) else None,
                "limit": LISTING_LIMITS[field],
            }
            for field in LISTING_LIMITS
        }
    elif listing_error:
        missing.append(f"商店文案未能读取：{listing_error}")
    if reviews_error:
        missing.append(f"评价未能读取：{reviews_error}")
    if not filtered_reviews:
        missing.append("没有可用于本次分析的评价样本；不能据此推断总体用户反馈。")
    if install_error:
        missing.append(f"Play 安装报表未能读取：{install_error}")
    elif install_stats is None:
        missing.append("安装报表未配置：需要 Play Console 报表 bucket 和 GCS 读取权限。")
    elif not install_stats["completeWindow"]:
        missing.append("安装报表未覆盖整个分析窗口；已有日期合计不能当作完整周期总量。")
    if keyword_error:
        missing.append(f"Play 搜索词报表未能读取：{keyword_error}")
    elif keyword_stats is None:
        missing.append("搜索词报表未配置：需要 Play Console 报表 bucket 和 GCS 读取权限。")
    elif not keyword_stats["completeWindow"]:
        missing.append("搜索词报表未覆盖整个分析窗口；只能分析已有日期。")
    if rating_error:
        missing.append(f"Play 评分报表未能读取：{rating_error}")
    elif rating_stats is None or rating_stats.get("latestTotalAverageRating") is None:
        missing.append("商店整体评分没有可用的 overview 记录；不能用评价样本均分代替。")
    if crash_error:
        missing.append(f"Play 崩溃/ANR 报表未能读取：{crash_error}")
    elif crash_stats is None or not crash_stats.get("daysWithRows"):
        missing.append("分析窗口没有崩溃/ANR overview 报表；不能将缺失数据解释为零事件。")
    missing.extend([
        "没有接入真正的日活用户数或自然排名数据。",
        "评价按本次最多取回的样本做本地筛选；样本不代表全部用户。",
    ])

    inferences = []
    if normalized:
        inferences.append("评价中的需求和问题只反映当前样本；需结合产品事实人工确认优先级。")
    else:
        inferences.append("当前没有足够评价样本形成可靠的用户需求判断。")

    suggestions = [
        "先依据现有 listing 与已核实的产品功能制作候选文案；本工具不会臆造功能或搜索数据。",
        "所有本地化文案在未经过母语审校前均需人工复核。",
    ]
    data_sources = [
        "Google Play Android Publisher API reviews.list",
        "Google Play Android Publisher API edits.insert + edits.listings.get",
    ]
    if install_stats is not None:
        data_sources.append(install_stats["source"])
    if keyword_stats is not None:
        data_sources.append(keyword_stats["source"])
    if rating_stats is not None:
        data_sources.append(rating_stats["source"])
    if crash_stats is not None:
        data_sources.append(crash_stats["source"])
    return {
        "appName": app_name,
        "packageName": package_name,
        "locale": locale,
        "localeSource": locale_source,
        "fetchedAt": datetime.now(timezone.utc).isoformat(),
        "dataSources": data_sources,
        "analysisDays": days,
        "fetchedReviewCount": len(fetched_reviews),
        "filteredReviewCount": len(filtered_reviews),
        "reviewRating": review_rating,
        "installStats": install_stats,
        "installStatsError": install_error,
        "keywordPerformance": keyword_stats,
        "keywordPerformanceError": keyword_error,
        "ratingStats": rating_stats,
        "ratingStatsError": rating_error,
        "crashStats": crash_stats,
        "crashStatsError": crash_error,
        "currentListing": current_listing,
        "reviews": normalized,
        "listing": listing_result,
        "facts": facts,
        "inferences": inferences,
        "missingData": missing,
        "suggestions": suggestions,
    }
