from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any, Protocol

from policyguard.config import Settings


@dataclass
class PiiEntity:
    entity_type: str
    start: int
    end: int
    score: float = 1.0


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


def _luhn_ok(digits: str) -> bool:
    if not digits.isdigit() or not (13 <= len(digits) <= 19):
        return False
    total = 0
    reverse = digits[::-1]
    for i, ch in enumerate(reverse):
        n = int(ch)
        if i % 2 == 1:
            n *= 2
            if n > 9:
                n -= 9
        total += n
    return total % 10 == 0


# Structured PII patterns (in-process; no external analyzer service).
_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    ("EMAIL_ADDRESS", re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b")),
    (
        "PHONE_NUMBER",
        re.compile(
            r"(?<!\d)(?:\+?1[-.\s]?)?(?:\(?\d{3}\)?[-.\s]?)\d{3}[-.\s]?\d{4}(?!\d)"
        ),
    ),
    ("US_SSN", re.compile(r"\b\d{3}-\d{2}-\d{4}\b")),
    (
        "CREDIT_CARD",
        re.compile(r"\b(?:\d[ -]*?){13,19}\b"),
    ),
    (
        "IP_ADDRESS",
        re.compile(
            r"\b(?:(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}"
            r"(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\b"
        ),
    ),
    (
        "US_BANK_NUMBER",
        re.compile(r"(?i)\b(?:routing|account)\s*(?:number|#|no\.?)?\s*[:#]?\s*(\d{8,17})\b"),
    ),
    (
        "PERSON",
        re.compile(
            r"\b(?:Mr|Mrs|Ms|Dr|Prof)\.?\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+)?\b"
        ),
    ),
]


class PiiDetector(Protocol):
    def analyze(self, text: str, language: str = "en") -> list[PiiEntity]: ...


class RegexPiiDetector:
    """Detect common structured PII with regex (+ Luhn for cards)."""

    def analyze(self, text: str, language: str = "en") -> list[PiiEntity]:
        if not text:
            return []
        found: list[PiiEntity] = []
        for entity_type, pattern in _PATTERNS:
            for m in pattern.finditer(text):
                if entity_type == "US_BANK_NUMBER" and m.lastindex:
                    start, end = m.start(1), m.end(1)
                else:
                    start, end = m.start(), m.end()
                if entity_type == "CREDIT_CARD":
                    digits = re.sub(r"\D", "", text[start:end])
                    if not _luhn_ok(digits):
                        continue
                found.append(PiiEntity(entity_type, start, end, 1.0))
        return _dedupe_overlaps(found)


class StubPiiDetector:
    """No-op detector for tests that want to isolate redaction side effects."""

    def analyze(self, text: str, language: str = "en") -> list[PiiEntity]:
        return []


def build_pii_detector(settings: Settings) -> PiiDetector:
    if settings.pii_stub:
        return StubPiiDetector()
    return RegexPiiDetector()


def _dedupe_overlaps(entities: list[PiiEntity]) -> list[PiiEntity]:
    """Prefer longer spans when ranges overlap; then earlier start."""
    ordered = sorted(entities, key=lambda e: (-(e.end - e.start), e.start, e.entity_type))
    kept: list[PiiEntity] = []
    for ent in ordered:
        if any(ent.start < k.end and ent.end > k.start for k in kept):
            continue
        kept.append(ent)
    return sorted(kept, key=lambda e: e.start)


class PiiRedactionGateway:
    def __init__(self, detector: PiiDetector) -> None:
        self._detector = detector

    def redact(self, text: str | None) -> RedactionResult:
        if text is None:
            text = ""
        entities = self._detector.analyze(text, "en")
        sorted_ents = sorted(entities, key=lambda e: e.start, reverse=True)
        result = text
        for ent in sorted_ents:
            placeholder = to_placeholder(ent.entity_type)
            result = result[: ent.start] + placeholder + result[ent.end :]
        found = [
            {"type": e.entity_type, "start": e.start, "end": e.end} for e in entities
        ]
        return RedactionResult(result, found, bool(entities))
