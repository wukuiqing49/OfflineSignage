#!/usr/bin/env python3
"""Normalize keyword-tool exports and connect rows to project-derived Seed Keywords."""

from __future__ import annotations

import argparse
import csv
import json
import re
from pathlib import Path
from typing import Any

import inspect_keyword_inputs
import init_workspace


OUTPUT_COLUMNS = [
    "keyword",
    "locale",
    "tool_name",
    "target_market",
    "exported_at",
    "source_file",
    "source_sheet",
    "source_row",
    "matched_seed",
    "product_evidence",
    "relevance_score",
    "intent_risk",
    "recommendation",
    "semantic_status",
    "semantic_reason",
    "average_monthly_searches",
    "competition",
    "competition_index",
    "three_month_change",
    "year_over_year_change",
    "raw_metrics",
]
REVIEW_SENSITIVE_TERMS = {
    "online", "web", "website", "cloud", "team", "teams", "collaboration", "ai", "free",
}
STOP_TOKENS = {"a", "an", "and", "for", "of", "the", "to", "with"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Normalize and score keyword research rows.")
    parser.add_argument("--project-root", default=".", help="Android project root.")
    parser.add_argument("--input-dir", help="Override keyword-tool input directory.")
    parser.add_argument("--seed-csv", help="Override keyword-research-input.csv.")
    parser.add_argument("--output", help="Override keyword-research-analysis.csv.")
    parser.add_argument(
        "--allow-unconfirmed-market",
        action="store_true",
        help="Allow CONCEPT analysis when source market metadata is incomplete.",
    )
    return parser.parse_args()


def stem_token(token: str) -> str:
    for suffix, replacement, minimum_length in (
        ("ies", "y", 5),
        ("ing", "", 6),
        ("ions", "", 7),
        ("ion", "", 6),
        ("ers", "", 6),
        ("or", "", 5),
        ("er", "", 5),
        ("ed", "", 5),
        ("s", "", 4),
    ):
        if suffix == "s" and token.endswith("ss"):
            continue
        if len(token) >= minimum_length and token.endswith(suffix):
            return token[:-len(suffix)] + replacement
    return token


def tokens(value: str) -> set[str]:
    raw = re.findall(r"[a-z0-9]+", value.casefold())
    return {
        stem_token(token)
        for token in raw
        if token not in STOP_TOKENS
    }


def project_terms(seeds: list[dict[str, str]]) -> set[str]:
    return tokens(" ".join(
        value
        for seed in seeds
        for key, value in seed.items()
        if key != "product_evidence"
    ))


def relevance(keyword: str, seeds: list[dict[str, str]]) -> tuple[str, str, int]:
    keyword_normalized = " ".join(keyword.casefold().split())
    keyword_tokens = tokens(keyword)
    best_seed = ""
    best_evidence = ""
    best_score = 0
    for seed in seeds:
        seed_keyword = seed["seed_keyword"]
        if keyword_normalized == " ".join(seed_keyword.casefold().split()):
            score = 100
        else:
            seed_tokens = tokens(seed_keyword)
            overlap = len(keyword_tokens & seed_tokens)
            if not overlap:
                score = 0
            else:
                seed_coverage = overlap / max(1, len(seed_tokens))
                keyword_coverage = overlap / max(1, len(keyword_tokens))
                score = round(max(seed_coverage, keyword_coverage) * 100)
        if score > best_score:
            best_seed = seed_keyword
            best_evidence = seed["product_evidence"]
            best_score = score
    return best_seed, best_evidence, best_score


def choose_column(columns: list[str], preferred: tuple[str, ...], markers: tuple[str, ...] = ()) -> str:
    normalized = {column: inspect_keyword_inputs.normalize_column(column) for column in columns}
    for expected in preferred:
        for column, value in normalized.items():
            if value == expected:
                return column
    for marker in markers:
        for column, value in normalized.items():
            if marker in value:
                return column
    return ""


def numeric_value(value: str) -> float:
    cleaned = value.replace(",", "").replace("%", "").strip()
    match = re.search(r"-?\d+(?:\.\d+)?", cleaned)
    return float(match.group(0)) if match else -1.0


def read_seeds(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    seeds = [
        {
            "seed_keyword": (row.get("seed_keyword") or "").strip(),
            "product_evidence": (row.get("product_evidence") or "").strip(),
            "category": (row.get("category") or "").strip(),
            "search_intent": (row.get("search_intent") or "").strip(),
            "rationale": (row.get("rationale") or "").strip(),
        }
        for row in rows
        if (row.get("seed_keyword") or "").strip()
    ]
    if not seeds:
        raise ValueError(f"seed CSV has no keywords: {path}")
    return seeds


def normalized_rows(
    path: Path,
    metadata: dict[str, Any],
    seeds: list[dict[str, str]],
) -> list[dict[str, str]]:
    details = (
        inspect_keyword_inputs.read_csv(path, include_rows=True)
        if path.suffix.lower() == ".csv"
        else inspect_keyword_inputs.read_xlsx(path, include_rows=True)
    )
    datasets = details.get("sheets", []) if details["format"] == "xlsx" else [details]
    output: list[dict[str, str]] = []
    verified_project_terms = project_terms(seeds)
    sensitive_terms = tokens(" ".join(REVIEW_SENSITIVE_TERMS))
    for dataset in datasets:
        if not dataset.get("research_ready"):
            continue
        columns = dataset["columns"]
        keyword_column = dataset["keyword_columns"][0]
        volume_column = choose_column(
            columns,
            ("avgmonthlysearches", "averagemonthlysearches", "searchvolume", "volume", "搜索量"),
            ("monthlysearches",),
        )
        competition_index_column = choose_column(
            columns,
            ("competitionindexedvalue", "竞争指数", "競爭指數"),
        )
        competition_column = choose_column(columns, ("competition", "竞争", "競爭"))
        three_month_column = choose_column(columns, ("threemonthchange", "三个月变化", "三個月變化"))
        yoy_column = choose_column(columns, ("yearoveryearchange", "年同比变化", "年同比變化"))
        metric_columns = [
            column for column in dataset["research_columns"]
            if column not in {keyword_column}
        ]
        for offset, row in enumerate(dataset.get("_rows", [])):
            keyword = (row.get(keyword_column) or "").strip()
            if not keyword:
                continue
            matched_seed, evidence, score = relevance(keyword, seeds)
            risk = ";".join(sorted(
                (tokens(keyword) & sensitive_terms) - verified_project_terms
            ))
            recommendation = (
                "REVIEW_INTENT_RISK"
                if risk and score > 0
                else "REVIEW_RELEVANT"
                if score >= 50
                else "LOW_RELEVANCE"
            )
            raw_metrics = {
                column: row.get(column, "")
                for column in metric_columns
                if row.get(column, "") not in (None, "")
            }
            output.append({
                "keyword": keyword,
                "locale": str(metadata["locale"]),
                "tool_name": str(metadata["tool_name"]),
                "target_market": str(metadata["target_market"]),
                "exported_at": str(metadata["exported_at"]),
                "source_file": path.name,
                "source_sheet": str(dataset.get("name", "")),
                "source_row": row.get(
                    "__source_row__",
                    str(int(dataset.get("header_row", 1)) + 1 + offset),
                ),
                "matched_seed": matched_seed,
                "product_evidence": evidence,
                "relevance_score": str(score),
                "intent_risk": risk,
                "recommendation": recommendation,
                "semantic_status": "PENDING_REVIEW",
                "semantic_reason": "",
                "average_monthly_searches": row.get(volume_column, "") if volume_column else "",
                "competition": row.get(competition_column, "") if competition_column else "",
                "competition_index": row.get(competition_index_column, "") if competition_index_column else "",
                "three_month_change": row.get(three_month_column, "") if three_month_column else "",
                "year_over_year_change": row.get(yoy_column, "") if yoy_column else "",
                "raw_metrics": json.dumps(raw_metrics, ensure_ascii=False, sort_keys=True),
            })
    return output


def analyze(
    input_dir: Path,
    seed_csv: Path,
    output: Path,
    allow_unconfirmed_market: bool = False,
) -> tuple[int, int]:
    report = inspect_keyword_inputs.inspect_directory(
        input_dir,
        require_metadata=not allow_unconfirmed_market,
    )
    ready = (
        report.get("data_status") == "ready"
        if allow_unconfirmed_market
        else report.get("status") == "ready"
    )
    if not ready:
        issues = report.get("metadata", {}).get("issues", [])
        detail = "; ".join(issues) if issues else report.get("status", "unknown")
        raise ValueError(f"keyword research input is not ready: {detail}")
    metadata = dict(report.get("metadata", {}).get("values", {}))
    for field in ("tool_name", "target_market", "locale", "exported_at"):
        value = str(metadata.get(field, "")).strip()
        if not value or value.startswith("["):
            metadata[field] = "UNCONFIRMED"
    seeds = read_seeds(seed_csv)
    rows: list[dict[str, str]] = []
    for item in report["files"]:
        if item.get("research_ready"):
            rows.extend(normalized_rows(Path(item["path"]), metadata, seeds))

    deduplicated: dict[str, dict[str, str]] = {}
    for row in rows:
        identity = " ".join(row["keyword"].casefold().split())
        previous = deduplicated.get(identity)
        if previous is None or numeric_value(row["average_monthly_searches"]) > numeric_value(
            previous["average_monthly_searches"]
        ):
            deduplicated[identity] = row
    ranked = sorted(
        deduplicated.values(),
        key=lambda row: (
            row["recommendation"] != "REVIEW_RELEVANT",
            -int(row["relevance_score"]),
            -numeric_value(row["average_monthly_searches"]),
            row["keyword"].casefold(),
        ),
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_COLUMNS)
        writer.writeheader()
        writer.writerows(ranked)
    review_count = sum(row["recommendation"] == "REVIEW_RELEVANT" for row in ranked)
    return len(ranked), review_count


def main() -> int:
    args = parse_args()
    root = Path(args.project_root).resolve()
    input_dir = Path(args.input_dir).resolve() if args.input_dir else root / ".ai-work/play-assets/input/keywords"
    seed_csv = Path(args.seed_csv).resolve() if args.seed_csv else root / ".ai-work/play-assets/output/strategy/keyword-research-input.csv"
    output = Path(args.output).resolve() if args.output else root / ".ai-work/play-assets/output/strategy/keyword-research-analysis.csv"
    context_errors = init_workspace.validate_project_context(root)
    if context_errors:
        for error in context_errors:
            print(f"ERROR: {error}")
        return 1
    try:
        row_count, review_count = analyze(
            input_dir,
            seed_csv,
            output,
            allow_unconfirmed_market=args.allow_unconfirmed_market,
        )
    except (OSError, UnicodeError, csv.Error, ValueError) as error:
        print(f"ERROR: {error}")
        return 1
    print(f"ANALYSIS: {output}")
    print(f"ROWS: {row_count}")
    print(f"REVIEW_RELEVANT: {review_count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
