"""Application configuration loading and selection."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any


class AppConfigError(ValueError):
    """Raised when the local app map is missing or invalid."""


PACKAGE_PATTERN = re.compile(r"^[A-Za-z_][A-Za-z_0-9]*(?:\.[A-Za-z_][A-Za-z_0-9]*)+$")


def is_package_name(value: str) -> bool:
    return bool(PACKAGE_PATTERN.fullmatch(value))


def load_app_config(path: Path) -> tuple[dict[str, str], dict[str, str]]:
    if not path.is_file():
        raise AppConfigError(
            f"App config not found: {path}. Copy config/apps.example.json to config/apps.json and fill verified package names."
        )
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise AppConfigError(f"Could not read app config ({type(exc).__name__}).") from exc

    apps = payload.get("apps") if isinstance(payload, dict) else None
    if not isinstance(apps, dict) or not apps:
        raise AppConfigError("App config must contain a non-empty 'apps' object.")

    cleaned: dict[str, str] = {}
    locales: dict[str, str] = {}
    packages: set[str] = set()
    for name, value in apps.items():
        if not isinstance(name, str) or not name.strip():
            raise AppConfigError("App names must be non-empty strings.")
        package: Any = value
        default_locale = None
        if isinstance(value, dict):
            package = value.get("packageName")
            default_locale = value.get("defaultLocale")
        if not isinstance(package, str) or not package.strip():
            raise AppConfigError(f"App '{name}' has an empty package name.")
        package = package.strip()
        if package.startswith("REPLACE_WITH_"):
            raise AppConfigError(f"App '{name}' still has a placeholder package name.")
        if package in packages:
            raise AppConfigError(f"Package name '{package}' is configured more than once.")
        packages.add(package)
        cleaned[name.strip()] = package
        if default_locale is not None:
            if not isinstance(default_locale, str) or not default_locale.strip():
                raise AppConfigError(f"App '{name}' has an invalid defaultLocale.")
            locales[package] = default_locale.strip()
    return cleaned, locales


def load_apps(path: Path) -> dict[str, str]:
    return load_app_config(path)[0]


def load_reports_bucket(path: Path) -> str | None:
    if not path.is_file():
        return None
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise AppConfigError(f"Could not read app config ({type(exc).__name__}).") from exc
    value = payload.get("reportsBucket") if isinstance(payload, dict) else None
    if value is None:
        return None
    if not isinstance(value, str) or not value.strip():
        raise AppConfigError("reportsBucket must be a non-empty string when provided.")
    return value.strip()


def resolve_app(apps: dict[str, str], selector: str) -> tuple[str, str]:
    for name, package in apps.items():
        if selector == name or selector == package:
            return name, package
    raise AppConfigError(
        f"App '{selector}' is not configured. Use 'apps' to list configured names and package IDs."
    )


def merge_apps(configured: dict[str, str], discovered: dict[str, str]) -> dict[str, str]:
    """Prefer configured aliases, and keep every distinct package addressable."""
    merged = dict(configured)
    known = set(merged.values())
    for name, package in discovered.items():
        if package in known:
            continue
        candidate = name if name not in merged else f"{name} ({package})"
        merged[candidate] = package
        known.add(package)
    return merged
