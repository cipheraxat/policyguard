from __future__ import annotations

from typing import Any

from sqlalchemy import text
from sqlalchemy.orm import Session

from policyguard.config import Settings
from policyguard.providers import EmbeddingProvider
from policyguard.services.rrf import RetrievalHit, RrfFusionService


class HybridRetriever:
    def __init__(
        self,
        session: Session,
        embedding: EmbeddingProvider,
        rrf: RrfFusionService,
        settings: Settings,
    ) -> None:
        self._session = session
        self._embedding = embedding
        self._rrf = rrf
        self._settings = settings

    def retrieve(
        self,
        query_text: str,
        filters: dict[str, Any] | None,
        top_k: int,
    ) -> list[RetrievalHit]:
        embedding = self._embedding.embed(query_text)
        literal = "[" + ",".join(str(float(x)) for x in embedding) + "]"
        candidate_k = top_k * 2

        semantic_rows = self._session.execute(
            text(
                """
                SELECT chunk_id, document_id, paragraph_ref, text,
                       1 - (embedding <=> CAST(:q AS vector)) AS sim,
                       metadata::text
                FROM document_chunks
                WHERE embedding IS NOT NULL
                ORDER BY embedding <=> CAST(:q AS vector) ASC
                LIMIT :limit
                """
            ),
            {"q": literal, "limit": candidate_k},
        ).fetchall()

        keyword_rows = self._session.execute(
            text(
                """
                SELECT chunk_id, document_id, paragraph_ref, text,
                       ts_rank_cd(to_tsvector('english', text), plainto_tsquery('english', :q)) AS rank,
                       metadata::text
                FROM document_chunks
                WHERE to_tsvector('english', text) @@ plainto_tsquery('english', :q)
                ORDER BY rank DESC
                LIMIT :limit
                """
            ),
            {"q": query_text, "limit": candidate_k},
        ).fetchall()

        semantic_hits = self._to_hits(semantic_rows)
        keyword_hits = self._to_hits(keyword_rows)
        rrf_k = self._settings.retrieval_rrf_k
        fused = self._rrf.fuse(semantic_hits, keyword_hits, rrf_k)

        semantic_ids = {h.chunk_id for h in semantic_hits}
        keyword_rank = {h.chunk_id: i + 1 for i, h in enumerate(keyword_hits)}

        scored: list[RetrievalHit] = []
        for hit in fused:
            if hit.chunk_id not in semantic_ids:
                rank = keyword_rank.get(hit.chunk_id, len(fused))
                hit = RetrievalHit(
                    hit.chunk_id,
                    hit.document_id,
                    hit.paragraph_ref,
                    hit.text,
                    self._rrf.rrf_score(rank, rrf_k),
                    hit.metadata,
                )
            scored.append(hit)

        filtered = self._apply_filters(scored, filters)
        return filtered[:top_k]

    def _to_hits(self, rows: list[Any]) -> list[RetrievalHit]:
        import json

        hits: list[RetrievalHit] = []
        for row in rows:
            meta_raw = row[5]
            try:
                metadata = json.loads(meta_raw) if meta_raw else {}
            except Exception:
                metadata = {}
            hits.append(
                RetrievalHit(
                    chunk_id=row[0],
                    document_id=row[1],
                    paragraph_ref=row[2],
                    text=row[3],
                    score=float(row[4] or 0.0),
                    metadata=metadata,
                )
            )
        return hits

    def _apply_filters(
        self, hits: list[RetrievalHit], filters: dict[str, Any] | None
    ) -> list[RetrievalHit]:
        if not filters:
            return hits
        out: list[RetrievalHit] = []
        for hit in hits:
            ok = True
            for key, value in filters.items():
                if key == "documentId":
                    if str(value) != hit.document_id:
                        ok = False
                        break
                else:
                    if str(value) != str(hit.metadata.get(key)):
                        ok = False
                        break
            if ok:
                out.append(hit)
        return out
