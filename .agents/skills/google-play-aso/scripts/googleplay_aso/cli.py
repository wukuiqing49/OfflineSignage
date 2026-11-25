"""Command-line interface for read-focused Google Play analysis."""

from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

from .apps import AppConfigError, is_package_name, load_app_config, load_apps, load_reports_bucket, merge_apps, resolve_app
from .aso import build_analysis, validate_listing_text
from .auth import AuthenticationError, build_android_publisher
from .client import PlayClient
from .discovery import DiscoveryError, authorized_http, discover_apps
from .installs import fetch_installs
from .health_reports import fetch_overview
from .reports import render, write_data_reports, write_output
from .reviews import fetch_reviews, filter_reviews, normalize_review
from .store_performance import fetch_search_terms


ROOT = Path.cwd()
CONFIG_PATH = ROOT / "config" / "apps.json"
OUTPUT_DIR = ROOT / "output"


def api_error(exc: Exception) -> str:
    status = getattr(getattr(exc, "resp", None), "status", None)
    if status == 401:
        return "HTTP 401: credentials are invalid or expired; verify the local service-account key."
    if status == 403:
        return "HTTP 403: service account lacks Play Console access for this app or API."
    if status == 404:
        return "HTTP 404: app/package or requested locale was not found, or is not accessible."
    if status == 429:
        return "HTTP 429: request quota exceeded; retry later."
    if isinstance(status, int) and status >= 500:
        return f"HTTP {status}: Google Play service error; retry later."
    return f"{type(exc).__name__}: {exc}"


def add_output_options(parser: argparse.ArgumentParser, *, app_required: bool = True) -> None:
    parser.add_argument("--app", required=app_required, help="Configured app name or package name")
    parser.add_argument("--format", choices=("json", "md"), default="md")
    parser.add_argument("--output", help="Write a report filename inside output/")
    parser.add_argument("--credentials", help=argparse.SUPPRESS)


def make_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="googleplay-aso")
    parser.add_argument("--config", type=Path, default=CONFIG_PATH, help=argparse.SUPPRESS)
    subparsers = parser.add_subparsers(dest="command", required=True)

    doctor = subparsers.add_parser("doctor", help="Check local setup and per-app review access")
    doctor.add_argument("--app", help="Check one configured app; default checks all")
    doctor.add_argument("--format", choices=("json", "md"), default="md")
    doctor.add_argument("--output", help="Write a report filename inside output/")
    doctor.add_argument("--credentials", help=argparse.SUPPRESS)

    apps_parser = subparsers.add_parser("apps", help="Discover accessible apps")
    apps_parser.add_argument("--app", help="Filter by configured name or package")
    apps_parser.add_argument("--format", choices=("json", "md"), default="md")
    apps_parser.add_argument("--output", help="Write a report filename inside output/")
    apps_parser.add_argument("--credentials", help=argparse.SUPPRESS)

    reviews = subparsers.add_parser("reviews", help="Fetch and locally filter reviews")
    add_output_options(reviews)
    reviews.add_argument("--limit", type=int, default=100, help="Maximum reviews fetched (default: 100)")
    reviews.add_argument("--days", type=int, help="Keep reviews modified in the last N days")
    reviews.add_argument("--stars", type=int, choices=range(1, 6), help="Locally filter by star rating")
    reviews.add_argument("--locale", help="Translation language code, for example en-US")

    listing = subparsers.add_parser("listing", help="Read a localized store listing")
    add_output_options(listing)
    listing.add_argument("--locale", required=True)

    analyze = subparsers.add_parser("analyze", help="Build an evidence-only ASO report")
    add_output_options(analyze)
    analyze.add_argument("--locale", help="Listing locale; defaults to configured defaultLocale, then en-US fallback")
    analyze.add_argument("--days", type=int, default=30)
    analyze.add_argument("--limit", type=int, default=500, help="Maximum reviews fetched (default: 500)")
    analyze.add_argument("--reports-bucket", default=os.environ.get("PLAY_REPORTS_BUCKET"), help="Play Console report bucket ID")

    batch = subparsers.add_parser("analyze-all", help="Analyze multiple apps after one catalog lookup")
    batch.add_argument("--app", action="append", help="Select an app name or package; repeat to select several")
    batch.add_argument("--locale", help="Listing locale; defaults per app to configured defaultLocale, then en-US fallback")
    batch.add_argument("--days", type=int, default=30)
    batch.add_argument("--limit", type=int, default=500)
    batch.add_argument("--reports-bucket", default=os.environ.get("PLAY_REPORTS_BUCKET"), help="Play Console report bucket ID")
    batch.add_argument("--format", choices=("json", "md"), default="json")
    batch.add_argument("--output", help="Write a report filename inside output/")
    batch.add_argument("--data-reports", action="store_true", help="Write one factual Markdown report per selected app")
    batch.add_argument("--credentials", help=argparse.SUPPRESS)

    validate = subparsers.add_parser("validate-copy", help="Check Play listing text lengths")
    validate.add_argument("--title")
    validate.add_argument("--short-description")
    validate.add_argument("--full-description")
    validate.add_argument("--format", choices=("json", "md"), default="json")
    validate.add_argument("--output", help="Write a report filename inside output/")
    return parser


def print_or_save(data: dict[str, Any], output_format: str, output_name: str | None) -> None:
    content = render(data, output_format)
    if output_name:
        target = write_output(OUTPUT_DIR, output_name, content)
        print(f"Report written to {target}")
    else:
        print(content)


def make_client(credentials_path: str | None) -> PlayClient:
    return PlayClient(build_android_publisher(credentials_path))


def analysis_locale(config_path: Path, package: str, requested: str | None) -> tuple[str, str]:
    if requested:
        return requested, "requested"
    if config_path.is_file():
        _, locales = load_app_config(config_path)
        if package in locales:
            return locales[package], "configured_default"
    return "en-US", "fallback"


def app_catalog(
    config_path: Path, credentials_path: str | None, selectors: list[str] | None = None
) -> dict[str, str]:
    configured = load_apps(config_path) if config_path.is_file() else {}
    if selectors:
        explicit = {selector: selector for selector in selectors if is_package_name(selector)}
        configured = merge_apps(configured, explicit)
        if all(selector in configured or selector in configured.values() for selector in selectors):
            return configured
    try:
        discovered = discover_apps(authorized_http(credentials_path))
    except (AuthenticationError, DiscoveryError) as exc:
        if not configured:
            raise
        print(f"warning: app discovery unavailable ({exc}); using verified local app config.", file=sys.stderr)
        discovered = {}
    return merge_apps(configured, discovered)


def command_apps(args: argparse.Namespace, apps: dict[str, str]) -> int:
    selected = apps
    if args.app:
        name, package = resolve_app(apps, args.app)
        selected = {name: package}
    data = {
        "apps": [{"name": name, "packageName": package} for name, package in selected.items()]
    }
    print_or_save(data, args.format, args.output)
    return 0


def command_doctor(args: argparse.Namespace, apps: dict[str, str]) -> int:
    selected = apps
    if args.app:
        name, package = resolve_app(apps, args.app)
        selected = {name: package}
    results: list[dict[str, Any]] = []
    try:
        service = build_android_publisher(args.credentials)
        client = PlayClient(service)
    except AuthenticationError as exc:
        print_or_save({"status": "error", "error": str(exc), "apps": []}, args.format, args.output)
        return 2

    for name, package in selected.items():
        try:
            client.list_reviews(package, page_size=1)
            results.append({"appName": name, "packageName": package, "reviewsAccess": "ok"})
        except Exception as exc:
            results.append({
                "appName": name,
                "packageName": package,
                "reviewsAccess": "error",
                "error": api_error(exc),
            })
    success = sum(result["reviewsAccess"] == "ok" for result in results)
    data = {
        "status": "ok" if success == len(results) else "partial_failure",
        "credentialParsed": True,
        "reviewsAccess": {"succeeded": success, "total": len(results)},
        "listingPermissions": "not_checked; run listing explicitly",
        "apps": results,
    }
    print_or_save(data, args.format, args.output)
    return 0 if success == len(results) else 1


def command_reviews(args: argparse.Namespace, apps: dict[str, str]) -> int:
    if args.limit < 1 or (args.days is not None and args.days < 1):
        raise ValueError("--limit and --days must be positive integers.")
    name, package = resolve_app(apps, args.app)
    client = make_client(args.credentials)
    try:
        fetched = fetch_reviews(client, package, limit=args.limit, locale=args.locale)
    except Exception as exc:
        print(f"error: {api_error(exc)}", file=sys.stderr)
        return 1
    selected = filter_reviews(fetched, days=args.days, stars=args.stars)
    data = {
        "appName": name,
        "packageName": package,
        "dataSource": "Google Play Android Publisher API reviews.list",
        "locale": args.locale,
        "fetchedAt": datetime.now(timezone.utc).isoformat(),
        "fetchedReviewCount": len(fetched),
        "returnedReviewCount": len(selected),
        "filters": {"days": args.days, "stars": args.stars},
        "reviews": [normalize_review(review) for review in selected],
    }
    print_or_save(data, args.format, args.output)
    return 0


def command_listing(args: argparse.Namespace, apps: dict[str, str]) -> int:
    name, package = resolve_app(apps, args.app)
    client = make_client(args.credentials)
    try:
        result = client.get_listing(package, args.locale)
        data = {
            "appName": name,
            "packageName": package,
            "dataSource": "Google Play Android Publisher API edits.insert + edits.listings.get",
            "locale": args.locale,
            "fetchedAt": datetime.now(timezone.utc).isoformat(),
            "status": "ok",
            **result,
        }
    except Exception as exc:
        status = getattr(getattr(exc, "resp", None), "status", None)
        error = (
            "Requested locale may not exist; also verify this package and service-account access."
            if status == 404
            else api_error(exc)
        )
        data = {
            "appName": name,
            "packageName": package,
            "dataSources": [
                "Google Play Android Publisher API reviews.list",
                "Google Play Android Publisher API edits.insert + edits.listings.get",
            ],
            "locale": args.locale,
            "fetchedAt": datetime.now(timezone.utc).isoformat(),
            "status": "not_found" if status == 404 else "error",
            "error": error,
        }
        print_or_save(data, args.format, args.output)
        return 1
    print_or_save(data, args.format, args.output)
    return 0


def analyze_one(client: PlayClient, name: str, package: str, *, locale: str, days: int, limit: int, locale_source: str = "requested", reports_bucket: str | None = None, credentials_path: str | None = None) -> dict[str, Any]:
    reviews: list[dict[str, Any]] = []
    filtered: list[dict[str, Any]] = []
    reviews_error = None
    listing_result = None
    listing_error = None
    install_stats = None
    install_error = None
    keyword_stats = None
    keyword_error = None
    rating_stats = None
    rating_error = None
    crash_stats = None
    crash_error = None
    try:
        reviews = fetch_reviews(client, package, limit=limit, locale=locale)
        filtered = filter_reviews(reviews, days=days)
    except Exception as exc:
        reviews_error = api_error(exc)
    try:
        listing_result = client.get_listing(package, locale)
    except Exception as exc:
        listing_error = api_error(exc)
    if reports_bucket:
        try:
            install_stats = fetch_installs(reports_bucket, package, days=days, credentials_path=credentials_path)
        except Exception as exc:
            install_error = api_error(exc)
        try:
            keyword_stats = fetch_search_terms(reports_bucket, package, days=days, credentials_path=credentials_path)
        except Exception as exc:
            keyword_error = api_error(exc)
        try:
            rating_stats = fetch_overview(reports_bucket, package, "ratings", days=days, credentials_path=credentials_path)
        except Exception as exc:
            rating_error = api_error(exc)
        try:
            crash_stats = fetch_overview(reports_bucket, package, "crashes", days=days, credentials_path=credentials_path)
        except Exception as exc:
            crash_error = api_error(exc)
    data = build_analysis(
        app_name=name,
        package_name=package,
        locale=locale,
        locale_source=locale_source,
        days=days,
        fetched_reviews=reviews,
        filtered_reviews=filtered,
        listing_result=listing_result,
        reviews_error=reviews_error,
        listing_error=listing_error,
        install_stats=install_stats,
        install_error=install_error,
        keyword_stats=keyword_stats,
        keyword_error=keyword_error,
        rating_stats=rating_stats,
        rating_error=rating_error,
        crash_stats=crash_stats,
        crash_error=crash_error,
    )
    data["status"] = "ok" if not any((reviews_error, listing_error, install_error, keyword_error, rating_error, crash_error)) else "partial_failure"
    return data


def command_analyze(args: argparse.Namespace, apps: dict[str, str]) -> int:
    if args.days < 1 or args.limit < 1:
        raise ValueError("--days and --limit must be positive integers.")
    name, package = resolve_app(apps, args.app)
    locale, locale_source = analysis_locale(args.config, package, args.locale)
    reports_bucket = args.reports_bucket or load_reports_bucket(args.config)
    data = analyze_one(make_client(args.credentials), name, package, locale=locale, locale_source=locale_source, days=args.days, limit=args.limit, reports_bucket=reports_bucket, credentials_path=args.credentials)
    print_or_save(data, args.format, args.output)
    return 0 if data["status"] == "ok" else 1


def command_analyze_all(args: argparse.Namespace, apps: dict[str, str]) -> int:
    if args.days < 1 or args.limit < 1:
        raise ValueError("--days and --limit must be positive integers.")
    selected = dict.fromkeys(resolve_app(apps, selector) for selector in args.app) if args.app else {
        (name, package): None for name, package in apps.items()
    }
    client = make_client(args.credentials)
    reports_bucket = args.reports_bucket or load_reports_bucket(args.config)
    reports = []
    for name, package in selected:
        locale, locale_source = analysis_locale(args.config, package, args.locale)
        reports.append(analyze_one(client, name, package, locale=locale, locale_source=locale_source, days=args.days, limit=args.limit, reports_bucket=reports_bucket, credentials_path=args.credentials))
    data = {
        "status": "ok" if all(report["status"] == "ok" for report in reports) else "partial_failure",
        "locale": args.locale,
        "analysisDays": args.days,
        "apps": reports,
    }
    if args.data_reports:
        data["dataReportFiles"] = [str(path) for path in write_data_reports(OUTPUT_DIR, reports)]
    print_or_save(data, args.format, args.output)
    return 0 if data["status"] == "ok" else 1


def command_validate_copy(args: argparse.Namespace) -> int:
    values = {
        field: value
        for field, value in (
            ("title", args.title),
            ("shortDescription", args.short_description),
            ("fullDescription", args.full_description),
        )
        if value is not None
    }
    if not values:
        raise ValueError("Provide at least one of --title, --short-description, or --full-description.")
    checks = validate_listing_text(values)
    data = {
        "status": "ok" if all(item["withinLimit"] for item in checks.values()) else "over_limit",
        "method": "Python Unicode code-point count; Play Console validation is authoritative.",
        "fieldChecks": checks,
    }
    print_or_save(data, args.format, args.output)
    return 0 if data["status"] == "ok" else 1


def main(argv: list[str] | None = None) -> int:
    parser = make_parser()
    args = parser.parse_args(argv)
    try:
        if args.command == "validate-copy":
            return command_validate_copy(args)
        selectors = getattr(args, "app", None)
        if isinstance(selectors, str):
            selectors = [selectors]
        apps = app_catalog(args.config, args.credentials, selectors)
        if args.command == "apps":
            return command_apps(args, apps)
        if args.command == "doctor":
            return command_doctor(args, apps)
        if args.command == "reviews":
            return command_reviews(args, apps)
        if args.command == "listing":
            return command_listing(args, apps)
        if args.command == "analyze":
            return command_analyze(args, apps)
        if args.command == "analyze-all":
            return command_analyze_all(args, apps)
    except (AppConfigError, AuthenticationError, DiscoveryError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
