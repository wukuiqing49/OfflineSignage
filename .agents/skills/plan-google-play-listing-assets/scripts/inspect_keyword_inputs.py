#!/usr/bin/env python3
"""Inspect keyword-tool CSV/XLSX exports without modifying source files."""

from __future__ import annotations

import argparse
import csv
import json
import re
import zipfile
from datetime import date
from pathlib import Path
from typing import Any
from xml.etree import ElementTree


ENCODINGS = ("utf-8-sig", "utf-16", "cp1252")
SUPPORTED_SUFFIXES = {".csv", ".xlsx"}
SHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
DOC_REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
PACKAGE_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
KEYWORD_COLUMN_ALIASES = {
    "keyword", "keywords", "keywordtext", "searchterm", "searchterms", "query", "queries",
    "关键词", "關鍵詞", "搜索词", "搜尋詞",
}
RESEARCH_COLUMN_MARKERS = {
    "volume", "searches", "traffic", "competition", "difficulty", "trend", "cpc", "bid",
    "install", "result", "搜索量", "流量", "竞争", "競爭", "难度", "難度", "趋势", "趨勢",
    "点击", "點擊", "安装", "安裝",
}
METRIC_FAMILIES = (
    {"volume", "searches", "traffic", "搜索量", "流量"},
    {"competition", "difficulty", "竞争", "競爭", "难度", "難度"},
    {"trend", "change", "趋势", "趨勢"},
    {"cpc", "bid", "点击", "點擊"},
    {"install", "安装", "安裝"},
)
MAX_HEADER_SCAN_ROWS = 50
METADATA_FILE_NAME = "keyword-research-metadata.json"
REQUIRED_METADATA_FIELDS = ("tool_name", "target_market", "locale", "exported_at", "metric_definitions")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Inspect keyword research exports.")
    parser.add_argument("--project-root", default=".", help="Android project root.")
    parser.add_argument("--input-dir", help="Override the keyword input directory.")
    parser.add_argument("--output", help="Optional JSON report path.")
    parser.add_argument("--require-data", action="store_true", help="Fail when no valid export exists.")
    parser.add_argument(
        "--require-metadata",
        action="store_true",
        help="Require keyword source metadata before reporting ready.",
    )
    return parser.parse_args()


def normalize_column(value: str) -> str:
    return re.sub(r"[\s_.()\[\]{}:/\\-]+", "", value).casefold()


def metrics_are_related(left: str, right: str) -> bool:
    if left == right or left in right or right in left:
        return True
    return any(
        any(marker in left for marker in family)
        and any(marker in right for marker in family)
        for family in METRIC_FAMILIES
    )


def research_readiness(columns: list[str], row_count: int) -> dict[str, Any]:
    normalized = {column: normalize_column(column) for column in columns if column.strip()}
    keyword_columns = [
        column for column, value in normalized.items()
        if value in KEYWORD_COLUMN_ALIASES
    ]
    research_columns = [
        column for column, value in normalized.items()
        if any(marker in value for marker in RESEARCH_COLUMN_MARKERS)
    ]
    issues: list[str] = []
    if row_count <= 0:
        issues.append("no non-empty data rows")
    if not keyword_columns:
        issues.append("no recognized keyword column")
    if not research_columns:
        issues.append("no recognized research metric column")
    return {
        "keyword_columns": keyword_columns,
        "research_columns": research_columns,
        "research_ready": not issues,
        "readiness_issues": issues,
    }


def find_header_row(rows: list[list[str]]) -> tuple[int, list[str]]:
    for index, row in enumerate(rows[:MAX_HEADER_SCAN_ROWS]):
        columns = [column.strip() for column in row]
        readiness = research_readiness(columns, row_count=1)
        if readiness["keyword_columns"] and readiness["research_columns"]:
            return index, columns
    columns = [column.strip() for column in rows[0]] if rows else []
    return 0, columns


def detect_delimiter(sample: str) -> str:
    try:
        return csv.Sniffer().sniff(sample, delimiters=",;\t").delimiter
    except csv.Error:
        delimiter = max((",", ";", "\t"), key=sample.count)
        if sample.count(delimiter) == 0:
            raise ValueError("unable to detect CSV delimiter")
        return delimiter


def rows_as_dicts(
    columns: list[str],
    rows: list[list[str]],
    row_numbers: list[int] | None = None,
) -> list[dict[str, str]]:
    output: list[dict[str, str]] = []
    for offset, row in enumerate(rows):
        if not any(cell.strip() for cell in row):
            continue
        values = {
            column: (row[index].strip() if index < len(row) else "")
            for index, column in enumerate(columns)
            if column
        }
        if row_numbers is not None and offset < len(row_numbers):
            values["__source_row__"] = str(row_numbers[offset])
        output.append(values)
    return output


def load_metadata(input_dir: Path) -> dict[str, Any]:
    path = input_dir / METADATA_FILE_NAME
    issues: list[str] = []
    values: dict[str, Any] = {}
    if not path.is_file():
        return {
            "path": str(path.resolve()),
            "status": "missing",
            "issues": [f"missing {METADATA_FILE_NAME}"],
        }
    try:
        loaded = json.loads(path.read_text(encoding="utf-8-sig"))
        if not isinstance(loaded, dict):
            raise ValueError("metadata root must be an object")
        values = loaded
    except (OSError, UnicodeError, json.JSONDecodeError, ValueError) as error:
        return {"path": str(path.resolve()), "status": "invalid", "issues": [str(error)]}

    unresolved_fields: set[str] = set()
    for field in REQUIRED_METADATA_FIELDS:
        value = values.get(field)
        if value in (None, "", [], {}) or (isinstance(value, str) and value.strip().startswith("[")):
            issues.append(f"metadata field is missing or unresolved: {field}")
            unresolved_fields.add(field)
    locale = str(values.get("locale", ""))
    if "locale" not in unresolved_fields and locale and not re.fullmatch(
        r"[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*",
        locale,
    ):
        issues.append(f"invalid metadata locale: {locale}")
    exported_at = str(values.get("exported_at", ""))
    if "exported_at" not in unresolved_fields and exported_at:
        try:
            if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", exported_at):
                raise ValueError
            date.fromisoformat(exported_at)
        except ValueError:
            issues.append("metadata exported_at must be a valid YYYY-MM-DD date")
    metric_definitions = values.get("metric_definitions")
    if metric_definitions not in (None, "", [], {}) and not isinstance(metric_definitions, dict):
        issues.append("metadata metric_definitions must be an object")
    elif isinstance(metric_definitions, dict):
        for metric, definition in metric_definitions.items():
            if not str(metric).strip() or not str(definition).strip():
                issues.append("metadata metric_definitions keys and values must be non-empty")
                break
            if str(metric).strip().startswith("[") or str(definition).strip().startswith("["):
                issues.append("metadata metric_definitions contains an unresolved placeholder")
                break
    return {
        "path": str(path.resolve()),
        "status": "ready" if not issues else "incomplete",
        "issues": issues,
        "values": values,
    }


def read_csv(path: Path, include_rows: bool = False) -> dict[str, Any]:
    last_error: Exception | None = None
    for encoding in ENCODINGS:
        try:
            with path.open("r", encoding=encoding, newline="") as handle:
                sample = handle.read(8192)
                handle.seek(0)
                delimiter = detect_delimiter(sample)
                rows = list(csv.reader(handle, delimiter=delimiter))
            header_index, columns = find_header_row(rows)
            row_count = sum(
                1 for row in rows[header_index + 1:]
                if any(cell.strip() for cell in row)
            )
            if not any(columns):
                raise ValueError("CSV header is empty")
            result = {
                "status": "valid",
                "format": "csv",
                "encoding": encoding,
                "delimiter": delimiter,
                "columns": columns,
                "header_row": header_index + 1,
                "preamble_row_count": header_index,
                "row_count": row_count,
            }
            result.update(research_readiness(columns, row_count))
            if include_rows:
                data_rows = rows[header_index + 1:]
                row_numbers = list(range(header_index + 2, len(rows) + 1))
                result["_rows"] = rows_as_dicts(columns, data_rows, row_numbers)
            return result
        except (UnicodeError, csv.Error, OSError, ValueError) as error:
            last_error = error
    raise ValueError(f"unable to parse CSV: {last_error}")


def read_xlsx(path: Path, include_rows: bool = False) -> dict[str, Any]:
    def xml(root: zipfile.ZipFile, name: str) -> ElementTree.Element:
        try:
            return ElementTree.fromstring(root.read(name))
        except KeyError as error:
            raise ValueError(f"XLSX is missing {name}") from error

    def shared_strings(root: zipfile.ZipFile) -> list[str]:
        if "xl/sharedStrings.xml" not in root.namelist():
            return []
        tree = xml(root, "xl/sharedStrings.xml")
        return ["".join(node.text or "" for node in item.iter(f"{{{SHEET_NS}}}t")) for item in tree]

    def cell_value(cell: ElementTree.Element, strings: list[str]) -> str:
        cell_type = cell.attrib.get("t", "")
        if cell_type == "inlineStr":
            return "".join(node.text or "" for node in cell.iter(f"{{{SHEET_NS}}}t")).strip()
        value_node = cell.find(f"{{{SHEET_NS}}}v")
        raw = "" if value_node is None or value_node.text is None else value_node.text
        if cell_type == "s" and raw.isdigit():
            index = int(raw)
            return strings[index].strip() if index < len(strings) else ""
        return raw.strip()

    def column_index(reference: str) -> int:
        match = re.match(r"([A-Z]+)", reference.upper())
        if not match:
            return 0
        index = 0
        for char in match.group(1):
            index = index * 26 + ord(char) - ord("A") + 1
        return index - 1

    try:
        with zipfile.ZipFile(path) as archive:
            workbook = xml(archive, "xl/workbook.xml")
            relationships = xml(archive, "xl/_rels/workbook.xml.rels")
            targets = {
                relation.attrib["Id"]: relation.attrib["Target"]
                for relation in relationships.findall(f"{{{PACKAGE_REL_NS}}}Relationship")
            }
            strings = shared_strings(archive)
            sheets: list[dict[str, Any]] = []
            for sheet in workbook.findall(f".//{{{SHEET_NS}}}sheet"):
                relation_id = sheet.attrib.get(f"{{{DOC_REL_NS}}}id", "")
                target = targets.get(relation_id, "")
                if not target:
                    raise ValueError(f"XLSX sheet relationship is missing: {sheet.attrib.get('name', '')}")
                sheet_path = target.lstrip("/")
                if not sheet_path.startswith("xl/"):
                    sheet_path = f"xl/{sheet_path}"
                worksheet = xml(archive, sheet_path)
                rows = worksheet.findall(f".//{{{SHEET_NS}}}row")
                matrix: list[list[str]] = []
                matrix_row_numbers: list[int] = []
                for row in rows:
                    values: dict[int, str] = {}
                    for cell in row.findall(f"{{{SHEET_NS}}}c"):
                        values[column_index(cell.attrib.get("r", "A1"))] = cell_value(cell, strings)
                    width = max(values, default=-1) + 1
                    matrix.append([values.get(index, "") for index in range(width)])
                    matrix_row_numbers.append(int(row.attrib.get("r", len(matrix))))
                header_index, columns = find_header_row(matrix)
                data_rows = matrix[header_index + 1:]
                row_count = sum(1 for row in data_rows if any(cell.strip() for cell in row))
                sheet_result = {
                    "name": sheet.attrib.get("name", ""),
                    "columns": columns,
                    "header_row": header_index + 1,
                    "preamble_row_count": header_index,
                    "row_count": row_count,
                }
                sheet_result.update(research_readiness(columns, row_count))
                if include_rows:
                    sheet_result["_rows"] = rows_as_dicts(
                        columns,
                        data_rows,
                        matrix_row_numbers[header_index + 1:],
                    )
                sheets.append(sheet_result)
    except (OSError, zipfile.BadZipFile, ElementTree.ParseError, KeyError, IndexError) as error:
        raise ValueError(f"unable to parse XLSX: {error}") from error
    if not sheets:
        raise ValueError("XLSX has no worksheets")
    return {
        "status": "valid",
        "format": "xlsx",
        "sheets": sheets,
        "research_ready": any(sheet["research_ready"] for sheet in sheets),
    }


def inspect_directory(input_dir: Path, require_metadata: bool = False) -> dict[str, Any]:
    report: dict[str, Any] = {
        "input_dir": str(input_dir.resolve()),
        "files": [],
        "metadata": load_metadata(input_dir),
    }
    if not input_dir.is_dir():
        report["status"] = "missing_directory"
        return report

    candidates = sorted(
        path for path in input_dir.iterdir()
        if path.is_file() and path.suffix.lower() in SUPPORTED_SUFFIXES
    )
    for path in candidates:
        item: dict[str, Any] = {"path": str(path.resolve()), "name": path.name}
        try:
            details = read_csv(path) if path.suffix.lower() == ".csv" else read_xlsx(path)
            item.update(details)
        except (OSError, ValueError) as error:
            item.update({"status": "invalid", "error": str(error)})
        report["files"].append(item)

    valid_count = sum(item["status"] == "valid" for item in report["files"])
    usable_count = sum(
        item["status"] == "valid" and item.get("research_ready", False)
        for item in report["files"]
    )
    if usable_count and report["metadata"].get("status") == "ready":
        research_columns: set[str] = set()
        for item in report["files"]:
            datasets = item.get("sheets", []) if item.get("format") == "xlsx" else [item]
            for dataset in datasets:
                if dataset.get("research_ready"):
                    research_columns.update(
                        normalize_column(column) for column in dataset.get("research_columns", [])
                    )
        definitions = report["metadata"].get("values", {}).get("metric_definitions", {})
        defined_metrics = {normalize_column(str(metric)) for metric in definitions}
        if not any(
            metrics_are_related(defined, column)
            for defined in defined_metrics
            for column in research_columns
        ):
            report["metadata"]["status"] = "incomplete"
            report["metadata"]["issues"].append(
                "metadata metric_definitions does not describe any detected research metric column"
            )
    data_status = "ready" if usable_count else ("no_usable_research_data" if valid_count else "no_valid_data")
    status = data_status
    if require_metadata and data_status == "ready" and report["metadata"]["status"] != "ready":
        status = "needs_metadata"
    report.update({
        "status": status,
        "data_status": data_status,
        "valid_file_count": valid_count,
        "usable_file_count": usable_count,
        "file_count": len(report["files"]),
    })
    return report


def main() -> int:
    args = parse_args()
    root = Path(args.project_root).resolve()
    input_dir = Path(args.input_dir).resolve() if args.input_dir else root / ".ai-work/play-assets/input/keywords"
    report = inspect_directory(input_dir, require_metadata=args.require_metadata)
    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        output = Path(args.output)
        if not output.is_absolute():
            output = root / output
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(rendered + "\n", encoding="utf-8")
        print(f"REPORT: {output}")
    else:
        print(rendered)
    if args.require_data and report.get("status") != "ready":
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
