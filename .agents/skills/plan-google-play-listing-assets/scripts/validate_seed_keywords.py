#!/usr/bin/env python3
"""Validate candidate Seed Keyword CSV files."""

from __future__ import annotations

import argparse
import csv
import re
from collections import Counter
from pathlib import Path


REQUIRED_COLUMNS = [
    "seed_keyword",
    "locale",
    "category",
    "search_intent",
    "product_evidence",
    "rationale",
    "status",
]
FORBIDDEN_METRIC_MARKERS = {
    "search_volume", "volume", "competition", "trend", "keyword_difficulty", "difficulty", "cpc", "priority"
}
LOCALE_RE = re.compile(r"^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$")
EXPECTED_LOCALE = "en-US"
EXPECTED_SEED_COUNT = 10
ENGLISH_ASCII_RE = re.compile(r"^[\x20-\x7E]+$")
ALLOWED_CATEGORIES = {
    "product-category",
    "core-task",
    "feature",
    "differentiator",
    "problem",
    "scenario",
}
GENERIC_INTENTS = {
    "commercial",
    "discovery",
    "informational",
    "navigational",
    "transactional",
}
STOP_TOKENS = {"a", "an", "and", "for", "of", "the", "to", "with"}
HIGH_SIMILARITY_THRESHOLD = 0.75


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate candidate Seed Keyword CSV.")
    parser.add_argument("--csv", required=True, help="Seed Keyword CSV path.")
    parser.add_argument("--markdown", required=True, help="User-facing Seed Keyword Markdown path.")
    parser.add_argument(
        "--strict-quality",
        action="store_true",
        help="Fail when semantic-quality heuristics emit warnings.",
    )
    return parser.parse_args()


def keyword_tokens(value: str) -> set[str]:
    return {
        token
        for token in re.findall(r"[a-z0-9]+", value.casefold())
        if token not in STOP_TOKENS
    }


def token_similarity(left: set[str], right: set[str]) -> float:
    if not left or not right:
        return 0.0
    return len(left & right) / len(left | right)


def validate(path: Path) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []
    if not path.is_file():
        return [f"file does not exist: {path}"], warnings

    try:
        with path.open("r", encoding="utf-8-sig", newline="") as handle:
            reader = csv.DictReader(handle)
            columns = reader.fieldnames or []
            normalized = [column.strip() for column in columns]
            missing = [column for column in REQUIRED_COLUMNS if column not in normalized]
            if missing:
                errors.append(f"missing required columns: {', '.join(missing)}")
            forbidden = sorted(
                column for column in normalized if column.lower().replace(" ", "_") in FORBIDDEN_METRIC_MARKERS
            )
            if forbidden:
                errors.append(f"seed file must not contain research metric columns: {', '.join(forbidden)}")

            seen: set[tuple[str, str]] = set()
            seed_rows: list[dict[str, object]] = []
            category_counts: Counter[str] = Counter()
            row_count = 0
            for line_number, row in enumerate(reader, start=2):
                row_count += 1
                values = {str(key).strip(): (value or "").strip() for key, value in row.items() if key is not None}
                for column in REQUIRED_COLUMNS:
                    value = values.get(column, "")
                    if not value:
                        errors.append(f"line {line_number}: {column} is empty")
                    elif value.startswith("[") and value.endswith("]"):
                        errors.append(f"line {line_number}: {column} still contains a template placeholder")
                if values.get("status") and values["status"] != "CANDIDATE_SEED":
                    errors.append(f"line {line_number}: status must be CANDIDATE_SEED")
                locale = values.get("locale", "")
                if locale and not LOCALE_RE.fullmatch(locale):
                    errors.append(f"line {line_number}: invalid BCP-47-like locale: {locale}")
                elif locale and locale != EXPECTED_LOCALE:
                    errors.append(f"line {line_number}: locale must be {EXPECTED_LOCALE}")
                keyword = values.get("seed_keyword", "")
                if keyword and (not ENGLISH_ASCII_RE.fullmatch(keyword) or not re.search(r"[A-Za-z]", keyword)):
                    errors.append(f"line {line_number}: seed_keyword must be an English ASCII phrase")
                category = values.get("category", "")
                if category and category not in ALLOWED_CATEGORIES:
                    errors.append(
                        f"line {line_number}: category must be one of: {', '.join(sorted(ALLOWED_CATEGORIES))}"
                    )
                elif category:
                    category_counts[category] += 1
                search_intent = values.get("search_intent", "")
                if search_intent.casefold() in GENERIC_INTENTS:
                    errors.append(
                        f"line {line_number}: search_intent must describe the user's query goal, not only '{search_intent}'"
                    )
                elif search_intent and len(re.findall(r"[A-Za-z0-9]+", search_intent)) < 2:
                    warnings.append(f"line {line_number}: search_intent is too short to explain the query goal")
                rationale = values.get("rationale", "")
                if rationale and len(re.findall(r"[A-Za-z0-9]+", rationale)) < 4:
                    warnings.append(f"line {line_number}: rationale is too short for a semantic quality review")
                identity = (values.get("seed_keyword", "").casefold(), locale.casefold())
                if all(identity):
                    if identity in seen:
                        errors.append(f"line {line_number}: duplicate seed keyword for locale: {identity[0]}")
                    seen.add(identity)
                tokens = keyword_tokens(keyword)
                if len(tokens) > 6:
                    warnings.append(
                        f"line {line_number}: seed keyword has more than 6 meaningful words and may be a feature statement"
                    )
                if keyword:
                    seed_rows.append({"line": line_number, "keyword": keyword, "tokens": tokens})
            if row_count == 0:
                errors.append("seed file has no data rows")
            elif row_count != EXPECTED_SEED_COUNT:
                errors.append(f"seed file must contain exactly {EXPECTED_SEED_COUNT} data rows; found {row_count}")
            if row_count and not category_counts["product-category"]:
                errors.append("seed file must contain at least one product-category anchor")
            if row_count and not (category_counts["core-task"] or category_counts["problem"]):
                warnings.append("seed set should cover at least one core-task or problem search intent")
            if row_count and len(category_counts) < 3:
                warnings.append("seed set uses fewer than 3 intent categories and may lack search-intent diversity")
            dominant = category_counts.most_common(1)
            if dominant and dominant[0][1] > 7:
                warnings.append(
                    f"seed set is concentrated in category '{dominant[0][0]}' ({dominant[0][1]} of {row_count})"
                )
            for index, left in enumerate(seed_rows):
                for right in seed_rows[index + 1:]:
                    left_tokens = left["tokens"]
                    right_tokens = right["tokens"]
                    if not isinstance(left_tokens, set) or not isinstance(right_tokens, set):
                        continue
                    if left_tokens and left_tokens == right_tokens:
                        errors.append(
                            f"lines {left['line']} and {right['line']}: seed keywords are token-equivalent: "
                            f"'{left['keyword']}' / '{right['keyword']}'"
                        )
                    elif token_similarity(left_tokens, right_tokens) >= HIGH_SIMILARITY_THRESHOLD:
                        warnings.append(
                            f"lines {left['line']} and {right['line']}: seed keywords have high lexical overlap: "
                            f"'{left['keyword']}' / '{right['keyword']}'"
                        )
    except (OSError, UnicodeError, csv.Error) as error:
        errors.append(f"unable to read CSV: {error}")
    return errors, warnings


def read_keywords(path: Path) -> list[str]:
    try:
        with path.open("r", encoding="utf-8-sig", newline="") as handle:
            return [
                (row.get("seed_keyword") or "").strip()
                for row in csv.DictReader(handle)
            ]
    except (OSError, UnicodeError, csv.Error):
        return []


def validate_markdown(path: Path, expected_keywords: list[str]) -> list[str]:
    if not path.is_file():
        return [f"markdown file does not exist: {path}"]
    try:
        actual = path.read_text(encoding="utf-8-sig")
    except (OSError, UnicodeError) as error:
        return [f"unable to read Markdown: {error}"]

    expected = "# Seed Keywords\n\n```text\n" + "\n".join(expected_keywords) + "\n```\n"
    if actual != expected:
        return [
            "seed Markdown must contain only '# Seed Keywords' and one text code block "
            "whose 10 keyword lines exactly match CSV order"
        ]
    return []


def main() -> int:
    args = parse_args()
    csv_path = Path(args.csv)
    errors, warnings = validate(csv_path)
    errors.extend(validate_markdown(Path(args.markdown), read_keywords(csv_path)))
    for warning in warnings:
        print(f"WARN: {warning}")
    for error in errors:
        print(f"ERROR: {error}")
    strict_quality_failed = args.strict_quality and bool(warnings)
    if strict_quality_failed:
        print("ERROR: strict semantic quality gate rejected the warnings above")
    if errors or strict_quality_failed:
        print(f"seed keyword validation failed: {len(errors)} error(s), {len(warnings)} warning(s)")
        return 1
    print(f"seed keyword validation passed: {len(warnings)} warning(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
