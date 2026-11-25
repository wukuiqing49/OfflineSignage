"""Narrow API wrapper exposing only the approved operations."""

from __future__ import annotations

from typing import Any


class PlayClient:
    """Expose review reads and the narrowly-scoped listing read flow only."""

    def __init__(self, service: Any):
        self._service = service

    def list_reviews(
        self,
        package_name: str,
        *,
        page_size: int = 100,
        token: str | None = None,
        translation_language: str | None = None,
    ) -> dict[str, Any]:
        params: dict[str, Any] = {
            "packageName": package_name,
            "maxResults": page_size,
        }
        if token:
            params["token"] = token
        if translation_language:
            params["translationLanguage"] = translation_language
        return self._service.reviews().list(**params).execute(num_retries=3)

    def get_listing(self, package_name: str, locale: str) -> dict[str, Any]:
        edit = self._service.edits().insert(
            packageName=package_name,
            body={},
        ).execute(num_retries=0)
        edit_id = edit.get("id")
        if not edit_id:
            raise RuntimeError("Google Play did not return a temporary edit ID.")
        listing = self._service.edits().listings().get(
            packageName=package_name,
            editId=edit_id,
            language=locale,
        ).execute(num_retries=3)
        return {
            "editId": edit_id,
            "editExpiryTimeSeconds": edit.get("expiryTimeSeconds"),
            "listing": listing,
        }
