from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import httpx

from policyguard.config import Settings


@dataclass
class PresidioEntity:
    entity_type: str
    start: int
    end: int
    score: float = 0.0


@dataclass
class RedactionResult:
    redacted_text: str
    entities_found: list[dict[str, Any]]
    was_redacted: bool


PLACEHOLDERS = {
    "PERSON": "<PERSON>",
    "EMAIL_ADDRESS": "<EMAIL>",
    "PHONE_NUMBER": "<PHONE>",
    "CREDIT_CARD": "<CREDIT_CARD>",
    "US_SSN": "<SSN>",
    "US_BANK_NUMBER": "<BANK_ACCOUNT>",
    "IP_ADDRESS": "<IP>",
}


def to_placeholder(entity_type: str) -> str:
    return PLACEHOLDERS.get(entity_type, f"<{entity_type}>")


class PresidioClient:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._base = settings.presidio_base_url.rstrip("/")

    def analyze(self, text: str, language: str = "en") -> list[PresidioEntity]:
        if self._settings.presidio_stub:
            return []
        timeout = httpx.Timeout(
            connect=self._settings.presidio_connect_timeout_ms / 1000,
            read=self._settings.presidio_read_timeout_ms / 1000,
            write=5.0,
            pool=5.0,
        )
        with httpx.Client(timeout=timeout) as client:
            resp = client.post(
                f"{self._base}/analyze",
                json={"text": text, "language": language},
            )
            resp.raise_for_status()
            data = resp.json() or []
        return [
            PresidioEntity(
                entity_type=e.get("entity_type") or e.get("entityType"),
                start=int(e["start"]),
                end=int(e["end"]),
                score=float(e.get("score", 0)),
            )
            for e in data
        ]


class PiiRedactionGateway:
    def __init__(self, client: PresidioClient) -> None:
        self._client = client

    def redact(self, text: str | None) -> RedactionResult:
        if text is None:
            text = ""
        entities = self._client.analyze(text, "en")
        sorted_ents = sorted(entities, key=lambda e: e.start, reverse=True)
        chars = list(text)
        # replace using string slicing for correctness with multi-char placeholders
        result = text
        for ent in sorted_ents:
            placeholder = to_placeholder(ent.entity_type)
            result = result[: ent.start] + placeholder + result[ent.end :]
        found = [
            {"type": e.entity_type, "start": e.start, "end": e.end} for e in entities
        ]
        return RedactionResult(result, found, bool(entities))
