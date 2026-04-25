# PolicyGuard

**Compliance RAG + PII Redaction Gateway** — a production-quality Spring Boot 3 / Java 21 service that
answers regulatory policy questions with cited evidence, redacts PII before it reaches an LLM, escalates
high-risk queries for human review, and refuses requests it cannot answer with confidence.

```
Query ──► PII Redaction ──► Risk Classifier ──► HybridRetriever ──► CitationGenerator
                                    │                                        │
                             [high risk]                              ConfidenceGate
                                    ▼                                 ┌─────┴─────┐
                             ReviewQueue                           pass         fail
                                    │                                │           │
                            (human resolve)                       Answer      Refusal
                                    └─────────────────────────────► AuditLog ◄──┘
```

---

## Features

| Capability | Detail |
|---|---|
| **Hybrid Retrieval** | BM25 full-text + pgvector cosine similarity, re-ranked by combined score |
| **PII Redaction** | Microsoft Presidio integration; redacted spans replaced before any LLM call |
| **Risk Classification** | Configurable regex patterns → escalation or direct answer path |
| **Citation Generation** | Spring AI `ChatClient`; response includes `[Doc: X, Para: Y]` citations |
| **Confidence Gate** | Configurable threshold; queries below gate return an explicit refusal |
| **Immutable Audit Log** | Every pipeline event written in `REQUIRES_NEW` transaction; append-only |
| **Human Review Workflow** | Escalated items queued; reviewer resolves with APPROVED / REJECTED decision |
| **Stub Profile** | Fully deterministic `StubChatModel` + `StubEmbeddingModel`; no external calls needed |
| **Eval Harness** | Offline eval against a gold query set; outputs JSON metrics report |
| **JMH Benchmarks** | End-to-end latency benchmarks on the stub profile |

---

## Architecture

See [`docs/architecture.md`](docs/architecture.md) for full Mermaid diagrams and component descriptions.

**High-level flow:**

1. `POST /api/query` → `PiiRedactionGateway` calls Presidio; redacts PII spans
2. `RiskClassifier` applies regex patterns; high-risk → `EscalationService` → `review_queue`
3. Low-risk → `HybridRetriever` (BM25 + pgvector) → top-K chunks
4. `CitationGenerator` calls `ChatClient` with chunks as context; extracts citation refs
5. `ConfidenceGate` compares max similarity score against `policyguard.confidence.threshold`
6. Above gate → `Answer` persisted; below gate → refusal response with `Answer(outcome=REFUSED)`
7. `AuditLogService` records every transition event (`REQUIRES_NEW` = always committed)

---

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Java 21 + Maven 3.9+
- (Optional) An OpenAI API key — not required when using the `stub` profile

### 1 — Start backing services

```bash
docker-compose up -d
# Starts: pgvector/pgvector:pg16, Redis 7, Presidio Analyzer
```

### 2 — Run the application

**Stub mode (no external LLM/embedding API needed):**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=stub
```

**With OpenAI:**

```bash
OPENAI_API_KEY=sk-... mvn spring-boot:run -Dspring-boot.run.profiles=openai
```

**With LM Studio (default local profile):**

```bash
mvn spring-boot:run
```

### 3 — Seed policy documents

```bash
mvn spring-boot:run \
    -Dspring-boot.run.profiles=stub \
    -Dspring-boot.run.arguments=--seed
```

This runs `DemoSeeder` which ingests all 8 synthetic policy fixtures idempotently.

### 4 — Submit a query

```bash
curl -s -X POST http://localhost:8080/api/query \
     -H "Content-Type: application/json" \
     -d '{"question":"How long must customer PII be retained after account closure?","userId":"demo-01"}' \
     | jq .
```

### 5 — Full end-to-end demo

```bash
# Start the app with seed flag first, then:
./scripts/demo.sh
```

---

## Provider Matrix

| Maven profile | `SPRING_PROFILES_ACTIVE` | Chat model | Embedding model | Presidio |
|---|---|---|---|---|
| *(default)* | `lmstudio` | LM Studio `http://host.docker.internal:1234/v1` | same | real |
| `-Popenrouter` | `openrouter` | OpenRouter `https://openrouter.ai/api/v1` | LM Studio | real |
| `-Popenai` | `openai` | OpenAI GPT-4o | text-embedding-3-small | real |
| `-Pstub` | `stub` | `StubChatModel` (deterministic) | `StubEmbeddingModel` (SHA-256 hash) | real |
| IT tests | `test,stub` | `StubChatModel` | `StubEmbeddingModel` | Mockito mock |

---

## API Reference

### Ingest a document

```
POST /api/ingest
Content-Type: multipart/form-data

file=<PDF binary>
metadata={"documentId":"gdpr-001","title":"GDPR Policy","docType":"REGULATION"}
```

### Submit a query

```
POST /api/query
Content-Type: application/json

{"question": "...", "userId": "user-123"}
```

**Response:**

```json
{
  "queryId": "qry-...",
  "outcome": "ANSWERED",
  "answerText": "Per the policy: ... [Doc: gdpr-001, Para: 2]",
  "citations": [{"documentId":"gdpr-001","paragraphRef":"2","excerpt":"..."}],
  "confidenceScore": 0.82
}
```

`outcome` is one of: `ANSWERED` | `REFUSED` | `ESCALATED`

### Review queue

```
GET  /api/review?status=PENDING
POST /api/review/{id}/resolve
     X-Reviewer-Id: reviewer-123
     {"reviewerId":"reviewer-123","decision":"APPROVED","notes":"..."}
```

### Audit log

```
GET /api/audit?queryId={queryId}
GET /api/audit?page=0&size=20
```

### Health

```
GET /actuator/health
```

---

## Testing

### Unit tests (default — no Docker required)

```bash
mvn test
```

`@Tag("it")` integration tests are automatically excluded by the Surefire configuration.

### Integration tests (requires Docker)

```bash
mvn -Pit verify
```

Runs 8 test classes (≈ 38 tests) via Testcontainers:

| Class | What it verifies |
|---|---|
| `DocumentIngestionIT` | DB row counts, embeddings stored, paragraph refs populated |
| `QueryPipelineIT` | Answered path: Answer row, audit chain, citation refs |
| `EscalationFlowIT` | Escalation path: retrieval skipped, review_queue populated |
| `RefusalBehaviorIT` | Refusal path: Answer(outcome=REFUSED), empty citations |
| `AuditCompletenessIT` | Monotonic timestamps, append-only invariant |
| `ReviewWorkflowIT` | Escalate → resolve(APPROVED) → answer persisted, reviewer actor |

Singleton containers (one PostgreSQL + one Redis) are shared across all IT contexts.

### Eval harness

```bash
mvn -DskipTests package -Pstub
java -cp target/policyguard-*.jar com.policyguard.eval.EvalRunner
cat target/eval-report.json | jq .
```

Reports: `citation_precision`, `retrieval_recall_at_5`, `escalation_precision`, `escalation_recall`,
`refusal_rate`, `p95_latency_ms`.

### JMH benchmarks

```bash
mvn -Pjmh -DskipTests package
mvn -Pjmh exec:java -Dexec.mainClass=com.policyguard.bench.JmhRunner
```

Config: `@Fork(1)`, `@Warmup(2×2s)`, `@Measurement(5×5s)`, `AverageTime / ms`.

---

## Key Configuration

```yaml
# src/main/resources/application.yml
policyguard:
  embedding:
    dim: 1536                      # must match your embedding model output dimension
  confidence:
    threshold: 0.65                # gate score; 0.0–1.0
  risk:
    patterns:
      policy_exception:            "\\b(exception|waiver|override|bypass)\\b.*\\b(policy|control|requirement)\\b"
      customer_data_exposure:      "\\b(customer|client)\\b.*\\b(data|PII)\\b.*\\b(access|share|disclose)\\b"
      regulatory_interpretation:   "\\b(FINRA|SEC|GDPR|HIPAA|SOX|PCI)\\b.*\\b(interpret|apply|compliance)\\b"
```

See `.env.example` for Docker Compose overrides (database URL, passwords, Presidio image).

---

## Limitations

- **Presidio language support** — analyzer is configured for English only; multi-language PII detection requires additional Presidio configuration.
- **PDF extraction** — uses Apache PDFBox; scanned/image-only PDFs will produce empty text.
- **Embedding dimension** — `policyguard.embedding.dim` must match the configured model; changing it after data is ingested requires a re-index.
- **Stub similarity** — `StubEmbeddingModel` produces ≈0.75 cosine similarity for any pair of non-identical texts; not representative of production retrieval quality.
- **PII eval metrics** — `pii_redaction_precision` / `pii_redaction_recall` are stubbed to `1.0` in the eval harness pending Presidio gold-truth labelling.

---

## Project Structure

```
policyguard/
├── docs/
│   └── architecture.md           # Mermaid request/ingestion flow diagrams
├── scripts/
│   └── demo.sh                   # End-to-end demo script
├── src/
│   ├── jmh/java/                 # JMH benchmark sources (-Pjmh)
│   └── main/
│       ├── java/com/policyguard/
│       │   ├── config/           # AiConfig, RedisConfig, SecurityConfig
│       │   ├── controller/       # QueryController, ReviewController, IngestionController
│       │   ├── demo/             # DemoSeeder (--seed flag)
│       │   ├── domain/           # JPA entities: Document, DocumentChunk, Query, Answer, …
│       │   ├── eval/             # EvalRunner, EvalMetrics, GoldSet
│       │   ├── fixtures/         # PolicyFixtureFactory (8 synthetic docs)
│       │   ├── repository/       # Spring Data JPA repositories
│       │   ├── service/
│       │   │   ├── audit/        # AuditLogService (REQUIRES_NEW)
│       │   │   ├── chunking/     # ChunkingService
│       │   │   ├── citation/     # CitationGenerator
│       │   │   ├── gate/         # ConfidenceGate
│       │   │   ├── ingestion/    # DocumentIngestionService
│       │   │   ├── pii/          # PiiRedactionGateway, PresidioClient
│       │   │   ├── query/        # QueryService (pipeline orchestrator)
│       │   │   ├── retrieval/    # HybridRetriever
│       │   │   ├── review/       # ReviewService
│       │   │   └── risk/         # RiskClassifier
│       │   └── type/             # Enums: Outcome, RiskLevel, ReviewDecision, …
│       └── resources/
│           ├── application.yml
│           ├── application-stub.yml
│           ├── application-test.yml
│           └── db/migration/     # Flyway scripts (V1–V4)
│   └── test/
│       └── java/com/policyguard/
│           ├── it/               # Testcontainers IT suite (Phase 18)
│           │   ├── BaseIT.java
│           │   ├── PresidioStubConfig.java
│           │   ├── IngestionFixturesHelper.java
│           │   ├── DocumentIngestionIT.java
│           │   ├── QueryPipelineIT.java
│           │   ├── EscalationFlowIT.java
│           │   ├── RefusalBehaviorIT.java
│           │   ├── AuditCompletenessIT.java
│           │   └── ReviewWorkflowIT.java
│           └── unit/             # Fast unit tests (no Docker)
├── docker-compose.yml
├── pom.xml
└── README.md
```

