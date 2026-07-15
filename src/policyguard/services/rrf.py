from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass
class RetrievalHit:
    chunk_id: str
    document_id: str
    paragraph_ref: str
    text: str
    score: float
    metadata: dict[str, Any] = field(default_factory=dict)


class RrfFusionService:
    def fuse(
        self, a: list[RetrievalHit], b: list[RetrievalHit], k: int
    ) -> list[RetrievalHit]:
        rank_a = {h.chunk_id: i + 1 for i, h in enumerate(a)}
        rank_b = {h.chunk_id: i + 1 for i, h in enumerate(b)}
        all_ids: list[str] = []
        seen: set[str] = set()
        for h in a + b:
            if h.chunk_id not in seen:
                seen.add(h.chunk_id)
                all_ids.append(h.chunk_id)

        hit_source: dict[str, RetrievalHit] = {}
        for h in b:
            hit_source[h.chunk_id] = h
        for h in a:
            hit_source[h.chunk_id] = h

        rrf: dict[str, float] = {}
        for cid in all_ids:
            score = 0.0
            if cid in rank_a:
                score += 1.0 / (k + rank_a[cid])
            if cid in rank_b:
                score += 1.0 / (k + rank_b[cid])
            rrf[cid] = score

        sorted_ids = sorted(all_ids, key=lambda i: (-rrf[i], i))
        return [hit_source[i] for i in sorted_ids]

    def rrf_score(self, rank: int, k: int) -> float:
        return 1.0 / (k + rank)
