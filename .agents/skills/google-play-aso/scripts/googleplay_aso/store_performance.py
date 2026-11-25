"""Read Play Console store-performance search-term exports."""

from __future__ import annotations

import csv
import io
import re
from collections import defaultdict
from datetime import date, timedelta
from typing import Any

from .report_io import decode_report


BUCKET_PATTERN = re.compile(r"^(?:gs://)?([A-Za-z0-9][A-Za-z0-9._-]*)/?$")


def _normalized(row: dict[str, str]) -> dict[str, str]:
    return {" ".join(key.strip().lower().split()): (value or "").strip() for key, value in row.items() if key}


def _integer(value: str) -> int:
    return int(value.replace(",", "").strip() or "0")


def read_store_performance_rows(text: str, package: str) -> list[dict[str, Any]]:
    reader = csv.DictReader(io.StringIO(text.lstrip("\ufeff")))
    rows: list[dict[str, Any]] = []
    required = {
        "date", "package name", "traffic source", "search term",
        "store listing acquisitions", "store listing visitors",
    }
    normalized_fields = {" ".join((field or "").strip().lower().split()) for field in reader.fieldnames or []}
    if not required.issubset(normalized_fields):
        raise ValueError("Play store-performance report is missing required traffic-source columns.")
    for source in reader:
        row = _normalized(source)
        if row["package name"] != package:
            raise ValueError("Play store-performance report contains another package; refusing mixed app data.")
        day = date.fromisoformat(row["date"])
        traffic_source = row["traffic source"].lower()
        if traffic_source == "other":
            rows.append({
                "date": day.isoformat(),
                "searchTerm": None,
                "visitors": _integer(row["store listing visitors"]),
                "acquisitions": _integer(row["store listing acquisitions"]),
                "trafficSource": "other",
            })
            continue
        if traffic_source != "google play search" or not row["search term"]:
            continue
        rows.append({
            "date": day.isoformat(),
            "searchTerm": row["search term"],
            "visitors": _integer(row["store listing visitors"]),
            "acquisitions": _integer(row["store listing acquisitions"]),
            "trafficSource": "google play search",
        })
    return rows


def summarize_search_terms(rows: list[dict[str, Any]], *, days: int, today: date | None = None) -> dict[str, Any]:
    today = today or date.today()
    start = today - timedelta(days=days - 1)
    totals: dict[str, dict[str, int]] = defaultdict(lambda: {"visitors": 0, "acquisitions": 0})
    dates: set[str] = set()
    other_visitors = 0
    other_acquisitions = 0
    for row in rows:
        day = date.fromisoformat(row["date"])
        if start <= day <= today:
            dates.add(row["date"])
            if row["trafficSource"] == "other":
                other_visitors += row["visitors"]
                other_acquisitions += row["acquisitions"]
                continue
            totals[row["searchTerm"]]["visitors"] += row["visitors"]
            totals[row["searchTerm"]]["acquisitions"] += row["acquisitions"]
    terms = []
    for term, values in totals.items():
        visitors = values["visitors"]
        terms.append({
            "searchTerm": term,
            **values,
            "conversionRate": round(values["acquisitions"] / visitors, 4) if visitors else None,
            "privacyAggregated": term.lower() == "other",
            "trafficSource": "google play search",
        })
    terms.sort(key=lambda item: (-item["acquisitions"], -item["visitors"], item["searchTerm"].lower()))
    expected_dates = {(start + timedelta(days=offset)).isoformat() for offset in range(days)}
    return {
        "source": "Play Console GCS stats/store_performance traffic_source CSV",
        "window": {"start": start.isoformat(), "end": today.isoformat()},
        "daysWithData": len(dates),
        "missingDates": sorted(expected_dates - dates),
        "completeWindow": dates == expected_dates,
        "searchTerms": terms,
        "totalVisitors": sum(item["visitors"] for item in terms),
        "totalAcquisitions": sum(item["acquisitions"] for item in terms),
        "otherTrafficSourceVisitors": other_visitors,
        "otherTrafficSourceAcquisitions": other_acquisitions,
        "otherTrafficSourceNote": "Traffic source Other is not attributable to Google Play search or any search term.",
    }


def fetch_search_terms(bucket: str, package: str, *, days: int, credentials_path: str | None = None) -> dict[str, Any]:
    match = BUCKET_PATTERN.fullmatch(bucket)
    if not match:
        raise ValueError("Invalid Play report bucket name.")
    from google.cloud import storage
    from google.oauth2 import service_account
    from .auth import credential_file

    credentials = service_account.Credentials.from_service_account_file(
        str(credential_file(credentials_path)), scopes=["https://www.googleapis.com/auth/devstorage.read_only"]
    )
    client = storage.Client(credentials=credentials, project=credentials.project_id)
    target = client.bucket(match.group(1))
    today = date.today()
    start = today - timedelta(days=days - 1)
    month = date(start.year, start.month, 1)
    rows: list[dict[str, Any]] = []
    requested_files: list[str] = []
    found_files: list[str] = []
    while month <= today:
        name = f"stats/store_performance/store_performance_{package}_{month:%Y%m}_traffic_source.csv"
        requested_files.append(name)
        blob = target.blob(name)
        if blob.exists(client=client):
            found_files.append(name)
            rows.extend(read_store_performance_rows(decode_report(blob.download_as_bytes()), package))
        month = date(month.year + (month.month == 12), month.month % 12 + 1, 1)
    summary = summarize_search_terms(rows, days=days, today=today)
    summary.update({
        "requestedFiles": requested_files,
        "foundFiles": found_files,
        "missingFiles": [name for name in requested_files if name not in found_files],
    })
    return summary
