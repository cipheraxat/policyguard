"""Initial PolicyGuard schema (ported from Flyway V1 + override_answer)."""

from alembic import op

revision = "0001_init"
down_revision = None
branch_labels = None
depends_on = None

EMBEDDING_DIM = 1536


def upgrade() -> None:
    op.execute("CREATE EXTENSION IF NOT EXISTS vector")
    op.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto")
    op.execute(
        f"""
        CREATE TABLE IF NOT EXISTS documents (
            id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            document_id VARCHAR(50)  NOT NULL UNIQUE,
            title       TEXT         NOT NULL,
            doc_type    VARCHAR(50)  NOT NULL,
            source_path TEXT         NOT NULL,
            content_raw TEXT         NOT NULL,
            metadata    JSONB        NOT NULL DEFAULT '{{}}',
            created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
        );

        CREATE TABLE IF NOT EXISTS document_chunks (
            id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            chunk_id      VARCHAR(50)              NOT NULL UNIQUE,
            document_id   VARCHAR(50)              REFERENCES documents(document_id),
            paragraph_ref TEXT                     NOT NULL,
            text          TEXT                     NOT NULL,
            embedding     VECTOR({EMBEDDING_DIM}),
            metadata      JSONB NOT NULL DEFAULT '{{}}'
        );

        CREATE INDEX IF NOT EXISTS idx_chunks_document ON document_chunks(document_id);

        CREATE INDEX IF NOT EXISTS idx_chunks_fts ON document_chunks
            USING GIN (to_tsvector('english', text));

        CREATE TABLE IF NOT EXISTS queries (
            id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            query_id        VARCHAR(50)  NOT NULL UNIQUE,
            original_prompt TEXT         NOT NULL,
            redacted        BOOLEAN      NOT NULL DEFAULT FALSE,
            redaction_log   JSONB        NOT NULL DEFAULT '{{}}',
            status          VARCHAR(50)  NOT NULL,
            created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
        );

        CREATE TABLE IF NOT EXISTS answers (
            id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            query_id         VARCHAR(50) REFERENCES queries(query_id),
            response_text    TEXT        NOT NULL,
            citations        JSONB       NOT NULL DEFAULT '[]',
            confidence_score FLOAT,
            retrieval_hits   JSONB       NOT NULL DEFAULT '[]',
            generated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );

        CREATE TABLE IF NOT EXISTS review_queue (
            id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            item_id           VARCHAR(50)  NOT NULL UNIQUE,
            query_id          VARCHAR(50)  REFERENCES queries(query_id),
            escalation_reason VARCHAR(100) NOT NULL,
            risk_category     VARCHAR(20)  NOT NULL,
            status            VARCHAR(50)  NOT NULL DEFAULT 'pending',
            reviewer_id       VARCHAR(100),
            reviewer_notes    TEXT,
            override_answer   TEXT,
            resolved_at       TIMESTAMPTZ,
            created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
        );

        CREATE INDEX IF NOT EXISTS idx_review_status ON review_queue(status);

        CREATE TABLE IF NOT EXISTS audit_logs (
            id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            log_id      VARCHAR(50)  NOT NULL UNIQUE,
            query_id    VARCHAR(50)  REFERENCES queries(query_id),
            event_type  VARCHAR(50)  NOT NULL,
            actor       VARCHAR(100) NOT NULL,
            input_data  JSONB        NOT NULL DEFAULT '{{}}',
            output_data JSONB        NOT NULL DEFAULT '{{}}',
            timestamp   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
        );

        CREATE INDEX IF NOT EXISTS idx_audit_query     ON audit_logs(query_id);
        CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_logs(timestamp);
        """
    )


def downgrade() -> None:
    op.execute(
        """
        DROP TABLE IF EXISTS audit_logs CASCADE;
        DROP TABLE IF EXISTS review_queue CASCADE;
        DROP TABLE IF EXISTS answers CASCADE;
        DROP TABLE IF EXISTS queries CASCADE;
        DROP TABLE IF EXISTS document_chunks CASCADE;
        DROP TABLE IF EXISTS documents CASCADE;
        """
    )
