from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any
from uuid import uuid4

from sqlalchemy.orm import Session

from policyguard.config import Settings
from policyguard.models import Answer, Query
from policyguard.services.audit import AuditLogService
from policyguard.services.citation import Citation, CitationGenerator
from policyguard.services.gate import ConfidenceGate, GateDecision
from policyguard.services.pii import PiiRedactionGateway
from policyguard.services.retrieval import HybridRetriever
from policyguard.services.review import ReviewQueueService
from policyguard.services.risk import RiskClassifier
from policyguard.services.rrf import RetrievalHit

REFUSAL_MSG = "I cannot answer this question based on the available policy documents."


@dataclass
class Answered:
    query_id: str
    answer: str
    citations: list[Citation]
    confidence_score: float
    retrieval_hits_count: int
    status: str = "answered"


@dataclass
class Refused:
    query_id: str
    reason: str
    message: str
    status: str = "refused"


@dataclass
class Escalated:
    query_id: str
    reason: str
    review_item_id: str
    message: str
    status: str = "escalated"


QueryOutcome = Answered | Refused | Escalated


class QueryService:
    def __init__(
        self,
        session: Session,
        pii: PiiRedactionGateway,
        risk: RiskClassifier,
        retriever: HybridRetriever,
        citation: CitationGenerator,
        gate: ConfidenceGate,
        review: ReviewQueueService,
        audit: AuditLogService,
        settings: Settings,
    ) -> None:
        self._session = session
        self._pii = pii
        self._risk = risk
        self._retriever = retriever
        self._citation = citation
        self._gate = gate
        self._review = review
        self._audit = audit
        self._settings = settings

    def handle(
        self, question: str, user_id: str, filters: dict[str, Any] | None
    ) -> QueryOutcome:
        query_id = f"qry-{uuid4()}"
        original_length = 0 if question is None else len(question)

        redaction = self._pii.redact(question)
        redacted_text = redaction.redacted_text

        query = Query(
            query_id=query_id,
            original_prompt=redacted_text,
            redacted=redaction.was_redacted,
            redaction_log={"entities": redaction.entities_found},
            status="pending",
        )
        self._session.add(query)
        self._session.flush()

        self._audit.append(
            query_id,
            "prompt_received",
            user_id,
            {"raw_length": original_length},
            {"redacted_prompt": redacted_text, "was_redacted": redaction.was_redacted},
        )
        self._audit.append(
            query_id,
            "pii_redaction",
            "system",
            {},
            {
                "entities_found": redaction.entities_found,
                "count": len(redaction.entities_found),
            },
        )

        risk = self._risk.classify(redacted_text)
        self._audit.append(
            query_id,
            "risk_classification",
            "system",
            {},
            {"risk_level": risk.risk_level, "category": risk.category or "none"},
        )

        if risk.requires_review:
            item = self._review.enqueue(query_id, risk.category or "unknown", risk.risk_level)
            query.status = "escalated"
            self._session.flush()
            self._audit.append(
                query_id,
                "escalation",
                "system",
                {},
                {"review_item_id": item.item_id, "reason": risk.category},
            )
            self._audit.append(query_id, "response_sent", "system", {}, {"status": "escalated"})
            return Escalated(
                query_id=query_id,
                reason=f"{risk.category} detected",
                review_item_id=item.item_id,
                message="This question has been routed to the compliance team for review.",
            )

        return self._run_retrieval_pipeline(query_id, redacted_text, filters, "system", query)

    def handle_approved(self, query_id: str, original_prompt: str, reviewer_id: str) -> QueryOutcome:
        from sqlalchemy import select

        query = self._session.scalars(select(Query).where(Query.query_id == query_id)).first()
        if query is None:
            raise KeyError(f"Query not found: {query_id}")
        actor = f"reviewer-{reviewer_id}"
        return self._run_retrieval_pipeline(query_id, original_prompt, None, actor, query)

    def _run_retrieval_pipeline(
        self,
        query_id: str,
        text: str,
        filters: dict[str, Any] | None,
        actor: str,
        query: Query,
    ) -> QueryOutcome:
        hits = self._retriever.retrieve(text, filters, self._settings.retrieval_top_k)
        top_score = hits[0].score if hits else 0.0
        self._audit.append(
            query_id, "retrieval", "system", {}, {"hits": len(hits), "top_score": top_score}
        )

        if not hits:
            self._persist_answer(query_id, REFUSAL_MSG, [], 0.0, [])
            query.status = "refused"
            self._session.flush()
            self._audit.append(query_id, "generation", "system", {}, {"skipped": True, "reason": "no_hits"})
            self._audit.append(query_id, "response_sent", actor, {}, {"status": "refused"})
            return Refused(query_id, "No relevant documents found", REFUSAL_MSG)

        cr = self._citation.generate(text, hits)
        is_explicit = self._gate.is_explicit_refusal(cr.response_text)
        self._audit.append(
            query_id,
            "generation",
            "system",
            {},
            {
                "confidence": cr.confidence,
                "citation_count": len(cr.citations),
                "explicit_refusal": is_explicit,
            },
        )

        if is_explicit:
            self._persist_answer(query_id, cr.response_text, [], cr.confidence, hits)
            query.status = "refused"
            self._session.flush()
            self._audit.append(query_id, "response_sent", actor, {}, {"status": "refused"})
            return Refused(query_id, "Model declined to answer based on excerpts", cr.response_text)

        gate = self._gate.evaluate(cr.confidence, cr.citations, cr.hits)
        if gate.decision == GateDecision.REFUSE:
            self._persist_answer(query_id, cr.response_text, [], cr.confidence, hits)
            query.status = "refused"
            self._session.flush()
            self._audit.append(query_id, "response_sent", actor, {}, {"status": "refused"})
            return Refused(query_id, gate.reason, REFUSAL_MSG)

        self._persist_answer(query_id, cr.response_text, cr.citations, cr.confidence, hits)
        query.status = "answered"
        self._session.flush()
        self._audit.append(
            query_id,
            "response_sent",
            actor,
            {},
            {"status": "answered", "citation_count": len(cr.citations)},
        )
        return Answered(
            query_id=query_id,
            answer=cr.response_text,
            citations=cr.citations,
            confidence_score=cr.confidence,
            retrieval_hits_count=len(hits),
        )

    def _persist_answer(
        self,
        query_id: str,
        response_text: str,
        citations: list[Citation],
        confidence: float,
        hits: list[RetrievalHit],
    ) -> None:
        answer = Answer(
            query_id=query_id,
            response_text=response_text,
            citations=[
                {
                    "chunkId": c.chunk_id,
                    "documentId": c.document_id,
                    "paragraphRef": c.paragraph_ref,
                    "textSnippet": c.text_snippet,
                }
                for c in citations
            ],
            confidence_score=confidence,
            retrieval_hits=[
                {
                    "chunk_id": h.chunk_id,
                    "document_id": h.document_id,
                    "paragraph_ref": h.paragraph_ref,
                    "score": h.score,
                    "text_snippet": (h.text[:200] if h.text else None),
                }
                for h in hits
            ],
            generated_at=datetime.now(timezone.utc),
        )
        self._session.add(answer)
        self._session.flush()
