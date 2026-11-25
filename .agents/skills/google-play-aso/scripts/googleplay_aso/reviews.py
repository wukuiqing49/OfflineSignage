"""Review pagination, normalization, and local filtering."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Any

from .client import PlayClient


def fetch_reviews(
    client: PlayClient,
    package_name: str,
    *,
    limit: int,
    locale: str | None = None,
) -> list[dict[str, Any]]:
    reviews: list[dict[str, Any]] = []
    token: str | None = None
    while len(reviews) < limit:
        page_size = min(100, limit - len(reviews))
        response = client.list_reviews(
            package_name,
            page_size=page_size,
            token=token,
            translation_language=locale,
        )
        batch = response.get("reviews") or []
        reviews.extend(batch[: limit - len(reviews)])
        token_data = response.get("tokenPagination") or {}
        next_token = token_data.get("nextPageToken")
        if not batch or not next_token or next_token == token:
            break
        token = next_token
    return reviews


def comment_parts(review: dict[str, Any]) -> tuple[dict[str, Any], dict[str, Any] | None]:
    user_comment: dict[str, Any] = {}
    developer_comment: dict[str, Any] | None = None
    for comment in review.get("comments") or []:
        if "userComment" in comment:
            user_comment = comment["userComment"] or {}
        if "developerComment" in comment:
            developer_comment = comment["developerComment"] or {}
    return user_comment, developer_comment


def timestamp(value: Any) -> datetime | None:
    if isinstance(value, str):
        try:
            parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
            return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)
        except ValueError:
            return None
    if isinstance(value, dict) and value.get("seconds") is not None:
        try:
            seconds = int(value["seconds"])
            nanos = int(value.get("nanos", 0))
            return datetime.fromtimestamp(seconds + nanos / 1_000_000_000, tz=timezone.utc)
        except (TypeError, ValueError, OSError, OverflowError):
            return None
    return None


def review_time(review: dict[str, Any]) -> datetime | None:
    user_comment, _ = comment_parts(review)
    return timestamp(user_comment.get("lastModified") or review.get("lastModified"))


def filter_reviews(
    reviews: list[dict[str, Any]],
    *,
    days: int | None = None,
    stars: int | None = None,
    now: datetime | None = None,
) -> list[dict[str, Any]]:
    cutoff = None
    if days is not None:
        cutoff = (now or datetime.now(timezone.utc)) - timedelta(days=days)
    filtered = []
    for review in reviews:
        user_comment, _ = comment_parts(review)
        rating = user_comment.get("starRating")
        if stars is not None and rating != stars:
            continue
        if cutoff is not None:
            published = review_time(review)
            if published is None or published < cutoff:
                continue
        filtered.append(review)
    return filtered


def normalize_review(review: dict[str, Any]) -> dict[str, Any]:
    user_comment, developer_comment = comment_parts(review)
    return {
        "reviewId": review.get("reviewId"),
        "authorName": review.get("authorName"),
        "rating": user_comment.get("starRating"),
        "language": user_comment.get("reviewerLanguage"),
        "text": user_comment.get("text"),
        "lastModified": user_comment.get("lastModified") or review.get("lastModified"),
        "developerReply": developer_comment.get("text") if developer_comment else None,
    }
