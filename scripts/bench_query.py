#!/usr/bin/env python3
"""Simple stub-profile latency probe (replaces JMH)."""
from __future__ import annotations

import statistics
import time

from policyguard.config import Settings, get_settings
from policyguard.deps import bootstrap, build_ingestion_service, build_query_service, get_session_factory
from policyguard.fixtures.factory import build
from policyguard.models import Document, init_db, reset_engine
from sqlalchemy import select


def main() -> None:
    reset_engine()
    get_settings.cache_clear()
    settings = Settings(POLICYGUARD_PROFILE="stub")
    bootstrap(settings)
    init_db(settings.embedding_dim)
    SessionLocal = get_session_factory()
    session = SessionLocal()
    try:
        if not session.scalars(select(Document).where(Document.document_id == "POL-TEST-001")).first():
            f = build("POL-TEST-001")
            build_ingestion_service(session).ingest_bytes(
                f.to_pdf_bytes(),
                "POL-TEST-001.pdf",
                {"documentId": f.document_id, "title": f.title, "docType": f.doc_type},
            )
            session.commit()
        qs = build_query_service(session)
        samples = []
        for _ in range(20):
            t0 = time.perf_counter()
            qs.handle(
                "How long must customer PII be retained after account closure?",
                "bench",
                None,
            )
            samples.append((time.perf_counter() - t0) * 1000)
        session.commit()
        print(
            f"n={len(samples)} mean_ms={statistics.mean(samples):.1f} "
            f"p95_ms={sorted(samples)[int(0.95*(len(samples)-1))]:.1f}"
        )
    finally:
        session.close()


if __name__ == "__main__":
    main()
