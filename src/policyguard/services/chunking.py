from __future__ import annotations

import re
from dataclasses import dataclass

from policyguard.config import Settings

NUMERIC_HEADING = re.compile(r"^\s*(\d+(?:\.\d+)*)(?:\s+\S.*)?$")
SECTION_KEYWORD = re.compile(r"^Section\s+(\d+(?:\.\d+)*)", re.IGNORECASE)


@dataclass
class Chunk:
    text: str
    paragraph_ref: str


class ChunkingService:
    def __init__(self, settings: Settings | None = None, max_chars: int | None = None, overlap: int | None = None) -> None:
        if settings is not None:
            self.max_chars = settings.chunking_max_chars
            self.overlap = settings.chunking_overlap
        else:
            self.max_chars = max_chars if max_chars is not None else 1200
            self.overlap = overlap if overlap is not None else 150

    def chunk(self, text: str | None) -> list[Chunk]:
        if text is None or not text.strip():
            return []
        raw_paragraphs = re.split(r"\n{2,}", text)
        result: list[Chunk] = []
        current_section: str | None = None
        para_index = 0

        for raw in raw_paragraphs:
            para = raw.strip()
            if not para:
                continue
            detected = self._detect_section_heading(para)
            if detected is not None:
                current_section = detected
                para_index = 0
                ref = self._build_ref(current_section, 0)
                result.extend(self._split_to_parts(para, ref))
                continue
            para_index += 1
            ref = self._build_ref(current_section, para_index)
            result.extend(self._split_to_parts(para, ref))
        return result

    @staticmethod
    def _detect_section_heading(para: str) -> str | None:
        m = SECTION_KEYWORD.match(para)
        if m:
            return m.group(1)
        if "\n" not in para and len(para) <= 120:
            nm = NUMERIC_HEADING.match(para)
            if nm:
                return nm.group(1)
        return None

    @staticmethod
    def _build_ref(section: str | None, para_index: int) -> str:
        if section is None:
            return f"Paragraph {para_index}"
        if para_index == 0:
            return f"Section {section}"
        return f"Section {section}, Paragraph {para_index}"

    def _split_to_parts(self, text: str, ref: str) -> list[Chunk]:
        if len(text) <= self.max_chars:
            return [Chunk(text, ref)]
        parts: list[Chunk] = []
        stride = max(1, self.max_chars - self.overlap)
        start = 0
        while start < len(text):
            end = min(start + self.max_chars, len(text))
            parts.append(Chunk(text[start:end], ref))
            start += stride
        return parts
