"""Read Play Console country-dimension install exports without mixing dimensions."""

from __future__ import annotations

import csv
import io
import re
from collections import defaultdict
from datetime import date, timedelta
from typing import Any

from .report_io import decode_report


BUCKET_PATTERN = re.compile(r"^(?:gs://)?([A-Za-z0-9][A-Za-z0-9._-]*)/?$")


def read_install_rows(text: str, package: str) -> list[dict[str, Any]]:
    reader = csv.DictReader(io.StringIO(text.lstrip("\ufeff")))
    required = {"date", "package name", "country", "daily user installs", "daily user uninstalls"}
    fields = {" ".join((field or "").strip().lower().split()) for field in reader.fieldnames or []}
    if not required.issubset(fields):
        raise ValueError("Play install report is missing required country-dimension columns.")
    rows = []
    for source in reader:
        row = {" ".join(key.strip().lower().split()): (value or "").strip() for key, value in source.items() if key}
        if row["package name"] != package:
            raise ValueError("Play install report contains another package; refusing mixed app data.")
        day = date.fromisoformat(row["date"])
        rows.append({
            "date": day.isoformat(),
            "country": row["country"],
            "userInstalls": int(row["daily user installs"].replace(",", "") or "0"),
            "userUninstalls": int(row["daily user uninstalls"].replace(",", "") or "0"),
        })
    return rows


def summarize_installs(rows: list[dict[str, Any]], *, days: int, today: date | None = None) -> dict[str, Any]:
    today = today or date.today()
    start = today - timedelta(days=days - 1)
    daily: dict[str, dict[str, int]] = defaultdict(lambda: {"userInstalls": 0, "userUninstalls": 0})
    seen: set[tuple[str, str]] = set()
    for row in rows:
        key = (row["date"], row["country"])
        if key in seen:
            raise ValueError("Duplicate date/country in Play install reports; refusing to double count.")
        seen.add(key)
        if start <= date.fromisoformat(row["date"]) <= today:
            daily[row["date"]]["userInstalls"] += row["userInstalls"]
            daily[row["date"]]["userUninstalls"] += row["userUninstalls"]
    ordered = [{"date": day, **values} for day, values in sorted(daily.items())]
    expected_dates = {(start + timedelta(days=offset)).isoformat() for offset in range(days)}
    actual_dates = set(daily)
    return {
        "source": "Play Console GCS stats/installs country CSV",
        "window": {"start": start.isoformat(), "end": today.isoformat()},
        "daysWithData": len(ordered),
        "daily": ordered,
        "totalUserInstalls": sum(item["userInstalls"] for item in ordered),
        "totalUserUninstalls": sum(item["userUninstalls"] for item in ordered),
        "missingDates": sorted(expected_dates - actual_dates),
        "completeWindow": actual_dates == expected_dates,
    }


def fetch_installs(bucket: str, package: str, *, days: int, credentials_path: str | None = None) -> dict[str, Any]:
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
        prefix = f"stats/installs/installs_{package}_{month:%Y%m}_country.csv"
        requested_files.append(prefix)
        blob = target.blob(prefix)
        if blob.exists(client=client):
            found_files.append(prefix)
            rows.extend(read_install_rows(decode_report(blob.download_as_bytes()), package))
        month = date(month.year + (month.month == 12), month.month % 12 + 1, 1)
    summary = summarize_installs(rows, days=days, today=today)
    summary.update({
        "requestedFiles": requested_files,
        "foundFiles": found_files,
        "missingFiles": [name for name in requested_files if name not in found_files],
    })
    return summary
