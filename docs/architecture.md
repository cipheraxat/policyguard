# Architecture

Python / FastAPI rewrite of PolicyGuard. Controllers map to FastAPI routes under `policyguard.api.routes`;
Spring `ChatClient` / `StubChatModel` are replaced by OpenAI-compatible and stub providers in `policyguard.providers`.

## Request Flow

```mermaid
flowchart TD
    Client([Client]) -->|POST /api/query| QC[QueryController]
    QC --> PII[PiiRedactionGateway\nPresidioClient]
    PII --> RC[RiskClassifier\nregex patterns]

    RC -->|low risk| RET[HybridRetriever\nBM25 + pgvector]
    RC -->|high risk| ESC[EscalationService\nReviewQueue]
    ESC -->|PENDING| RQ[(review_queue)]
    ESC -->|escalated response| Client

    RET --> CG[CitationGenerator\nChatClient / StubChatModel]
    CG --> GATE[ConfidenceGate\nthreshold check]

    GATE -->|score ≥ threshold| ANS[AnswerService\npersist Answer]
    GATE -->|score < threshold| REF[Refusal response\npersist Answer]

    ANS --> AUDIT[AuditLogService\nREQUIRES_NEW]
    REF --> AUDIT
    AUDIT --> AL[(audit_logs)]

    ANS --> Client
    REF --> Client

    subgraph Persistence
        DOC[(documents)]
        CHUNK[(document_chunks\n+ pgvector embeddings)]
        QR[(queries)]
        ANS_T[(answers)]
        RQ
        AL
    end

    DOC --> CHUNK
    QC --> QR
    ANS --> ANS_T
```

## Component Descriptions

| Component | Class | Responsibility |
|---|---|---|
| `QueryController` | `com.policyguard.controller.QueryController` | Accepts query, orchestrates pipeline |
| `PiiRedactionGateway` | `com.policyguard.service.pii.PiiRedactionGateway` | Calls Presidio; redacts PII spans from question |
| `RiskClassifier` | `com.policyguard.service.risk.RiskClassifier` | Regex-based routing: answered vs escalated |
| `HybridRetriever` | `com.policyguard.service.retrieval.HybridRetriever` | Combines BM25 (full-text) + cosine similarity (pgvector) |
| `CitationGenerator` | `com.policyguard.service.citation.CitationGenerator` | Calls ChatClient with retrieved chunks; formats citations |
| `ConfidenceGate` | `com.policyguard.service.confidence.ConfidenceGate` | Compares max chunk score against configured threshold |
| `EscalationService` | `com.policyguard.service.escalation.EscalationService` | Persists `review_queue` row; returns escalated response |
| `AuditLogService` | `com.policyguard.service.audit.AuditLogService` | Appends immutable audit events (REQUIRES_NEW) |
| `ReviewController` | `com.policyguard.controller.ReviewController` | Lists queue; resolves with APPROVED/REJECTED decision |

## Ingestion Flow

```mermaid
flowchart LR
    Upload([Multipart PDF]) --> ING[DocumentIngestionService]
    ING -->|extract text| PDF[Apache PDFBox]
    PDF --> CHUNK_SVC[ChunkingService\nfixed-size + overlap]
    CHUNK_SVC --> EMB[EmbeddingModel\nOpenAI / StubEmbeddingModel]
    EMB --> PGVEC[(pgvector\ndocument_chunks)]
    ING -->|save doc| DOCREPO[(documents)]
```

## Profile Matrix

| Profile | ChatModel | EmbeddingModel | PresidioClient |
|---|---|---|---|
| *(none / prod)* | OpenAI GPT-4o | OpenAI text-embedding-3-small | Real HTTP call |
| `stub` | `StubChatModel` | `StubEmbeddingModel` | Real HTTP call |
| `test` + `stub` | `StubChatModel` | `StubEmbeddingModel` | Mockito `@Primary` mock |

## Stub Embedding Similarity

`StubEmbeddingModel` hashes text via SHA-256 → L2-normalised `float[1536]` with all-positive values.
Expected cosine similarity between any two stub embeddings ≈ **0.75**.

- Default threshold (`policyguard.confidence.threshold`) = **0.65** → stub scores **pass** the gate
- IT "refused" tests override threshold to **0.99** → stub scores **fail** the gate

## Database Schema (simplified)

```
documents          (id, document_id, title, doc_type, …)
document_chunks    (id, document_id FK, paragraph_ref, content, embedding vector(1536))
queries            (id, query_id, user_id, question, redacted_question, risk_level, …)
answers            (id, query_id FK, answer_text, citations jsonb, confidence_score, outcome, …)
review_queue       (id, query_id FK, reviewer_id, decision, status, …)
audit_logs         (id, query_id FK, event_type, actor, payload jsonb, occurred_at, …)
```
