from __future__ import annotations

import io
import logging
from dataclasses import dataclass
from typing import Any
from uuid import uuid4

from pypdf import PdfReader
from sqlalchemy import text
from sqlalchemy.orm import Session

from policyguard.config import Settings
from policyguard.models import Document, DocumentChunk
from policyguard.providers import EmbeddingProvider
from policyguard.services.chunking import ChunkingService
from policyguard.services.pii import PiiRedactionGateway

logger = logging.getLogger(__name__)


@dataclass
class IngestionResult:
    document_id: str
    chunks_created: int
    pii_entities_redacted: int
    title: str


class DocumentIngestionService:
    def __init__(
        self,
        session: Session,
        pii: PiiRedactionGateway,
        chunking: ChunkingService,
        embedding: EmbeddingProvider,
        settings: Settings,
    ) -> None:
        self._session = session
        self._pii = pii
        self._chunking = chunking
        self._embedding = embedding
        self._settings = settings

    def ingest_bytes(
        self,
        pdf_bytes: bytes,
        filename: str,
        metadata: dict[str, Any] | None = None,
    ) -> IngestionResult:
        if len(pdf_bytes) > self._settings.ingestion_max_file_bytes:
            raise ValueError("File exceeds max size")
        raw_text = self._extract_pdf(pdf_bytes)
        return self.ingest_text(raw_text, filename, metadata)

    def ingest_text(
        self,
        raw_text: str,
        source_path: str,
        metadata: dict[str, Any] | None = None,
    ) -> IngestionResult:
        metadata = metadata or {}
        redaction = self._pii.redact(raw_text)
        redacted = redaction.redacted_text
        document_id = str(metadata.get("documentId") or f"DOC-{uuid4().hex[:8].upper()}")
        title = str(metadata.get("title") or source_path or document_id)
        doc_type = str(metadata.get("docType") or "policy")

        doc = Document(
            document_id=document_id,
            title=title,
            doc_type=doc_type,
            source_path=source_path,
            content_raw=redacted,
            metadata_=metadata,
        )
        self._session.add(doc)
        self._session.flush()

        chunks = self._chunking.chunk(redacted)
        embeddings = self._embedding.embed_batch([c.text for c in chunks])
        for i, chunk in enumerate(chunks):
            entity = DocumentChunk(
                chunk_id=f"{document_id}-c{i}",
                document_id=document_id,
                paragraph_ref=chunk.paragraph_ref,
                text=chunk.text,
                embedding=embeddings[i],
                metadata_={},
            )
            self._session.add(entity)
        self._session.flush()

        try:
            self._session.execute(text("ANALYZE document_chunks"))
        except Exception as e:
            logger.warning("ANALYZE failed: %s", e)

        return IngestionResult(
            document_id=document_id,
            chunks_created=len(chunks),
            pii_entities_redacted=len(redaction.entities_found),
            title=title,
        )

    @staticmethod
    def _extract_pdf(pdf_bytes: bytes) -> str:
        reader = PdfReader(io.BytesIO(pdf_bytes))
        parts = []
        for page in reader.pages:
            parts.append(page.extract_text() or "")
        return "\n\n".join(parts)
