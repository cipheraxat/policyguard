from __future__ import annotations

import json
from typing import Any, Optional

from fastapi import APIRouter, Depends, File, Form, Header, HTTPException, UploadFile
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session

from policyguard.deps import (
    build_ingestion_service,
    build_query_service,
    get_app_services,
    get_session_factory,
)
from policyguard.models import AuditLog, Query
from policyguard.services.query import Answered, Escalated, Refused
from policyguard.services.review import ReviewQueueService

router = APIRouter()


def get_db() -> Session:
    SessionLocal = get_session_factory()
    session = SessionLocal()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


class QueryRequest(BaseModel):
    question: str
    userId: str
    filters: Optional[dict[str, Any]] = None


@router.post("/api/query")
def query_endpoint(body: QueryRequest, db: Session = Depends(get_db)):
    svc = build_query_service(db)
    outcome = svc.handle(body.question, body.userId, body.filters)
    if isinstance(outcome, Answered):
        return {
            "queryId": outcome.query_id,
            "status": "answered",
            "answer": outcome.answer,
            "citations": [
                {
                    "chunkId": c.chunk_id,
                    "documentId": c.document_id,
                    "paragraphRef": c.paragraph_ref,
                    "textSnippet": c.text_snippet,
                }
                for c in outcome.citations
            ],
            "confidenceScore": outcome.confidence_score,
            "retrievalHitsCount": outcome.retrieval_hits_count,
        }
    if isinstance(outcome, Refused):
        return {
            "queryId": outcome.query_id,
            "status": "refused",
            "reason": outcome.reason,
            "message": outcome.message,
        }
    # Escalated
    from fastapi.responses import JSONResponse

    return JSONResponse(
        status_code=202,
        content={
            "queryId": outcome.query_id,
            "status": "escalated",
            "reason": outcome.reason,
            "reviewItemId": outcome.review_item_id,
            "message": outcome.message,
        },
    )


@router.post("/api/documents")
async def documents_endpoint(
    file: UploadFile = File(...),
    metadata: Optional[str] = Form(None),
    db: Session = Depends(get_db),
):
    meta: dict[str, Any] = {}
    if metadata:
        meta = json.loads(metadata)
    content = await file.read()
    svc = build_ingestion_service(db)
    result = svc.ingest_bytes(content, file.filename or "upload.pdf", meta)
    from fastapi.responses import JSONResponse

    return JSONResponse(
        status_code=202,
        content={
            "documentId": result.document_id,
            "chunksCreated": result.chunks_created,
            "piiEntitiesRedacted": result.pii_entities_redacted,
            "title": result.title,
        },
    )


@router.get("/api/review-queue")
def review_queue(db: Session = Depends(get_db)):
    apps = get_app_services()
    review = ReviewQueueService(db, apps.redis)
    items = review.list_pending()
    dtos = []
    for item in items:
        q = db.scalars(select(Query).where(Query.query_id == item.query_id)).first()
        dtos.append(
            {
                "itemId": item.item_id,
                "queryId": item.query_id,
                "originalQuestion": q.original_prompt if q else None,
                "escalationReason": item.escalation_reason,
                "riskCategory": item.risk_category,
                "status": item.status,
                "createdAt": item.created_at.isoformat() if item.created_at else None,
            }
        )
    return {"items": dtos, "count": len(dtos)}


class ResolveRequest(BaseModel):
    reviewerId: str
    decision: str
    notes: Optional[str] = None
    overrideAnswer: Optional[str] = None


@router.post("/api/review/{item_id}/resolve")
def resolve_review(
    item_id: str,
    body: ResolveRequest,
    x_reviewer_id: Optional[str] = Header(default=None, alias="X-Reviewer-Id"),
    db: Session = Depends(get_db),
):
    if not x_reviewer_id or x_reviewer_id != body.reviewerId:
        raise HTTPException(status_code=401, detail="Unauthorized")
    apps = get_app_services()
    allowed = apps.settings.reviewers_allowed_ids or []
    if allowed and x_reviewer_id not in allowed:
        raise HTTPException(status_code=403, detail="Forbidden")

    review = ReviewQueueService(db, apps.redis)
    try:
        item = review.resolve(
            item_id, body.reviewerId, body.decision, body.notes, body.overrideAnswer
        )
    except KeyError as e:
        raise HTTPException(status_code=404, detail=str(e)) from e
    except (ValueError, RuntimeError) as e:
        raise HTTPException(status_code=400, detail=str(e)) from e

    final_answer = None
    if item.status == "overridden":
        final_answer = item.override_answer or item.reviewer_notes
    elif item.status == "approved":
        q = db.scalars(select(Query).where(Query.query_id == item.query_id)).first()
        if q is not None:
            outcome = build_query_service(db).handle_approved(
                item.query_id, q.original_prompt, body.reviewerId
            )
            if isinstance(outcome, Answered):
                final_answer = outcome.answer
            elif isinstance(outcome, Refused):
                final_answer = outcome.message

    return {
        "itemId": item.item_id,
        "status": item.status,
        "finalAnswer": final_answer,
        "resolvedAt": item.resolved_at.isoformat() if item.resolved_at else None,
    }


@router.get("/api/audit/{query_id}")
def audit_log(query_id: str, db: Session = Depends(get_db)):
    logs = list(
        db.scalars(
            select(AuditLog)
            .where(AuditLog.query_id == query_id)
            .order_by(AuditLog.timestamp.asc())
        )
    )
    return {
        "queryId": query_id,
        "events": [
            {
                "logId": e.log_id,
                "eventType": e.event_type,
                "actor": e.actor,
                "inputData": e.input_data,
                "outputData": e.output_data,
                "timestamp": e.timestamp.isoformat() if e.timestamp else None,
            }
            for e in logs
        ],
    }


@router.get("/health")
def health():
    return {"status": "UP"}
