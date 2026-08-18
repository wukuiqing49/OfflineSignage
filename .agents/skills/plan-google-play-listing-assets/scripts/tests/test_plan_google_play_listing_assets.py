from __future__ import annotations

import csv
import importlib.util
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


SCRIPTS = Path(__file__).resolve().parents[1]


def load_script(name: str):
    path = SCRIPTS / f"{name}.py"
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


init_workspace = load_script("init_workspace")
inspect_keyword_inputs = load_script("inspect_keyword_inputs")
analyze_keyword_inputs = load_script("analyze_keyword_inputs")
validate_seed_keywords = load_script("validate_seed_keywords")
validate_listing_briefs = load_script("validate_listing_briefs")
validate_video_brief = load_script("validate_video_brief")
export_prompt_files = load_script("export_prompt_files")


def write_keyword_metadata(directory: Path) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / "keyword-research-metadata.json"
    path.write_text(
        json.dumps({
            "tool_name": "Google Keyword Planner",
            "target_market": "United States",
            "locale": "en-US",
            "exported_at": "2026-08-14",
            "metric_definitions": {"volume": "Average monthly searches"},
        }),
        encoding="utf-8",
    )
    return path


def write_keyword_analysis(directory: Path, keyword: str = "batch photo processing") -> Path:
    path = directory / "keyword-research-analysis.csv"
    row = {column: "" for column in analyze_keyword_inputs.OUTPUT_COLUMNS}
    row.update({
        "keyword": keyword,
        "locale": "en-US",
        "tool_name": "Google Keyword Planner",
        "target_market": "United States",
        "exported_at": "2026-08-14",
        "source_file": "tool-en-US.csv",
        "source_row": "2",
        "matched_seed": "batch photo processing",
        "product_evidence": "PF-01",
        "relevance_score": "100",
        "recommendation": "REVIEW_RELEVANT",
        "semantic_status": "SELECTABLE",
        "semantic_reason": "Matches the current project's verified primary workflow",
        "average_monthly_searches": "100",
        "competition": "Low",
        "competition_index": "10",
        "raw_metrics": "{}",
    })
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=analyze_keyword_inputs.OUTPUT_COLUMNS)
        writer.writeheader()
        writer.writerow(row)
    return path


def brief_text(
    scene_headers: list[tuple[str, str]],
    duration: int,
    status: str = "DRAFT",
) -> str:
    section_names = validate_video_brief.REQUIRED_SECTIONS
    blocks: list[str] = ["# VIDEO_BRIEF"]
    for index, name in enumerate(section_names, start=1):
        blocks.append(f"## {index}. {name}")
        if name == "Executive Summary":
            blocks.append(
                f"- Status: {status}\n- Video Type: preview\n"
                "- Strategy Reference: PLAY_ASSET_STRATEGY.md\n"
                f"- Duration Seconds: {duration}\n"
                "- Orientation: portrait\n"
                "- Production Resolution: 1080x1920\n"
            )
        elif name == "Storyboard":
            for scene_index, (start, end) in enumerate(scene_headers, start=1):
                fields = "\n".join(
                    f"- {field}: "
                    f"{'PF-01' if field == 'Product Feature Evidence' else 'CLIP-01' if field == 'Recording Clip ID' else 'value'}"
                    for field in validate_video_brief.SCENE_FIELDS
                )
                blocks.append(f"### Scene {scene_index:02d} | {start}-{end}\n\n{fields}")
        elif name == "Google Play Compliance Check":
            blocks.append(
                "- Official Sources Checked: https://support.google.com/\n"
                "- Checked At: 2026-08-14\n"
            )
        elif name == "Final Execution Prompt":
            blocks.append(
                f"制作 preview video，portrait，1080x1920，时长 {duration} seconds。"
                "只合成真实录屏 CLIP-01，禁止生成或重绘 App UI。"
            )
        else:
            blocks.append("N/A")
    return "\n\n".join(blocks) + "\n"


def strategy_text() -> str:
    blocks = ["# PLAY_ASSET_STRATEGY"]
    for index, name in enumerate(validate_listing_briefs.STRATEGY_SECTIONS, start=1):
        blocks.append(f"## {index}. {name}")
        if name == "Executive Summary":
            blocks.append(
                "- Status: READY\n- Asset Mode: PRODUCTION\n"
                "- Primary Marketing Message: Process photos in batches"
            )
        elif name == "Cross-Asset Message Map":
            blocks.append("Feature Graphic\nScreenshots\nPreview Video")
        elif name == "Verified Product Facts":
            blocks.append(
                "| Claim ID | Claim | Status | Advertisable | Evidence | Notes |\n"
                "|---|---|---|---|---|---|\n"
                "| PF-01 | Batch processing | VERIFIED | true | source.kt:10 | reachable |"
            )
        elif name == "Required Assets":
            blocks.append(
                "| Asset ID | Type | Path | Locale | Status | Usage |\n"
                "|---|---|---|---|---|---|\n"
                "| BRAND-ICON-01 | app icon | icon.png | all | READY | brand |\n"
                "| SHOT-01 | screenshot | shot.png | en-US | READY | screenshot |\n"
                "| CLIP-01 | recording clip | clip.mp4 | en-US | READY | video |"
            )
        elif name == "Selected Keywords":
            blocks.append(
                "| Keyword | Classification | Source | Metrics | Product Fact | Reason |\n"
                "|---|---|---|---|---|---|\n"
                "| batch photo processing | SELECTED_PRIMARY | tool-en-US.csv | volume=100 | PF-01 | relevant |"
            )
        elif name == "Official Sources Checked":
            blocks.append(
                "- Sources: https://support.google.com/googleplay/android-developer/\n"
                "- Checked At: 2026-08-14\n"
                "- Current Policy Status: VERIFIED"
            )
        else:
            blocks.append("N/A")
    return "\n\n".join(blocks) + "\n"


def feature_graphic_text() -> str:
    blocks = ["# FEATURE_GRAPHIC_BRIEF"]
    for index, name in enumerate(validate_listing_briefs.FEATURE_SECTIONS, start=1):
        blocks.append(f"## {index}. {name}")
        if name == "Executive Summary":
            blocks.append(
                "- Status: READY_FOR_PRODUCTION\n"
                "- Strategy Reference: PLAY_ASSET_STRATEGY.md\n"
                "- Canvas Size: 1024x500"
            )
        elif name == "Message":
            blocks.append("- Product Feature Evidence: PF-01")
        elif name == "Composition":
            blocks.append("- Real UI Asset ID: SHOT-01\n- App Icon Asset ID: BRAND-ICON-01")
        elif name == "Compliance Check":
            blocks.append(
                "- Official Sources Checked: https://support.google.com/googleplay/android-developer/\n"
                "- Checked At: 2026-08-14\n"
                "- Canvas Size: PASS\n- File Format: PASS\n- Real UI Only: PASS"
            )
        elif name == "Final Image Prompt":
            blocks.append(
                "创建 1024x500 Feature Graphic，仅使用 BRAND-ICON-01 和 SHOT-01，"
                "禁止生成或重绘 App UI。"
            )
        else:
            blocks.append("N/A")
    return "\n\n".join(blocks) + "\n"


def screenshots_text(
    count: int = 1,
    source_screenshot_id: str = "SHOT-01",
    product_evidence: str = "PF-01",
    device_sets: str | None = None,
    output_format: str | None = None,
    orientation: str = "portrait",
) -> str:
    blocks = ["# SCREENSHOT_BRIEF"]
    for index, name in enumerate(validate_listing_briefs.SCREENSHOT_SECTIONS, start=1):
        blocks.append(f"## {index}. {name}")
        if name == "Executive Summary":
            summary = (
                "- Status: READY_FOR_PRODUCTION\n"
                "- Strategy Reference: PLAY_ASSET_STRATEGY.md\n"
                f"- Screenshot Count: {count}\n"
                f"- Orientation: {orientation}"
            )
            if device_sets:
                summary += f"\n- Device Sets: {device_sets}"
            if output_format:
                summary += f"\n- Output Format: {output_format}"
            blocks.append(summary)
        elif name == "Screenshots":
            fields = {
                "Purpose": "Show batch processing",
                "Device Type": "phone",
                "Locale": "en-US",
                "Orientation": orientation,
                "Real App Screen": "Batch queue",
                "Starting State": "Three files selected",
                "Demo Data": "Licensed sample photos",
                "Headline": "Process photos together",
                "Supporting Text": "Keep each task visible",
                "Text Position": "top",
                "Visual Focus": "batch queue",
                "Crop": "no UI crop",
                "Device Frame": "none",
                "Background Direction": "brand neutral",
                "Product Feature Evidence": product_evidence,
                "Keyword Relationship": "batch photo processing",
                "Source Screenshot ID": source_screenshot_id,
                "Required Assets": "SHOT-01",
            }
            field_text = "\n".join(f"- {key}: {value}" for key, value in fields.items())
            blocks.append(
                "### Screenshot 01\n\n"
                f"{field_text}\n\n"
                "#### Final Image Prompt\n\n"
                "为 phone portrait 商店截图使用 SHOT-01。Headline: Process photos together。"
                "Supporting Text: Keep each task visible。禁止生成或重绘 App UI。"
            )
        elif name == "Compliance Check":
            blocks.append(
                "- Official Sources Checked: https://support.google.com/googleplay/android-developer/\n"
                "- Checked At: 2026-08-14\n"
                "- Screenshot Count: PASS\n- Real UI Only: PASS\n- Claims Verified: PASS"
            )
        else:
            blocks.append("N/A")
    return "\n\n".join(blocks) + "\n"


class InitWorkspaceTest(unittest.TestCase):
    def test_creates_and_preserves_keyword_readme(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            directories, readme, copied = init_workspace.initialize(root)
            self.assertEqual(9, len(directories))
            self.assertTrue(all(path.is_dir() for path in directories))
            self.assertEqual(
                {
                    "input/keywords", "input/brand", "input/screenshots", "input/recordings",
                    "output/strategy", "output/feature-graphic", "output/screenshots",
                    "output/screenshots/prompts", "output/video",
                },
                {path.relative_to(root / ".ai-work" / "play-assets").as_posix() for path in directories},
            )
            self.assertTrue(copied)
            self.assertTrue(readme.is_file())
            self.assertTrue((readme.parent / "keyword-research-metadata.json").is_file())
            self.assertTrue((root / ".ai-work/play-assets/project-context.json").is_file())
            readme.write_text("custom\n", encoding="utf-8")
            _, _, copied_again = init_workspace.initialize(root)
            self.assertFalse(copied_again)
            self.assertEqual("custom\n", readme.read_text(encoding="utf-8"))

    def test_rejects_workspace_context_from_another_project(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            init_workspace.initialize(root)
            context = root / ".ai-work/play-assets/project-context.json"
            values = json.loads(context.read_text(encoding="utf-8"))
            values["project_id"] = "another-project"
            context.write_text(json.dumps(values), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "belongs to another project"):
                init_workspace.initialize(root)


class KeywordInputTest(unittest.TestCase):
    def test_inspects_csv_without_values(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            (directory / "tool-en-US.csv").write_text("keyword,volume\nbatch photos,100\n", encoding="utf-8")
            report = inspect_keyword_inputs.inspect_directory(directory)
            self.assertEqual("ready", report["status"])
            self.assertEqual(["keyword", "volume"], report["files"][0]["columns"])
            self.assertNotIn("batch photos", str(report))

    def test_rejects_parseable_but_unrelated_csv(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            (directory / "unrelated.csv").write_text("unrelated_column,other\nfoo,bar\n", encoding="utf-8")
            report = inspect_keyword_inputs.inspect_directory(directory)
            self.assertEqual("no_usable_research_data", report["status"])
            self.assertEqual(0, report["usable_file_count"])
            self.assertIn("no recognized keyword column", report["files"][0]["readiness_issues"])

    def test_requires_research_metric_column(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            (directory / "keywords-only.csv").write_text(
                "keyword,category\nbatch photos,feature\n",
                encoding="utf-8",
            )
            report = inspect_keyword_inputs.inspect_directory(directory)
            self.assertEqual("no_usable_research_data", report["status"])
            self.assertIn("no recognized research metric column", report["files"][0]["readiness_issues"])

    def test_inspects_keyword_planner_utf16_tsv_with_preamble(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            path = directory / "Keyword Stats.csv"
            path.write_text(
                "Keyword Stats 2026-08-14\n"
                "2025-08-01 - 2026-07-31\n"
                "Keyword\tCurrency\tAvg. monthly searches\tCompetition\n"
                "batch photo editor\tCNY\t5000\tLow\n"
                "image format converter\tCNY\t50000\tLow\n",
                encoding="utf-16",
            )
            report = inspect_keyword_inputs.inspect_directory(directory)
            item = report["files"][0]
            self.assertEqual("ready", report["status"])
            self.assertEqual("utf-16", item["encoding"])
            self.assertEqual("\t", item["delimiter"])
            self.assertEqual(3, item["header_row"])
            self.assertEqual(2, item["preamble_row_count"])
            self.assertEqual(2, item["row_count"])
            self.assertEqual(["Keyword"], item["keyword_columns"])
            self.assertIn("Avg. monthly searches", item["research_columns"])
            self.assertNotIn("batch photo editor", str(report))

    def test_requires_source_metadata_for_formal_readiness(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            (directory / "tool-en-US.csv").write_text(
                "keyword,volume\nbatch photos,100\n",
                encoding="utf-8",
            )
            report = inspect_keyword_inputs.inspect_directory(directory, require_metadata=True)
            self.assertEqual("needs_metadata", report["status"])
            write_keyword_metadata(directory)
            report = inspect_keyword_inputs.inspect_directory(directory, require_metadata=True)
            self.assertEqual("ready", report["status"])

    def test_rejects_invalid_metadata_date_and_unrelated_metric_definition(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            (directory / "tool-en-US.csv").write_text(
                "keyword,volume\nbatch photos,100\n",
                encoding="utf-8",
            )
            write_keyword_metadata(directory)
            metadata_path = directory / inspect_keyword_inputs.METADATA_FILE_NAME
            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
            metadata["exported_at"] = "2026-02-30"
            metadata["metric_definitions"] = {"difficulty": "Keyword difficulty"}
            metadata_path.write_text(json.dumps(metadata), encoding="utf-8")

            report = inspect_keyword_inputs.inspect_directory(directory, require_metadata=True)

            self.assertEqual("needs_metadata", report["status"])
            self.assertTrue(any("valid YYYY-MM-DD" in issue for issue in report["metadata"]["issues"]))

            metadata["exported_at"] = "2026-02-28"
            metadata_path.write_text(json.dumps(metadata), encoding="utf-8")
            report = inspect_keyword_inputs.inspect_directory(directory, require_metadata=True)
            self.assertEqual("needs_metadata", report["status"])
            self.assertTrue(any("does not describe" in issue for issue in report["metadata"]["issues"]))

    def test_inspects_xlsx_without_values(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            path = directory / "tool-en-US.xlsx"
            with zipfile.ZipFile(path, "w") as workbook:
                workbook.writestr(
                    "xl/workbook.xml",
                    '<?xml version="1.0"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
                    'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>'
                    '<sheet name="Keywords" sheetId="1" r:id="rId1"/></sheets></workbook>',
                )
                workbook.writestr(
                    "xl/_rels/workbook.xml.rels",
                    '<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                    '<Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>',
                )
                workbook.writestr(
                    "xl/sharedStrings.xml",
                    '<?xml version="1.0"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
                    '<si><t>keyword</t></si><si><t>volume</t></si><si><t>batch photos</t></si></sst>',
                )
                workbook.writestr(
                    "xl/worksheets/sheet1.xml",
                    '<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
                    '<sheetData><row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>'
                    '<row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2"><v>100</v></c></row></sheetData></worksheet>',
                )

            report = inspect_keyword_inputs.inspect_directory(directory)
            self.assertEqual("ready", report["status"])
            self.assertEqual(["keyword", "volume"], report["files"][0]["sheets"][0]["columns"])
            self.assertNotIn("batch photos", str(report))


class KeywordAnalysisTest(unittest.TestCase):
    def test_normalizes_utf16_keyword_planner_rows(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            input_dir = root / "input"
            write_keyword_metadata(input_dir)
            (input_dir / "tool-en-US.csv").write_text(
                "Keyword Stats\n"
                "2025-08-01 - 2026-07-31\n"
                "Keyword\tAvg. monthly searches\tCompetition\tCompetition (indexed value)\n"
                "compress photo\t5000000\tLow\t4\n"
                "online photo compressor\t500000\tLow\t8\n",
                encoding="utf-16",
            )
            seed_csv = root / "seeds.csv"
            seed_csv.write_text(
                "seed_keyword,locale,category,search_intent,product_evidence,rationale,status\n"
                "batch image compressor,en-US,core-task,compress,PF-02,verified,CANDIDATE_SEED\n",
                encoding="utf-8",
            )
            output = root / "analysis.csv"
            row_count, review_count = analyze_keyword_inputs.analyze(input_dir, seed_csv, output)
            self.assertEqual(2, row_count)
            self.assertEqual(1, review_count)
            with output.open("r", encoding="utf-8", newline="") as handle:
                rows = list(csv.DictReader(handle))
            self.assertEqual("compress photo", rows[0]["keyword"])
            self.assertEqual("5000000", rows[0]["average_monthly_searches"])
            risky = next(row for row in rows if row["keyword"] == "online photo compressor")
            self.assertEqual("online", risky["intent_risk"])
            self.assertEqual("REVIEW_INTENT_RISK", risky["recommendation"])

    def test_preserves_physical_source_row_after_blank_lines(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            input_dir = root / "input"
            write_keyword_metadata(input_dir)
            (input_dir / "tool-en-US.csv").write_text(
                "keyword,volume\n\nbatch photos,100\n",
                encoding="utf-8",
            )
            seed_csv = root / "seeds.csv"
            seed_csv.write_text(
                "seed_keyword,locale,category,search_intent,product_evidence,rationale,status\n"
                "batch photos,en-US,core-task,edit,PF-01,verified,CANDIDATE_SEED\n",
                encoding="utf-8",
            )
            output = root / "analysis.csv"

            analyze_keyword_inputs.analyze(input_dir, seed_csv, output)

            with output.open("r", encoding="utf-8", newline="") as handle:
                row = next(csv.DictReader(handle))
            self.assertEqual("3", row["source_row"])

    def test_supports_a_non_image_project_without_domain_vocabulary(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            input_dir = root / "input"
            write_keyword_metadata(input_dir)
            (input_dir / "tool-en-US.csv").write_text(
                "keyword,volume\ndaily habit tracker,1000\nhabit tracking app,800\n",
                encoding="utf-8",
            )
            seed_csv = root / "seeds.csv"
            seed_csv.write_text(
                "seed_keyword,locale,category,search_intent,product_evidence,rationale,status\n"
                "daily habit tracker,en-US,product-category,track recurring habits,PF-01,"
                "Verified recurring habit tracking,CANDIDATE_SEED\n",
                encoding="utf-8",
            )
            output = root / "analysis.csv"

            row_count, review_count = analyze_keyword_inputs.analyze(input_dir, seed_csv, output)

            self.assertEqual(2, row_count)
            self.assertEqual(2, review_count)
            with output.open("r", encoding="utf-8", newline="") as handle:
                rows = list(csv.DictReader(handle))
            self.assertTrue(all(row["product_evidence"] == "PF-01" for row in rows))

    def test_allows_sensitive_terms_verified_by_the_current_project(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            input_dir = root / "input"
            write_keyword_metadata(input_dir)
            (input_dir / "tool-en-US.csv").write_text(
                "keyword,volume\nai team collaboration,1000\nonline collaboration,900\n",
                encoding="utf-8",
            )
            seed_csv = root / "seeds.csv"
            seed_csv.write_text(
                "seed_keyword,locale,category,search_intent,product_evidence,rationale,status\n"
                "ai team collaboration,en-US,product-category,collaborate online,PF-01,"
                "Verified AI online team collaboration,CANDIDATE_SEED\n",
                encoding="utf-8",
            )
            output = root / "analysis.csv"

            analyze_keyword_inputs.analyze(input_dir, seed_csv, output)

            with output.open("r", encoding="utf-8", newline="") as handle:
                rows = list(csv.DictReader(handle))
            self.assertTrue(all(not row["intent_risk"] for row in rows))


class SeedValidationTest(unittest.TestCase):
    def test_accepts_candidate_seed_rows(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "seed.csv"
            categories = [
                "product-category", "product-category", "core-task", "core-task", "feature",
                "feature", "differentiator", "problem", "scenario", "scenario",
            ]
            rows = [
                f"keyword {index},en-US,{category},find product option {index},PF-{index},"
                f"Matches verified product fact {index},CANDIDATE_SEED"
                for index, category in enumerate(categories, start=1)
            ]
            path.write_text(
                "seed_keyword,locale,category,search_intent,product_evidence,rationale,status\n"
                + "\n".join(rows) + "\n",
                encoding="utf-8",
            )
            errors, warnings = validate_seed_keywords.validate(path)
            self.assertEqual([], errors)
            self.assertEqual([], warnings)

    def test_rejects_missing_product_category_anchor(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "seed.csv"
            rows = [
                f"keyword {index},en-US,feature,find feature option {index},PF-{index},"
                f"Matches verified feature fact {index},CANDIDATE_SEED"
                for index in range(1, 11)
            ]
            path.write_text(
                "seed_keyword,locale,category,search_intent,product_evidence,rationale,status\n"
                + "\n".join(rows) + "\n",
                encoding="utf-8",
            )

            errors, warnings = validate_seed_keywords.validate(path)

            self.assertTrue(any("product-category anchor" in error for error in errors))
            self.assertTrue(any("intent categories" in warning for warning in warnings))

    def test_rejects_token_equivalent_keywords_and_warns_on_high_overlap(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "seed.csv"
            keywords = [
                "photo batch editor",
                "editor for batch photo",
                "android photo editor",
                "photo editor android app",
                "compress images",
                "resize pictures",
                "convert image format",
                "remove photo background",
                "passport photo maker",
                "social media image creator",
            ]
            categories = [
                "product-category", "product-category", "product-category", "scenario", "core-task",
                "core-task", "core-task", "feature", "problem", "scenario",
            ]
            rows = [
                f"{keyword},en-US,{category},find a matching product,PF-{index},"
                f"Matches verified product fact {index},CANDIDATE_SEED"
                for index, (keyword, category) in enumerate(zip(keywords, categories), start=1)
            ]
            path.write_text(
                "seed_keyword,locale,category,search_intent,product_evidence,rationale,status\n"
                + "\n".join(rows) + "\n",
                encoding="utf-8",
            )

            errors, warnings = validate_seed_keywords.validate(path)

            self.assertTrue(any("token-equivalent" in error for error in errors))
            self.assertTrue(any("high lexical overlap" in warning for warning in warnings))

    def test_rejects_generic_search_intent_label(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "seed.csv"
            categories = ["product-category", "core-task", "problem"] + ["feature"] * 7
            rows = [
                f"keyword {index},en-US,{category},commercial,PF-{index},"
                f"Matches verified product fact {index},CANDIDATE_SEED"
                for index, category in enumerate(categories, start=1)
            ]
            path.write_text(
                "seed_keyword,locale,category,search_intent,product_evidence,rationale,status\n"
                + "\n".join(rows) + "\n",
                encoding="utf-8",
            )

            errors, _ = validate_seed_keywords.validate(path)

            self.assertTrue(any("must describe the user's query goal" in error for error in errors))

    def test_requires_exactly_ten_english_seed_rows(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "seed.csv"
            rows = [
                f"keyword {index},en-US,feature,commercial,PF-{index},verified feature,CANDIDATE_SEED"
                for index in range(1, 10)
            ]
            path.write_text(
                "seed_keyword,locale,category,search_intent,product_evidence,rationale,status\n"
                + "\n".join(rows) + "\n",
                encoding="utf-8",
            )
            errors, _ = validate_seed_keywords.validate(path)
            self.assertTrue(any("exactly 10" in error for error in errors))

    def test_rejects_non_english_locale_and_keyword(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "seed.csv"
            rows = [
                f"keyword {index},en-US,feature,commercial,PF-{index},verified feature,CANDIDATE_SEED"
                for index in range(1, 11)
            ]
            rows[0] = "批量照片,zh-CN,feature,commercial,PF-1,verified feature,CANDIDATE_SEED"
            path.write_text(
                "seed_keyword,locale,category,search_intent,product_evidence,rationale,status\n"
                + "\n".join(rows) + "\n",
                encoding="utf-8",
            )
            errors, _ = validate_seed_keywords.validate(path)
            self.assertTrue(any("locale must be en-US" in error for error in errors))
            self.assertTrue(any("English ASCII phrase" in error for error in errors))

    def test_rejects_research_metrics(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "seed.csv"
            path.write_text(
                "seed_keyword,locale,category,search_intent,product_evidence,rationale,status,search_volume\n"
                "batch photos,en-US,feature,commercial,PF-1,verified,CANDIDATE_SEED,100\n",
                encoding="utf-8",
            )
            errors, _ = validate_seed_keywords.validate(path)
            self.assertTrue(any("metric columns" in error for error in errors))

    def test_accepts_keyword_only_markdown_matching_csv_order(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            csv_path = root / "seed.csv"
            markdown_path = root / "seed.md"
            keywords = [f"keyword {index}" for index in range(1, 11)]
            rows = [
                f"{keyword},en-US,feature,commercial,PF-{index},verified feature,CANDIDATE_SEED"
                for index, keyword in enumerate(keywords, start=1)
            ]
            csv_path.write_text(
                "seed_keyword,locale,category,search_intent,product_evidence,rationale,status\n"
                + "\n".join(rows) + "\n",
                encoding="utf-8",
            )
            markdown_path.write_text(
                "# Seed Keywords\n\n```text\n" + "\n".join(keywords) + "\n```\n",
                encoding="utf-8",
            )
            errors = validate_seed_keywords.validate_markdown(
                markdown_path,
                validate_seed_keywords.read_keywords(csv_path),
            )
            self.assertEqual([], errors)

    def test_rejects_explanations_in_seed_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "seed.md"
            path.write_text(
                "# Seed Keywords\n\n说明：候选词\n\n```text\nkeyword 1\n```\n",
                encoding="utf-8",
            )
            errors = validate_seed_keywords.validate_markdown(path, ["keyword 1"])
            self.assertTrue(any("must contain only" in error for error in errors))


class BriefValidationTest(unittest.TestCase):
    def test_accepts_contiguous_storyboard(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "VIDEO_BRIEF.md"
            path.write_text(brief_text([("00:00", "00:03")], 3), encoding="utf-8")
            errors, _ = validate_video_brief.validate(path)
            self.assertEqual([], errors)

    def test_rejects_storyboard_gap(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "VIDEO_BRIEF.md"
            path.write_text(
                brief_text([("00:00", "00:02"), ("00:03", "00:04")], 4),
                encoding="utf-8",
            )
            errors, _ = validate_video_brief.validate(path)
            self.assertTrue(any("previous scene ends" in error for error in errors))

    def test_allows_hex_colors_but_rejects_rank_claims(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "VIDEO_BRIEF.md"
            valid = brief_text([("00:00", "00:03")], 3).replace(
                "- Visual Focus: value",
                "- Visual Focus: use #17211B and #1B6B3A",
            )
            path.write_text(valid, encoding="utf-8")
            errors, _ = validate_video_brief.validate(path)
            self.assertEqual([], errors)

            path.write_text(valid.replace("- Purpose: value", "- Purpose: #1 app"), encoding="utf-8")
            errors, _ = validate_video_brief.validate(path)
            self.assertTrue(any("banned marketing phrase" in error for error in errors))


class ListingBriefValidationTest(unittest.TestCase):
    def write_briefs(self, root: Path, screenshot_content: str) -> tuple[Path, Path, Path, Path]:
        strategy = root / "PLAY_ASSET_STRATEGY.md"
        feature = root / "FEATURE_GRAPHIC_BRIEF.md"
        screenshots = root / "SCREENSHOT_BRIEF.md"
        video = root / "VIDEO_BRIEF.md"
        for asset_name in ("icon.png", "shot.png", "clip.mp4"):
            (root / asset_name).write_bytes(b"test asset")
        write_keyword_analysis(root)
        strategy.write_text(strategy_text(), encoding="utf-8")
        feature.write_text(feature_graphic_text(), encoding="utf-8")
        screenshots.write_text(screenshot_content, encoding="utf-8")
        video.write_text(brief_text([("00:00", "00:03")], 3), encoding="utf-8")
        return strategy, feature, screenshots, video

    def test_accepts_complete_listing_package(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            paths = self.write_briefs(Path(temp), screenshots_text())
            errors, warnings = validate_listing_briefs.validate(*paths, require_complete_package=True)
            self.assertEqual([], errors)
            self.assertEqual([], warnings)

    def test_allows_hex_colors_but_rejects_rank_claims(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            paths = self.write_briefs(root, screenshots_text())
            paths[0].write_text(
                paths[0].read_text(encoding="utf-8") + "\nColors: #17211B and #1B6B3A\n",
                encoding="utf-8",
            )
            errors, _ = validate_listing_briefs.validate(*paths)
            self.assertEqual([], errors)

            paths[0].write_text(
                paths[0].read_text(encoding="utf-8") + "\nClaim: #1 app\n",
                encoding="utf-8",
            )
            errors, _ = validate_listing_briefs.validate(*paths)
            self.assertTrue(any("banned marketing phrase" in error for error in errors))

    def test_rejects_screenshot_count_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            paths = self.write_briefs(Path(temp), screenshots_text(count=2))
            errors, _ = validate_listing_briefs.validate(*paths)
            self.assertTrue(any("2 but 1 entries" in error for error in errors))

    def test_rejects_missing_source_and_product_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            paths = self.write_briefs(
                Path(temp),
                screenshots_text(source_screenshot_id="", product_evidence=""),
            )
            errors, _ = validate_listing_briefs.validate(*paths)
            self.assertTrue(any("Product Feature Evidence" in error for error in errors))
            self.assertTrue(any("Source Screenshot ID" in error for error in errors))

    def test_rejects_unknown_claim_and_asset_references(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            content = screenshots_text().replace("PF-01", "PF-99").replace("SHOT-01", "SHOT-99")
            paths = self.write_briefs(Path(temp), content)
            errors, _ = validate_listing_briefs.validate(*paths)
            self.assertTrue(any("unknown Claim ID: PF-99" in error for error in errors))
            self.assertTrue(any("available strategy Asset ID: SHOT-99" in error for error in errors))

    def test_rejects_unknown_asset_ids_in_final_prompts(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            content = screenshots_text().replace("使用 SHOT-01", "使用 SHOT-404")
            paths = self.write_briefs(root, content)
            paths[3].write_text(
                paths[3].read_text(encoding="utf-8").replace("录屏 CLIP-01", "录屏 CLIP-404"),
                encoding="utf-8",
            )
            errors, _ = validate_listing_briefs.validate(*paths)
            self.assertTrue(any("unknown Asset ID: SHOT-404" in error for error in errors))
            self.assertTrue(any("unknown Asset ID: CLIP-404" in error for error in errors))

    def test_rejects_selected_keyword_absent_from_analysis(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            paths = self.write_briefs(root, screenshots_text())
            paths[0].write_text(
                paths[0].read_text(encoding="utf-8").replace(
                    "batch photo processing",
                    "invented keyword",
                ),
                encoding="utf-8",
            )
            errors, _ = validate_listing_briefs.validate(*paths)
            self.assertTrue(any("absent from keyword analysis" in error for error in errors))

    def test_rejects_keyword_without_current_project_semantic_review(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            paths = self.write_briefs(root, screenshots_text())
            analysis = root / "keyword-research-analysis.csv"
            text = analysis.read_text(encoding="utf-8")
            analysis.write_text(
                text.replace("SELECTABLE", "PENDING_REVIEW").replace(
                    "Matches the current project's verified primary workflow",
                    "",
                ),
                encoding="utf-8",
            )

            errors, _ = validate_listing_briefs.validate(*paths)

            self.assertTrue(any("semantic review" in error for error in errors))

    def test_rejects_ready_strategy_with_unverified_current_policy(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            paths = self.write_briefs(root, screenshots_text())
            paths[0].write_text(
                paths[0].read_text(encoding="utf-8").replace(
                    "Current Policy Status: VERIFIED",
                    "Current Policy Status: UNVERIFIED_CURRENT_POLICY",
                ),
                encoding="utf-8",
            )

            errors, _ = validate_listing_briefs.validate(*paths)

            self.assertTrue(any("READY requires Current Policy Status: VERIFIED" in error for error in errors))


class PromptExportTest(unittest.TestCase):
    def write_prompt_briefs(self, root: Path) -> tuple[Path, Path, Path, Path]:
        init_workspace.initialize(root)
        strategy = root / "PLAY_ASSET_STRATEGY.md"
        feature = root / "FEATURE_GRAPHIC_BRIEF.md"
        screenshots = root / "SCREENSHOT_BRIEF.md"
        video = root / "VIDEO_BRIEF.md"
        for asset_name in ("icon.png", "shot.png", "clip.mp4"):
            (root / asset_name).write_bytes(b"test asset")
        keyword_dir = root / ".ai-work" / "play-assets" / "input" / "keywords"
        keyword_dir.mkdir(parents=True, exist_ok=True)
        write_keyword_metadata(keyword_dir)
        (keyword_dir / "tool-en-US.csv").write_text(
            "keyword,volume\nbatch photo processing,100\n",
            encoding="utf-8",
        )
        strategy.write_text(strategy_text(), encoding="utf-8")
        write_keyword_analysis(root)
        feature.write_text(feature_graphic_text(), encoding="utf-8")
        screenshots.write_text(screenshots_text(), encoding="utf-8")
        video.write_text(
            brief_text([("00:00", "00:03")], 3, status="READY_FOR_PRODUCTION"),
            encoding="utf-8",
        )
        return strategy, feature, screenshots, video

    def test_exports_and_checks_standalone_prompt_files(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            strategy, feature, screenshots, video = self.write_prompt_briefs(root)
            output = root / "output"
            errors, warnings, written = export_prompt_files.process(
                feature,
                screenshots,
                video,
                output,
                strategy_brief=strategy,
                project_root=root,
            )
            self.assertEqual([], errors)
            self.assertEqual([], warnings)
            self.assertEqual(3, len(written))

            feature_prompt = output / "feature-graphic" / "FEATURE_GRAPHIC_PROMPT.md"
            screenshot_prompt = output / "screenshots" / "prompts" / "SCREENSHOT_01_PROMPT.md"
            video_prompt = output / "video" / "VIDEO_PROMPT.md"
            self.assertIn("BRAND-ICON-01", feature_prompt.read_text(encoding="utf-8"))
            self.assertIn("SHOT-01", screenshot_prompt.read_text(encoding="utf-8"))
            self.assertIn("CLIP-01", video_prompt.read_text(encoding="utf-8"))

            check_errors, check_warnings, check_written = export_prompt_files.process(
                feature,
                screenshots,
                video,
                output,
                check=True,
                strategy_brief=strategy,
                project_root=root,
            )
            self.assertEqual([], check_errors)
            self.assertEqual([], check_warnings)
            self.assertEqual([], check_written)

    def test_exports_phone_and_tablet_prompt_sets_with_exact_contracts(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            strategy, feature, screenshots, video = self.write_prompt_briefs(root)
            screenshots.write_text(
                screenshots_text(
                    device_sets="Phone=1920x1080, Tablet=2560x1600",
                    output_format="opaque PNG or JPEG",
                    orientation="landscape",
                ),
                encoding="utf-8",
            )
            output = root / "output"
            errors, warnings, written = export_prompt_files.process(
                None,
                screenshots,
                None,
                output,
                strategy_brief=strategy,
                project_root=root,
            )

            self.assertEqual([], errors)
            self.assertEqual([], warnings)
            self.assertEqual(2, len(written))
            phone_prompt = (output / "screenshots/prompts/phone/SCREENSHOT_01_PROMPT.md").read_text(encoding="utf-8")
            tablet_prompt = (output / "screenshots/prompts/tablet/SCREENSHOT_01_PROMPT.md").read_text(encoding="utf-8")
            self.assertIn("Canvas: 1920x1080", phone_prompt)
            self.assertIn("Orientation: landscape", phone_prompt)
            self.assertIn("Output: opaque PNG or JPEG", phone_prompt)
            self.assertNotIn("2560x1600", phone_prompt)
            self.assertIn("Canvas: 2560x1600", tablet_prompt)
            self.assertIn("Orientation: landscape", tablet_prompt)
            self.assertIn("Output: opaque PNG or JPEG", tablet_prompt)
            self.assertNotIn("1920x1080", tablet_prompt)

            check_errors, check_warnings, check_written = export_prompt_files.process(
                None,
                screenshots,
                None,
                output,
                check=True,
                strategy_brief=strategy,
                project_root=root,
            )
            self.assertEqual([], check_errors)
            self.assertEqual([], check_warnings)
            self.assertEqual([], check_written)

    def test_check_rejects_prompt_drift(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            strategy, feature, screenshots, video = self.write_prompt_briefs(root)
            output = root / "output"
            errors, _, _ = export_prompt_files.process(
                feature,
                screenshots,
                video,
                output,
                strategy_brief=strategy,
                project_root=root,
            )
            self.assertEqual([], errors)
            prompt = output / "video" / "VIDEO_PROMPT.md"
            prompt.write_text("modified\n", encoding="utf-8")
            check_errors, _, _ = export_prompt_files.process(
                feature,
                screenshots,
                video,
                output,
                check=True,
                strategy_brief=strategy,
                project_root=root,
            )
            self.assertTrue(any("differs from its source brief" in error for error in check_errors))

    def test_rejects_unresolved_prompt_placeholder(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            strategy, feature, screenshots, video = self.write_prompt_briefs(root)
            feature.write_text(
                feature.read_text(encoding="utf-8").replace(
                    "创建 1024x500 Feature Graphic，仅使用 BRAND-ICON-01 和 SHOT-01，禁止生成或重绘 App UI。",
                    "为 [APP_NAME] 创建 1024x500 Feature Graphic，使用 BRAND-ICON-01 和 SHOT-01，禁止生成或重绘 App UI。",
                ),
                encoding="utf-8",
            )
            errors, _, written = export_prompt_files.process(
                feature,
                screenshots,
                video,
                root / "output",
                strategy_brief=strategy,
                project_root=root,
            )
            self.assertTrue(any("unresolved placeholder" in error for error in errors))
            self.assertEqual([], written)

    def test_rejects_placeholder_variants(self) -> None:
        for placeholder in ("[app name]", "{{APP_NAME}}", "${APP_NAME}", "<APP_NAME>", "TODO"):
            with self.subTest(placeholder=placeholder):
                errors = export_prompt_files.validate_prompt(
                    f"Use SHOT-01 for {placeholder}. Never generate or redraw App UI.",
                    "test prompt",
                )
                self.assertTrue(any("unresolved placeholder" in error for error in errors))

    def test_requires_ready_strategy_before_export(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            strategy, feature, screenshots, video = self.write_prompt_briefs(root)
            strategy.write_text(
                strategy.read_text(encoding="utf-8").replace("- Status: READY", "- Status: DRAFT", 1),
                encoding="utf-8",
            )
            errors, _, written = export_prompt_files.process(
                feature,
                screenshots,
                video,
                root / "output",
                strategy_brief=strategy,
                project_root=root,
            )
            self.assertTrue(any("Status must be READY" in error for error in errors))
            self.assertEqual([], written)

    def test_requires_ready_keyword_tool_input_before_export(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            strategy, feature, screenshots, video = self.write_prompt_briefs(root)
            keyword_file = root / ".ai-work" / "play-assets" / "input" / "keywords" / "tool-en-US.csv"
            keyword_file.unlink()
            errors, _, written = export_prompt_files.process(
                feature,
                screenshots,
                video,
                root / "output",
                strategy_brief=strategy,
                project_root=root,
            )
            self.assertTrue(any("keyword-tool input must be ready" in error for error in errors))
            self.assertEqual([], written)

    def test_prunes_only_stale_generated_screenshot_prompts(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            strategy, _, screenshots, _ = self.write_prompt_briefs(root)
            output = root / "output"
            prompt_dir = output / "screenshots" / "prompts"
            prompt_dir.mkdir(parents=True)
            generated = prompt_dir / "SCREENSHOT_98_PROMPT.md"
            custom = prompt_dir / "SCREENSHOT_99_PROMPT.md"
            generated.write_text(
                f"# stale\n\n{export_prompt_files.GENERATED_MARKER}\n",
                encoding="utf-8",
            )
            custom.write_text("custom prompt\n", encoding="utf-8")
            errors, warnings, _ = export_prompt_files.process(
                None,
                screenshots,
                None,
                output,
                strategy_brief=strategy,
                project_root=root,
                prune_stale=True,
            )
            self.assertEqual([], errors)
            self.assertFalse(generated.exists())
            self.assertTrue(custom.exists())
            self.assertTrue(any("was removed" in warning for warning in warnings))
            self.assertTrue(any("was preserved" in warning for warning in warnings))

    def test_exports_single_branch_without_touching_other_outputs(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            strategy, feature, _, _ = self.write_prompt_briefs(root)
            output = root / "output"
            stale = output / "screenshots" / "prompts" / "SCREENSHOT_99_PROMPT.md"
            stale.parent.mkdir(parents=True)
            stale.write_text("preserve\n", encoding="utf-8")
            errors, warnings, written = export_prompt_files.process(
                feature,
                None,
                None,
                output,
                strategy_brief=strategy,
                project_root=root,
            )
            self.assertEqual([], errors)
            self.assertEqual([], warnings)
            self.assertEqual([output / "feature-graphic" / "FEATURE_GRAPHIC_PROMPT.md"], written)
            self.assertEqual("preserve\n", stale.read_text(encoding="utf-8"))

    def test_exports_concept_prompts_without_screenshots_or_recordings(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            strategy, feature, screenshots, video = self.write_prompt_briefs(root)
            strategy_content = strategy.read_text(encoding="utf-8")
            strategy_content = strategy_content.replace("- Status: READY", "- Status: CONCEPT_READY", 1)
            strategy_content = strategy_content.replace("- Asset Mode: PRODUCTION", "- Asset Mode: CONCEPT")
            strategy_content = "\n".join(
                line for line in strategy_content.splitlines()
                if "| SHOT-01 |" not in line and "| CLIP-01 |" not in line
            ) + "\n"
            strategy.write_text(strategy_content, encoding="utf-8")
            feature.write_text(
                feature.read_text(encoding="utf-8")
                .replace("READY_FOR_PRODUCTION", "READY_FOR_CONCEPT")
                .replace("Real UI Asset ID: SHOT-01", "Real UI Asset ID: N/A")
                .replace("BRAND-ICON-01 和 SHOT-01", "BRAND-ICON-01")
                .replace("仅使用 BRAND-ICON-01", "仅使用 BRAND-ICON-01，不使用设备框或 App UI"),
                encoding="utf-8",
            )
            screenshots.write_text(
                screenshots.read_text(encoding="utf-8")
                .replace("READY_FOR_PRODUCTION", "READY_FOR_CONCEPT")
                .replace("Source Screenshot ID: SHOT-01", "Source Screenshot ID: N/A")
                .replace("使用 SHOT-01", "使用 BRAND-ICON-01 创建无设备框功能营销画面，不展示 App UI"),
                encoding="utf-8",
            )
            video.write_text(
                video.read_text(encoding="utf-8")
                .replace("READY_FOR_PRODUCTION", "READY_FOR_CONCEPT")
                .replace("Recording Clip ID: CLIP-01", "Recording Clip ID: N/A")
                .replace("只合成真实录屏 CLIP-01", "使用 BRAND-ICON-01 制作功能动效，不展示 App UI 或虚构操作"),
                encoding="utf-8",
            )
            (root / "shot.png").unlink()
            (root / "clip.mp4").unlink()

            errors, warnings, written = export_prompt_files.process(
                feature,
                screenshots,
                video,
                root / "output",
                strategy_brief=strategy,
                project_root=root,
            )

            self.assertEqual([], errors)
            self.assertEqual([], warnings)
            self.assertEqual(3, len(written))


if __name__ == "__main__":
    unittest.main()
