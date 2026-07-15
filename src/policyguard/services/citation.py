from __future__ import annotations

import re
from dataclasses import dataclass

from policyguard.providers import ChatProvider
from policyguard.services.rrf import RetrievalHit

CITATION_PATTERN = re.compile(r"\[Doc:\s*([^,\]]+?)\s*,\s*Para:\s*([^\]]+?)\s*\]")
SNIPPET_MAX = 200


@dataclass
class Citation:
    chunk_id: str | None
    document_id: str
    paragraph_ref: str
    text_snippet: str | None


@dataclass
class CitationResult:
    response_text: str
    citations: list[Citation]
    confidence: float
    hits: list[RetrievalHit]


class CitationGenerator:
    def __init__(self, chat: ChatProvider) -> None:
        self._chat = chat

    def generate(self, query: str, hits: list[RetrievalHit]) -> CitationResult:
        formatted = self._format_hits(hits)
        prompt = (
            "Answer the compliance question using ONLY the provided document excerpts.\n"
            "For each claim, cite the source using [Doc: {document_id}, Para: {paragraph_ref}].\n"
            "If the excerpts do not contain enough information, respond with EXACTLY:\n"
            '"I cannot answer this based on the available policy documents."\n\n'
            f"Question: {query}\n\n"
            f"Excerpts:\n{formatted}"
        )
        response_text = self._chat.complete(prompt)
        citations = self._parse_citations(response_text, hits)
        confidence = self._compute_confidence(hits)
        return CitationResult(response_text, citations, confidence, hits)

    @staticmethod
    def _format_hits(hits: list[RetrievalHit]) -> str:
        parts = []
        for h in hits:
            parts.append(f"[Doc: {h.document_id}, Para: {h.paragraph_ref}] {h.text}")
        return "\n".join(parts)

    def _parse_citations(self, response_text: str, hits: list[RetrievalHit]) -> list[Citation]:
        result: list[Citation] = []
        for m in CITATION_PATTERN.finditer(response_text or ""):
            doc_id = m.group(1).strip()
            para_ref = m.group(2).strip()
            matched = next(
                (h for h in hits if h.document_id == doc_id and h.paragraph_ref == para_ref),
                None,
            )
            if matched:
                snippet = matched.text[:SNIPPET_MAX]
                result.append(Citation(matched.chunk_id, doc_id, para_ref, snippet))
            else:
                result.append(Citation(None, doc_id, para_ref, None))
        return result

    @staticmethod
    def _compute_confidence(hits: list[RetrievalHit]) -> float:
        if not hits:
            return 0.0
        count = min(3, len(hits))
        mean = sum(hits[i].score for i in range(count)) / count
        return max(0.0, min(1.0, mean))
