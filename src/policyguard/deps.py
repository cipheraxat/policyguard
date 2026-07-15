from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Generator

import redis
from sqlalchemy.orm import Session

from policyguard.config import Settings, get_settings
from policyguard.models import get_session_factory, init_db
from policyguard.providers import ChatProvider, EmbeddingProvider, build_providers
from policyguard.services.audit import AuditLogService
from policyguard.services.chunking import ChunkingService
from policyguard.services.citation import CitationGenerator
from policyguard.services.gate import ConfidenceGate
from policyguard.services.ingestion import DocumentIngestionService
from policyguard.services.pii import PiiRedactionGateway, build_pii_detector
from policyguard.services.query import QueryService
from policyguard.services.retrieval import HybridRetriever
from policyguard.services.review import ReviewQueueService
from policyguard.services.risk import RiskClassifier
from policyguard.services.rrf import RrfFusionService


@dataclass
class AppServices:
    settings: Settings
    chat: ChatProvider
    embedding: EmbeddingProvider
    redis: Any | None


_app_services: AppServices | None = None


def bootstrap(settings: Settings | None = None) -> AppServices:
    global _app_services
    settings = settings or get_settings()
    init_db(settings.embedding_dim)
    chat, embedding = build_providers(settings)
    r = None
    try:
        r = redis.from_url(settings.redis_url, decode_responses=True)
        r.ping()
    except Exception:
        r = None
    _app_services = AppServices(settings=settings, chat=chat, embedding=embedding, redis=r)
    return _app_services


def get_app_services() -> AppServices:
    if _app_services is None:
        return bootstrap()
    return _app_services


def build_query_service(session: Session, apps: AppServices | None = None) -> QueryService:
    apps = apps or get_app_services()
    settings = apps.settings
    pii = PiiRedactionGateway(build_pii_detector(settings))
    risk = RiskClassifier(settings)
    rrf = RrfFusionService()
    retriever = HybridRetriever(session, apps.embedding, rrf, settings)
    citation = CitationGenerator(apps.chat)
    gate = ConfidenceGate(settings)
    review = ReviewQueueService(session, apps.redis)
    audit = AuditLogService(session)
    return QueryService(session, pii, risk, retriever, citation, gate, review, audit, settings)


def build_ingestion_service(session: Session, apps: AppServices | None = None) -> DocumentIngestionService:
    apps = apps or get_app_services()
    settings = apps.settings
    pii = PiiRedactionGateway(build_pii_detector(settings))
    chunking = ChunkingService(settings)
    return DocumentIngestionService(session, pii, chunking, apps.embedding, settings)


def session_scope() -> Generator[Session, None, None]:
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
