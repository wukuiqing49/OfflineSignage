"""Google service-account authentication."""

from __future__ import annotations

import os
from pathlib import Path


ANDROID_PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher"


class AuthenticationError(RuntimeError):
    """Raised when local credentials cannot be loaded safely."""


def credential_file(explicit_path: str | None = None) -> Path:
    raw_path = explicit_path or os.environ.get("GOOGLE_APPLICATION_CREDENTIALS")
    if not raw_path:
        raise AuthenticationError("Set GOOGLE_APPLICATION_CREDENTIALS to a local credential file; never bundle credentials with this Skill.")
    path = Path(raw_path).expanduser()
    if not path.is_file():
        raise AuthenticationError(
            "Credential file is unavailable. "
            "set GOOGLE_APPLICATION_CREDENTIALS, or pass --credentials."
        )
    return path


def build_android_publisher(credentials_path: str | None = None):
    try:
        from google.auth import exceptions as google_auth_exceptions
        from google.oauth2 import service_account
        from google_auth_httplib2 import AuthorizedHttp
        import httplib2
        from googleapiclient.discovery import build
    except ImportError as exc:
        raise AuthenticationError(
            "Google API dependencies are missing. Install this Skill requirements.txt using the current Python interpreter."
        ) from exc

    path = credential_file(credentials_path)
    try:
        credentials = service_account.Credentials.from_service_account_file(
            str(path), scopes=[ANDROID_PUBLISHER_SCOPE]
        )
        http = AuthorizedHttp(credentials, http=httplib2.Http(timeout=30))
        return build(
            "androidpublisher",
            "v3",
            http=http,
            cache_discovery=False,
        )
    except (OSError, ValueError, google_auth_exceptions.GoogleAuthError) as exc:
        raise AuthenticationError(
            f"Credential file could not be parsed ({type(exc).__name__}); key contents were not displayed."
        ) from exc
    except Exception as exc:
        raise AuthenticationError(
            f"Could not initialize the Google Play API client ({type(exc).__name__}); key contents were not displayed."
        ) from exc
