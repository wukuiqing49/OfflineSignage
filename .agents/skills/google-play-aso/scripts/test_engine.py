from __future__ import annotations

import json
import io
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import Mock, patch

from googleplay_aso.apps import AppConfigError, is_package_name, load_app_config, load_apps, load_reports_bucket, merge_apps, resolve_app
from googleplay_aso.aso import build_analysis, validate_listing_text
from googleplay_aso.auth import credential_file
from googleplay_aso.client import PlayClient
from googleplay_aso.cli import analysis_locale, app_catalog, main
from googleplay_aso.discovery import DiscoveryError, discover_apps
from googleplay_aso.installs import read_install_rows, summarize_installs
from googleplay_aso.health_reports import read_overview_rows, summarize_overview
from googleplay_aso.reviews import fetch_reviews, filter_reviews, normalize_review
from googleplay_aso.reports import to_markdown, write_data_reports, write_output
from googleplay_aso.report_io import decode_report
from googleplay_aso.store_performance import read_store_performance_rows, summarize_search_terms


def review(review_id: str, *, rating: int = 5, seconds: int = 1_700_000_000) -> dict:
    return {
        "reviewId": review_id,
        "authorName": "Reviewer",
        "comments": [{"userComment": {
            "starRating": rating,
            "reviewerLanguage": "en",
            "text": f"Review {review_id}",
            "lastModified": {"seconds": str(seconds), "nanos": 0},
        }}, {"developerComment": {"text": "Thanks"}}],
    }


class AuthenticationTests(unittest.TestCase):
    def test_requires_explicit_credential_environment(self):
        from googleplay_aso.auth import AuthenticationError
        with patch.dict("os.environ", {"GOOGLE_APPLICATION_CREDENTIALS": ""}):
            with self.assertRaises(AuthenticationError):
                credential_file()

    def test_credential_path_is_not_read_by_resolution(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "key.json"
            path.touch()
            with patch.dict("os.environ", {"GOOGLE_APPLICATION_CREDENTIALS": str(path)}):
                self.assertEqual(credential_file(), path)


class AppConfigTests(unittest.TestCase):
    def test_load_and_resolve_apps(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "apps.json"
            path.write_text(json.dumps({"apps": {"First": "com.example.first", "Second": "com.example.second"}}))
            apps = load_apps(path)
        self.assertEqual(resolve_app(apps, "com.example.second"), ("Second", "com.example.second"))

    def test_structured_config_keeps_default_locale(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "apps.json"
            path.write_text(json.dumps({"apps": {"Site": {"packageName": "com.example.site", "defaultLocale": "fr-FR"}}}))
            apps, locales = load_app_config(path)
            self.assertEqual(apps, {"Site": "com.example.site"})
            self.assertEqual(locales, {"com.example.site": "fr-FR"})
            self.assertEqual(analysis_locale(path, "com.example.site", None), ("fr-FR", "configured_default"))
            self.assertEqual(analysis_locale(path, "com.example.site", "de-DE"), ("de-DE", "requested"))

    def test_loads_optional_reports_bucket(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "apps.json"
            path.write_text(json.dumps({"reportsBucket": "pubsite_prod_rev_123", "apps": {"A": "com.example.a"}}))
            self.assertEqual(load_reports_bucket(path), "pubsite_prod_rev_123")

    def test_rejects_placeholder_and_duplicate_packages(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "apps.json"
            path.write_text(json.dumps({"apps": {"A": "REPLACE_WITH_PACKAGE"}}))
            with self.assertRaises(AppConfigError):
                load_apps(path)
            path.write_text(json.dumps({"apps": {"A": "com.example.app", "B": "com.example.app"}}))
            with self.assertRaises(AppConfigError):
                load_apps(path)

    def test_catalog_discovers_when_config_is_absent(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as folder:
            with patch("googleplay_aso.cli.authorized_http") as http, patch(
                "googleplay_aso.cli.discover_apps", return_value={"One": "com.example.one"}
            ) as discover:
                self.assertEqual(app_catalog(Path(folder) / "missing.json", None), {"One": "com.example.one"})
                discover.assert_called_once_with(http.return_value)

    def test_catalog_uses_verified_config_after_discovery_failure(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "apps.json"
            path.write_text(json.dumps({"apps": {"Verified": "com.example.verified"}}))
            with patch("googleplay_aso.cli.authorized_http"), patch(
                "googleplay_aso.cli.discover_apps", side_effect=DiscoveryError("HTTP 403")
            ), redirect_stderr(io.StringIO()):
                self.assertEqual(app_catalog(path, None), {"Verified": "com.example.verified"})

    def test_catalog_merges_by_package_with_configured_alias_precedence(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "apps.json"
            path.write_text(json.dumps({"apps": {"My alias": "com.one", "Other": "com.two"}}))
            with patch("googleplay_aso.cli.authorized_http"), patch(
                "googleplay_aso.cli.discover_apps",
                return_value={"Store title": "com.one", "Other": "com.three"},
            ):
                self.assertEqual(app_catalog(path, None), {
                    "My alias": "com.one", "Other": "com.two", "Other (com.three)": "com.three",
                })

    def test_merge_does_not_duplicate_packages(self):
        self.assertEqual(merge_apps({"Local": "com.one"}, {"Store": "com.one"}), {"Local": "com.one"})

    def test_explicit_package_bypasses_discovery_without_config(self):
        with tempfile.TemporaryDirectory() as folder, patch(
            "googleplay_aso.cli.authorized_http"
        ) as authorized:
            self.assertEqual(
                app_catalog(Path(folder) / "missing.json", None, ["com.wkq.site"]),
                {"com.wkq.site": "com.wkq.site"},
            )
            authorized.assert_not_called()

    def test_configured_alias_bypasses_discovery(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "apps.json"
            path.write_text(json.dumps({"apps": {"SiteReport": "com.wkq.site"}}))
            with patch("googleplay_aso.cli.authorized_http") as authorized:
                self.assertEqual(app_catalog(path, None, ["SiteReport"]), {"SiteReport": "com.wkq.site"})
                authorized.assert_not_called()

    def test_package_validation_rejects_arbitrary_dotted_text(self):
        self.assertTrue(is_package_name("com.wkq.site"))
        self.assertFalse(is_package_name("Site Report"))
        self.assertFalse(is_package_name("com..site"))


class BatchTests(unittest.TestCase):
    def test_batch_writes_one_data_report_per_app(self):
        reports = [
            {"appName": "First", "packageName": "com.one", "status": "ok", "analysisDays": 1},
            {"appName": "Second", "packageName": "com.two", "status": "partial_failure", "analysisDays": 1},
        ]
        with tempfile.TemporaryDirectory() as folder, patch(
            "googleplay_aso.cli.app_catalog", return_value={"First": "com.one", "Second": "com.two"}
        ), patch("googleplay_aso.cli.make_client"), patch(
            "googleplay_aso.cli.analyze_one", side_effect=reports
        ), patch("googleplay_aso.cli.OUTPUT_DIR", Path(folder)), redirect_stdout(io.StringIO()) as output:
            result = main(["--config", str(Path(folder) / "missing.json"), "analyze-all", "--data-reports", "--format", "json"])
        payload = json.loads(output.getvalue())
        self.assertEqual(result, 1)
        self.assertEqual(len(payload["dataReportFiles"]), 2)
        self.assertTrue(all(Path(path).name.startswith(package) for path, package in zip(
            payload["dataReportFiles"], ("com.one", "com.two")
        )))

    def test_cli_with_explicit_package_works_without_discovery(self):
        output = io.StringIO()
        client = Mock(spec=PlayClient)
        client.list_reviews.return_value = {"reviews": []}
        client.get_listing.return_value = {"listing": {"title": "SiteReport"}}
        with tempfile.TemporaryDirectory() as folder, patch(
            "googleplay_aso.cli.authorized_http"
        ) as authorized, patch("googleplay_aso.cli.make_client", return_value=client), redirect_stdout(output):
            result = main(["--config", str(Path(folder) / "missing.json"), "analyze-all", "--app", "com.wkq.site"])
        self.assertEqual(result, 0)
        authorized.assert_not_called()
        self.assertEqual(json.loads(output.getvalue())["apps"][0]["packageName"], "com.wkq.site")

    def test_batch_discovers_once_and_continues_after_one_app_failure(self):
        output = io.StringIO()
        client = Mock(spec=PlayClient)
        client.list_reviews.side_effect = [RuntimeError("first failed"), {"reviews": []}]
        client.get_listing.side_effect = [RuntimeError("listing failed"), {"listing": {"title": "Second"}}]
        with tempfile.TemporaryDirectory() as folder, patch(
            "googleplay_aso.cli.app_catalog", return_value={"First": "com.one", "Second": "com.two"}
        ) as catalog, patch("googleplay_aso.cli.make_client", return_value=client), redirect_stdout(output):
            result = main(["--config", str(Path(folder) / "missing.json"), "analyze-all", "--limit", "1", "--format", "json"])
        self.assertEqual(result, 1)
        catalog.assert_called_once()
        payload = json.loads(output.getvalue())
        self.assertEqual([item["packageName"] for item in payload["apps"]], ["com.one", "com.two"])
        self.assertEqual([item["status"] for item in payload["apps"]], ["partial_failure", "ok"])

    def test_batch_selects_repeated_names_and_packages_once(self):
        output = io.StringIO()
        client = Mock(spec=PlayClient)
        client.list_reviews.return_value = {"reviews": []}
        client.get_listing.return_value = {"listing": {"title": "One"}}
        with tempfile.TemporaryDirectory() as folder, patch(
            "googleplay_aso.cli.app_catalog", return_value={"First": "com.one", "Second": "com.two"}
        ), patch("googleplay_aso.cli.make_client", return_value=client), redirect_stdout(output):
            result = main(["--config", str(Path(folder) / "missing.json"), "analyze-all", "--app", "First", "--app", "com.one", "--limit", "1"])
        self.assertEqual(result, 0)
        self.assertEqual(len(json.loads(output.getvalue())["apps"]), 1)


class DiscoveryTests(unittest.TestCase):
    def test_paginates_and_disambiguates_duplicate_titles(self):
        http = Mock()
        http.request.side_effect = [
            (Mock(status=200), json.dumps({"apps": [{"displayName": "Same", "packageName": "com.one"}], "nextPageToken": "two"}).encode()),
            (Mock(status=200), json.dumps({"apps": [{"displayName": "Same", "packageName": "com.two"}]}).encode()),
        ]
        self.assertEqual(discover_apps(http), {"Same": "com.one", "Same (com.two)": "com.two"})
        self.assertIn("pageToken=two", http.request.call_args.args[0])

    def test_permission_failure_does_not_echo_response_body(self):
        http = Mock()
        http.request.return_value = (Mock(status=403), b"secret response")
        with self.assertRaises(DiscoveryError) as raised:
            discover_apps(http)
        self.assertNotIn("secret response", str(raised.exception))

    def test_rejects_empty_app_list(self):
        http = Mock()
        http.request.return_value = (Mock(status=200), b"{}")
        with self.assertRaises(DiscoveryError):
            discover_apps(http)


class ReviewTests(unittest.TestCase):
    def test_paginates_and_keeps_app_scope_and_locale(self):
        client = Mock(spec=PlayClient)
        client.list_reviews.side_effect = [
            {"reviews": [review("1")], "tokenPagination": {"nextPageToken": "page-2"}},
            {"reviews": [review("2")], "tokenPagination": {}},
        ]
        result = fetch_reviews(client, "com.example.second", limit=2, locale="en-US")
        self.assertEqual([item["reviewId"] for item in result], ["1", "2"])
        self.assertEqual(client.list_reviews.call_args_list[0].args[0], "com.example.second")
        self.assertEqual(client.list_reviews.call_args_list[1].kwargs["token"], "page-2")
        self.assertEqual(client.list_reviews.call_args_list[0].kwargs["translation_language"], "en-US")

    def test_filters_dates_and_ratings_locally(self):
        now = datetime(2024, 1, 20, tzinfo=timezone.utc)
        recent = review("recent", rating=5, seconds=int(datetime(2024, 1, 15, tzinfo=timezone.utc).timestamp()))
        old = review("old", rating=5, seconds=int(datetime(2023, 12, 1, tzinfo=timezone.utc).timestamp()))
        low = review("low", rating=2, seconds=int(datetime(2024, 1, 16, tzinfo=timezone.utc).timestamp()))
        result = filter_reviews([recent, old, low], days=10, stars=5, now=now)
        self.assertEqual([item["reviewId"] for item in result], ["recent"])

    def test_normalizes_developer_reply(self):
        data = normalize_review(review("1"))
        self.assertEqual(data["rating"], 5)
        self.assertEqual(data["developerReply"], "Thanks")


class ClientAllowlistTests(unittest.TestCase):
    def test_listing_uses_insert_and_get_only(self):
        listing = Mock()
        listing.get.return_value.execute.return_value = {"title": "Example"}
        edits = Mock()
        edits.insert.return_value.execute.return_value = {"id": "temporary", "expiryTimeSeconds": "123"}
        edits.listings.return_value = listing
        service = Mock()
        service.edits.return_value = edits
        result = PlayClient(service).get_listing("com.example.app", "en-US")
        self.assertEqual(result["listing"]["title"], "Example")
        edits.insert.assert_called_once()
        listing.get.assert_called_once()
        edits.commit.assert_not_called()
        edits.delete.assert_not_called()
        listing.update.assert_not_called()
        listing.patch.assert_not_called()
        self.assertFalse(hasattr(PlayClient(service), "commit"))

    def test_reviews_use_bounded_retries(self):
        service = Mock()
        service.reviews.return_value.list.return_value.execute.return_value = {"reviews": []}
        PlayClient(service).list_reviews("com.example.app")
        service.reviews.return_value.list.return_value.execute.assert_called_once_with(num_retries=3)


class ReportTests(unittest.TestCase):
    def test_analysis_shows_current_listing_and_sample_only_rating(self):
        current_review = review("one", rating=5)
        report = build_analysis(
            app_name="SiteReport",
            package_name="com.wkq.site",
            locale="en-US",
            days=30,
            fetched_reviews=[current_review],
            filtered_reviews=[current_review],
            listing_result={"listing": {
                "title": "SiteReport", "shortDescription": "Offline reports", "fullDescription": "Create reports offline."
            }},
        )
        self.assertEqual(report["currentListing"]["fullDescription"]["text"], "Create reports offline.")
        self.assertEqual(report["currentListing"]["title"]["characters"], 10)
        self.assertEqual(report["reviewRating"]["average"], 5.0)
        self.assertEqual(report["reviewRating"]["distribution"]["5"], 1)
        self.assertIsNone(report["reviewRating"]["storeAverageRating"])
        report["status"] = "ok"
        markdown = to_markdown(report)
        self.assertLess(markdown.index("## 当前商店资料"), markdown.index("## 数据限制"))
        self.assertNotIn("## 建议草案", markdown)
        self.assertIn("# Google Play ASO 数据报告", markdown)
        self.assertIn("Create reports offline.", markdown)
        self.assertIn("商店整体评分：未取得", markdown)
        self.assertIn("样本均分：5.0/5", markdown)
        self.assertNotIn("Review one", markdown)

    def test_listing_character_limits(self):
        result = validate_listing_text({"title": "x" * 31, "shortDescription": "y" * 80})
        self.assertFalse(result["title"]["withinLimit"])
        self.assertTrue(result["shortDescription"]["withinLimit"])

    def test_output_cannot_escape_output_directory(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(ValueError):
                write_output(Path(folder), "../outside.md", "unsafe")

    def test_output_uses_new_name_instead_of_overwriting(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "report.md").write_text("old", encoding="utf-8")
            target = write_output(root, "report.md", "new")
            self.assertEqual(target.name, "report-2.md")
            self.assertEqual((root / "report.md").read_text(encoding="utf-8"), "old")

    def test_data_reports_are_isolated_per_app_and_do_not_overwrite(self):
        reports = [
            {"appName": "One", "packageName": "com.example.one", "status": "ok", "analysisDays": 1},
            {"appName": "Two", "packageName": "com.example.two", "status": "partial_failure", "analysisDays": 1},
        ]
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            first = write_data_reports(root, reports, day=datetime(2026, 9, 28).date())
            second = write_data_reports(root, reports, day=datetime(2026, 9, 28).date())
            self.assertEqual(len({*first, *second}), 4)
            self.assertIn("com.example.one", first[0].read_text(encoding="utf-8"))
            self.assertIn("com.example.two", first[1].read_text(encoding="utf-8"))

    def test_analysis_separates_facts_inferences_and_missing_data(self):
        report = build_analysis(
            app_name="Example",
            package_name="com.example.app",
            locale="en-US",
            days=30,
            fetched_reviews=[],
            filtered_reviews=[],
            listing_result=None,
            listing_error="HTTP 403",
        )
        self.assertIn("facts", report)
        self.assertIn("inferences", report)
        self.assertIn("missingData", report)
        self.assertIn("suggestions", report)
        self.assertEqual(len(report["dataSources"]), 2)
        self.assertIsNone(report["currentListing"])
        self.assertIsNone(report["reviewRating"]["average"])
        self.assertTrue(any("HTTP 403" in item for item in report["missingData"]))

    def test_copy_validation_runs_without_app_config(self):
        output = io.StringIO()
        with redirect_stdout(output):
            result = main(["validate-copy", "--title", "CaptionMeta"])
        self.assertEqual(result, 0)
        self.assertIn('"characters": 11', output.getvalue())


class InstallReportTests(unittest.TestCase):
    def test_cli_reads_selected_package_and_marks_report_failure(self):
        client = Mock(spec=PlayClient)
        client.list_reviews.return_value = {"reviews": []}
        client.get_listing.return_value = {"listing": {"title": "SiteReport"}}
        with tempfile.TemporaryDirectory() as folder, patch(
            "googleplay_aso.cli.make_client", return_value=client
        ), patch("googleplay_aso.cli.fetch_installs", side_effect=PermissionError("GCS denied")) as fetch, patch(
            "googleplay_aso.cli.fetch_search_terms", return_value={
                "source": "test", "window": {}, "daysWithData": 0, "missingDates": [],
                "completeWindow": False, "searchTerms": [], "totalVisitors": 0,
                "totalAcquisitions": 0, "requestedFiles": [], "foundFiles": [], "missingFiles": [],
            }
        ), patch("googleplay_aso.cli.fetch_overview", return_value={"source": "test", "foundFiles": [], "missingFiles": []}), redirect_stdout(io.StringIO()) as output:
            result = main(["--config", str(Path(folder) / "missing.json"), "analyze", "--app", "com.wkq.site", "--reports-bucket", "reports-id", "--days", "2", "--format", "json"])
        self.assertEqual(result, 1)
        self.assertEqual(fetch.call_args.args[:2], ("reports-id", "com.wkq.site"))
        self.assertEqual(json.loads(output.getvalue())["status"], "partial_failure")

    def test_rating_overview_uses_latest_total_not_daily_average(self):
        rows = read_overview_rows(
            "Date,Package Name,Daily Average Rating,Total Average Rating\n"
            "2026-09-01,com.wkq.site,5.0,4.5\n"
            "2026-09-02,com.wkq.site,0.0,4.5\n", "com.wkq.site", "ratings"
        )
        result = summarize_overview(rows, "ratings", days=2, today=datetime(2026, 9, 2).date())
        self.assertEqual(result["latestTotalAverageRating"], 4.5)
        self.assertIsNone(result["ratingCount"])

    def test_missing_crash_overview_is_not_zero_evidence(self):
        result = summarize_overview([], "crashes", days=2, today=datetime(2026, 9, 2).date())
        self.assertEqual(result["daysWithRows"], 0)
        self.assertIsNone(result["reportedANRs"])
        self.assertTrue(result["noRowsDoesNotMeanZero"])
        with self.assertRaises(ValueError):
            read_overview_rows("Date,Package Name,Daily Crashes,Daily ANRs\n"
                               "2026-09-02,com.other.app,0,1\n", "com.wkq.site", "crashes")

    def test_country_report_sums_days_without_mixing_stocks(self):
        csv_data = ("Date,Package name,Country,Daily User Installs,Daily User Uninstalls,Active Device Installs\n"
                    "2026-09-23,com.wkq.site,US,4,1,100\n"
                    "2026-09-23,com.wkq.site,GB,2,0,40\n"
                    "2026-09-24,com.wkq.site,US,3,1,102\n")
        rows = read_install_rows(csv_data, "com.wkq.site")
        summary = summarize_installs(rows, days=2, today=datetime(2026, 9, 24).date())
        self.assertEqual(summary["totalUserInstalls"], 9)
        self.assertEqual(summary["daily"][0]["userInstalls"], 6)
        self.assertTrue(summary["completeWindow"])
        self.assertNotIn("dau", summary)

    def test_rejects_wrong_package_and_duplicate_dimension(self):
        payload = "Date,Package Name,Country,Daily User Installs,Daily User Uninstalls\n2026-09-24,com.other.app,US,1,0\n"
        with self.assertRaises(ValueError):
            read_install_rows(payload, "com.wkq.site")
        rows = [{"date": "2026-09-24", "country": "US", "userInstalls": 1, "userUninstalls": 0}]
        with self.assertRaises(ValueError):
            summarize_installs(rows * 2, days=1, today=datetime(2026, 9, 24).date())

    def test_partial_period_is_not_complete(self):
        rows = [{"date": "2026-09-24", "country": "US", "userInstalls": 1, "userUninstalls": 0}]
        summary = summarize_installs(rows, days=30, today=datetime(2026, 9, 24).date())
        self.assertFalse(summary["completeWindow"])
        self.assertIn("2026-09-23", summary["missingDates"])


class StorePerformanceTests(unittest.TestCase):
    def test_decodes_utf16_and_utf8_reports(self):
        self.assertEqual(decode_report("Date,Value\n".encode("utf-16")), "Date,Value\n")
        self.assertEqual(decode_report(b"\xef\xbb\xbfDate,Value\n"), "Date,Value\n")

    def test_parses_search_rows_and_recomputes_weighted_conversion(self):
        csv_data = (
            "Date,Package name,Traffic source,Search term,UTM source,UTM campaign,Store listing acquisitions,Store listing visitors,Store listing conversion rate\n"
            "2026-09-23,com.wkq.site,Google Play search,inspection app,,,2,10,0.2\n"
            "2026-09-24,com.wkq.site,Google Play search,inspection app,,,9,90,0.1\n"
            "2026-09-24,com.wkq.site,Google Play search,Other,,,1,5,0.2\n"
            "2026-09-24,com.wkq.site,Other,,,,3,12,0.25\n"
            "2026-09-24,com.wkq.site,Google Play explore,,,,4,20,0.2\n"
        )
        rows = read_store_performance_rows(csv_data, "com.wkq.site")
        summary = summarize_search_terms(rows, days=2, today=datetime(2026, 9, 24).date())
        self.assertEqual(len(rows), 4)
        self.assertEqual(summary["searchTerms"][0]["searchTerm"], "inspection app")
        self.assertEqual(summary["searchTerms"][0]["conversionRate"], 0.11)
        self.assertTrue(summary["searchTerms"][1]["privacyAggregated"])
        self.assertEqual(summary["otherTrafficSourceVisitors"], 12)
        self.assertEqual(summary["searchTerms"][1]["trafficSource"], "google play search")
        self.assertTrue(summary["completeWindow"])

    def test_rejects_mixed_package_search_report(self):
        csv_data = (
            "Date,Package name,Traffic source,Search term,Store listing acquisitions,Store listing visitors\n"
            "2026-09-24,com.other,Google Play search,inspection,1,2\n"
        )
        with self.assertRaises(ValueError):
            read_store_performance_rows(csv_data, "com.wkq.site")


if __name__ == "__main__":
    unittest.main()
