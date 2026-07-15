# PolicyGuard

**Compliance RAG + PII Redaction Gateway** — a production-style FastAPI / Python service that
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

## Features

| Capability | Detail |
|---|---|
| **Hybrid Retrieval** | Postgres FTS + pgvector cosine similarity, fused with RRF |
| **PII Redaction** | Microsoft Presidio integration; placeholders before any LLM call |
| **Risk Classification** | Configurable regex patterns → escalation or direct answer path |
| **Citation Generation** | OpenAI-compatible chat; `[Doc: X, Para: Y]` citations |
| **Confidence Gate** | Mean of top-3 hit scores; refuse below threshold / unverifiable cites |
| **Immutable Audit Log** | Append-only pipeline events |
| **Human Review Workflow** | Escalate → approve / reject / override |
| **Stub Profile** | Deterministic stub chat + SHA-256 embeddings (no external calls) |
| **Eval Harness** | Offline eval against a gold query set → JSON metrics report |

## Quick Start

### Prerequisites

- Python 3.11+
- Docker & Docker Compose (Postgres/pgvector, Redis, Presidio)

### 1 — Start backing services

```bash
docker compose up -d
```

### 2 — Install

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
cp .env.example .env
```

### 3 — Migrate / init schema

```bash
export POLICYGUARD_PROFILE=stub
alembic upgrade head
# or first-run auto init via the app / seed command
```

### 4 — Seed fixtures & run

```bash
POLICYGUARD_PROFILE=stub policyguard-seed
POLICYGUARD_PROFILE=stub uvicorn policyguard.main:app --host 0.0.0.0 --port 8080
```

### 5 — Query

```bash
curl -s -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{"question":"How long must customer PII be retained after account closure?","userId":"demo-01"}' \
  | python3 -m json.tool
```

### 6 — Demo script

```bash
./scripts/demo.sh
```

### 7 — Eval

```bash
POLICYGUARD_PROFILE=stub policyguard-eval -o target/eval-report.json
```

## Provider profiles

| `POLICYGUARD_PROFILE` | Chat | Embeddings | Presidio |
|---|---|---|---|
| `stub` (default for tests) | deterministic stub | SHA-256 L2 vectors | stubbed (no HTTP) |
| `lmstudio` | local OpenAI-compatible | local | real |
| `openrouter` | OpenRouter | local/override | real |

## API

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/query` | 200 answered/refused, 202 escalated |
| `POST` | `/api/documents` | multipart PDF ingest |
| `GET` | `/api/review-queue` | pending items |
| `POST` | `/api/review/{itemId}/resolve` | header `X-Reviewer-Id` |
| `GET` | `/api/audit/{queryId}` | chronological events |
| `GET` | `/health` | liveness |

## Architecture

See [`docs/architecture.md`](docs/architecture.md).

## Tests

```bash
pytest tests/unit -q
pytest tests/it -m it -q   # requires Docker
```

## License

MIT
