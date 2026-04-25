-- V1__init_schema.sql
-- PolicyGuard initial schema.
-- Uses Flyway placeholder ${embedding_dim} (configured via spring.flyway.placeholders.embedding_dim).

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ── Documents ────────────────────────────────────────────────────────────────

CREATE TABLE documents (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id VARCHAR(50)  NOT NULL UNIQUE,
    title       TEXT         NOT NULL,
    doc_type    VARCHAR(50)  NOT NULL,
    source_path TEXT         NOT NULL,
    content_raw TEXT         NOT NULL,
    metadata    JSONB        NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── Document chunks (with pgvector embeddings) ────────────────────────────────

CREATE TABLE document_chunks (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chunk_id      VARCHAR(50)              NOT NULL UNIQUE,
    document_id   VARCHAR(50)              REFERENCES documents(document_id),
    paragraph_ref TEXT                     NOT NULL,
    text          TEXT                     NOT NULL,
    embedding     VECTOR(${embedding_dim}),
    metadata      JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX ON document_chunks USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE INDEX idx_chunks_document ON document_chunks(document_id);

CREATE INDEX idx_chunks_fts ON document_chunks
    USING GIN (to_tsvector('english', text));

-- ── Queries ──────────────────────────────────────────────────────────────────

CREATE TABLE queries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    query_id        VARCHAR(50)  NOT NULL UNIQUE,
    original_prompt TEXT         NOT NULL,
    redacted        BOOLEAN      NOT NULL DEFAULT FALSE,
    redaction_log   JSONB        NOT NULL DEFAULT '{}',
    status          VARCHAR(50)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── Answers ───────────────────────────────────────────────────────────────────

CREATE TABLE answers (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    query_id         VARCHAR(50) REFERENCES queries(query_id),
    response_text    TEXT        NOT NULL,
    citations        JSONB       NOT NULL DEFAULT '[]',
    confidence_score FLOAT,
    retrieval_hits   JSONB       NOT NULL DEFAULT '[]',
    generated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Review queue ──────────────────────────────────────────────────────────────

CREATE TABLE review_queue (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id           VARCHAR(50)  NOT NULL UNIQUE,
    query_id          VARCHAR(50)  REFERENCES queries(query_id),
    escalation_reason VARCHAR(100) NOT NULL,
    risk_category     VARCHAR(20)  NOT NULL,
    status            VARCHAR(50)  NOT NULL DEFAULT 'pending',
    reviewer_id       VARCHAR(100),
    reviewer_notes    TEXT,
    resolved_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_review_status ON review_queue(status);

-- ── Audit logs ────────────────────────────────────────────────────────────────

CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    log_id      VARCHAR(50)  NOT NULL UNIQUE,
    query_id    VARCHAR(50)  REFERENCES queries(query_id),
    event_type  VARCHAR(50)  NOT NULL,
    actor       VARCHAR(100) NOT NULL,
    input_data  JSONB        NOT NULL DEFAULT '{}',
    output_data JSONB        NOT NULL DEFAULT '{}',
    timestamp   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_query     ON audit_logs(query_id);
CREATE INDEX idx_audit_timestamp ON audit_logs(timestamp);
