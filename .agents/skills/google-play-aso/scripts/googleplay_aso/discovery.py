"""Discover accessible apps through the Play Developer Reporting API."""

from __future__ import annotations

import json
from urllib.parse import urlencode

from .auth import AuthenticationError, credential_file


SCOPE = "https://www.googleapis.com/auth/playdeveloperreporting"
ENDPOINT = "https://playdeveloperreporting.googleapis.com/v1beta1/apps:search"


class DiscoveryError(RuntimeError):
    """An app catalog could not be obtained without guessing."""


def authorized_http(credentials_path: str | None = None):
    try:
        from google.oauth2 import service_account
        from google_auth_httplib2 import AuthorizedHttp
        import httplib2
    except ImportError as exc:
        raise AuthenticationError("Google API dependencies are missing; install the project first.") from exc
    try:
        credentials = service_account.Credentials.from_service_account_file(
            str(credential_file(credentials_path)), scopes=[SCOPE]
        )
        return AuthorizedHttp(credentials, http=httplib2.Http(timeout=30))
    except (OSError, ValueError) as exc:
        raise AuthenticationError(f"Credential could not be loaded ({type(exc).__name__}).") from exc


def discover_apps(http) -> dict[str, str]:
    """Return display name -> package name, consuming every response page."""
    found: dict[str, str] = {}
    seen_tokens: set[str] = set()
    token = ""
    while True:
        query = {"pageSize": 1000}
        if token:
            query["pageToken"] = token
        try:
            response, body = http.request(f"{ENDPOINT}?{urlencode(query)}", "GET")
        except Exception as exc:
            raise DiscoveryError(f"App discovery request failed ({type(exc).__name__}).") from exc
        status = int(response.status)
        if status != 200:
            hint = (
                "Enable Play Developer Reporting API and grant this service account app access in Play Console."
                if status in {403, 404}
                else "Check credentials, network, and API availability."
            )
            raise DiscoveryError(f"App discovery HTTP {status}. {hint}")
        try:
            payload = json.loads(body)
            apps = payload.get("apps", [])
            if not isinstance(apps, list):
                raise ValueError("Invalid apps array")
            for app in apps:
                package = app["packageName"]
                if not isinstance(package, str) or not package:
                    raise ValueError("Invalid packageName")
                name = app.get("displayName") or package
                if not isinstance(name, str):
                    raise ValueError("Invalid displayName")
                if name in found and found[name] != package:
                    name = f"{name} ({package})"
                found[name] = package
            next_token = payload.get("nextPageToken", "")
            if not isinstance(next_token, str):
                raise ValueError("Invalid nextPageToken")
        except (ValueError, KeyError, TypeError) as exc:
            raise DiscoveryError("App discovery returned an invalid response.") from exc
        if not next_token:
            break
        if next_token in seen_tokens:
            raise DiscoveryError("App discovery repeated a page token.")
        seen_tokens.add(next_token)
        token = next_token
    if not found:
        raise DiscoveryError("No apps are accessible to this service account via Play Developer Reporting API.")
    return found
