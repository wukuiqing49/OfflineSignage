"""Decode Play Console CSV exports using their byte-order mark."""

from __future__ import annotations


def decode_report(data: bytes) -> str:
    if data.startswith((b"\xff\xfe", b"\xfe\xff")):
        return data.decode("utf-16")
    return data.decode("utf-8-sig")
