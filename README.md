# PolicyGuard

Compliance RAG + PII Redaction Gateway — Spring Boot 3 service.

> **Status:** Phases 1–3 implemented (scaffold, persistence, AI provider wiring).
> Full README with architecture diagram, curl examples, and demo script coming in Phase 19.

## Quick start

```bash
docker-compose up -d          # start Postgres+pgvector, Redis, Presidio
mvn spring-boot:run           # default: lmstudio profile
# OR
mvn spring-boot:run -Dspring-boot.run.profiles=stub   # no external LLM needed
```

## Provider profiles

| Profile | Chat | Embedding |
|---------|------|-----------|
| `lmstudio` (default) | `http://host.docker.internal:1234/v1` | same |
| `openrouter` | `https://openrouter.ai/api/v1` | LMStudio |
| `openai` | `https://api.openai.com/v1` | same |
| `stub` | deterministic stub | deterministic hash |

## Key configuration

```yaml
policyguard:
  embedding:
    dim: 1536    # must match your embedding model output dimension
  confidence:
    threshold: 0.65
```

See `src/main/resources/application.yml` for full defaults and `.env.example` for Docker overrides.
