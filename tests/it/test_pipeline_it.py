from __future__ import annotations

import os

import pytest

pytestmark = pytest.mark.it


@pytest.fixture(scope="module")
def postgres_url():
    try:
        from testcontainers.postgres import PostgresContainer
    except Exception:
        pytest.skip("testcontainers not available")

    # Need pgvector image
    with PostgresContainer("pgvector/pgvector:pg16") as pg:
        # testcontainers uses psycopg2-style URL; adapt
        url = pg.get_connection_url().replace("psycopg2", "psycopg")
        if url.startswith("postgresql://"):
            url = url.replace("postgresql://", "postgresql+psycopg://", 1)
        yield url


def test_query_pipeline_answered(postgres_url, monkeypatch):
    monkeypatch.setenv("DATABASE_URL", postgres_url)
    monkeypatch.setenv("POLICYGUARD_PROFILE", "stub")

    from policyguard.config import get_settings
    from policyguard.deps import bootstrap, build_ingestion_service, build_query_service, get_session_factory
    from policyguard.fixtures.factory import build
    from policyguard.models import init_db, reset_engine
    from policyguard.services.query import Answered

    reset_engine()
    get_settings.cache_clear()
    settings = get_settings()
    settings.profile = "stub"
    bootstrap(settings)
    init_db(settings.embedding_dim)

    SessionLocal = get_session_factory()
    session = SessionLocal()
    try:
        fixture = build("POL-TEST-001")
        ingest = build_ingestion_service(session)
        ingest.ingest_bytes(
            fixture.to_pdf_bytes(),
            "POL-TEST-001.pdf",
            {"documentId": fixture.document_id, "title": fixture.title, "docType": fixture.doc_type},
        )
        session.commit()

        qs = build_query_service(session)
        outcome = qs.handle(
            "How long must customer PII be retained after account closure?",
            "it-user",
            None,
        )
        session.commit()
        assert isinstance(outcome, Answered)
        assert outcome.citations
    finally:
        session.close()
        reset_engine()
