from __future__ import annotations

import argparse

from policyguard.config import get_settings
from policyguard.deps import bootstrap, build_ingestion_service, get_session_factory
from policyguard.fixtures.factory import build_all
from policyguard.models import Document, init_db
from sqlalchemy import select


def seed(force: bool = False) -> None:
    settings = get_settings()
    settings.profile = settings.profile or "stub"
    bootstrap(settings)
    init_db(settings.embedding_dim)
    SessionLocal = get_session_factory()
    session = SessionLocal()
    try:
        svc = build_ingestion_service(session)
        for fixture in build_all():
            existing = session.scalars(
                select(Document).where(Document.document_id == fixture.document_id)
            ).first()
            if existing and not force:
                print(f"skip {fixture.document_id}")
                continue
            if existing and force:
                # simple: skip delete cascade complexity — only insert if missing
                print(f"exists {fixture.document_id}")
                continue
            meta = {
                "documentId": fixture.document_id,
                "title": fixture.title,
                "docType": fixture.doc_type,
                **fixture.metadata,
            }
            result = svc.ingest_bytes(fixture.to_pdf_bytes(), f"{fixture.document_id}.pdf", meta)
            print(f"ingested {result.document_id} chunks={result.chunks_created}")
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


def main() -> None:
    parser = argparse.ArgumentParser(description="Seed PolicyGuard fixtures")
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()
    seed(force=args.force)


if __name__ == "__main__":
    main()
