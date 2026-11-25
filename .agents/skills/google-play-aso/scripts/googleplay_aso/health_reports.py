"""Read package-scoped Play Console overview exports for ratings and stability."""

from __future__ import annotations

import csv
import io
from datetime import date, timedelta
from typing import Any

from .installs import BUCKET_PATTERN
from .report_io import decode_report


def read_overview_rows(text: str, package: str, kind: str) -> list[dict[str, Any]]:
    columns = {
        "ratings": ("daily average rating", "total average rating"),
        "crashes": ("daily crashes", "daily anrs"),
    }[kind]
    reader = csv.DictReader(io.StringIO(text.lstrip("\ufeff")))
    fields = {" ".join((field or "").lower().split()) for field in reader.fieldnames or []}
    if not {"date", "package name", *columns}.issubset(fields):
        raise ValueError(f"Play {kind} overview is missing required columns.")
    rows = []
    for source in reader:
        row = {" ".join(key.lower().split()): (value or "").strip() for key, value in source.items() if key}
        if row["package name"] != package:
            raise ValueError(f"Play {kind} overview contains another package.")
        day = date.fromisoformat(row["date"])
        values = [float(row[key]) if kind == "ratings" else int(row[key].replace(",", "")) for key in columns]
        rows.append({"date": day.isoformat(), columns[0]: values[0], columns[1]: values[1]})
    return rows


def summarize_overview(rows: list[dict[str, Any]], kind: str, *, days: int, today: date | None = None) -> dict[str, Any]:
    today = today or date.today()
    start = today - timedelta(days=days - 1)
    selected = [row for row in rows if start <= date.fromisoformat(row["date"]) <= today]
    dates = [row["date"] for row in selected]
    if len(dates) != len(set(dates)):
        raise ValueError(f"Duplicate date in Play {kind} overview.")
    selected.sort(key=lambda row: row["date"])
    result: dict[str, Any] = {
        "source": f"Play Console GCS stats/{kind} overview CSV",
        "window": {"start": start.isoformat(), "end": today.isoformat()},
        "daysWithRows": len(selected),
        "reportDates": dates,
        "noRowsDoesNotMeanZero": True,
    }
    if kind == "ratings":
        latest = selected[-1] if selected else None
        result.update({
            "latestTotalAverageRating": latest["total average rating"] if latest else None,
            "latestRatingDate": latest["date"] if latest else None,
            "ratingCount": None,
            "note": "Daily average rating 0.0 may mean no new rating; do not average daily values.",
        })
    else:
        result.update({
            "reportedCrashes": sum(row["daily crashes"] for row in selected) if selected else None,
            "reportedANRs": sum(row["daily anrs"] for row in selected) if selected else None,
            "note": "Overview rows may be sparse; totals cover reported rows only, not missing dates.",
        })
    return result


def fetch_overview(bucket: str, package: str, kind: str, *, days: int, credentials_path: str | None = None) -> dict[str, Any]:
    if kind not in ("ratings", "crashes"):
        raise ValueError("Unsupported overview report kind.")
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
    rows = []
    requested = []
    found = []
    while month <= today:
        name = f"stats/{kind}/{kind}_{package}_{month:%Y%m}_overview.csv"
        requested.append(name)
        blob = target.blob(name)
        if blob.exists(client=client):
            found.append(name)
            rows.extend(read_overview_rows(decode_report(blob.download_as_bytes()), package, kind))
        month = date(month.year + (month.month == 12), month.month % 12 + 1, 1)
    summary = summarize_overview(rows, kind, days=days, today=today)
    summary.update({"requestedFiles": requested, "foundFiles": found, "missingFiles": [name for name in requested if name not in found]})
    return summary
