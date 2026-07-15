FROM python:3.12-slim AS builder
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends build-essential && rm -rf /var/lib/apt/lists/
COPY pyproject.toml README.md ./
COPY src ./src
COPY alembic ./alembic
COPY alembic.ini ./
COPY data ./data
RUN pip install --no-cache-dir .

FROM python:3.12-slim
WORKDIR /app
ENV POLICYGUARD_PROFILE=stub \
    PYTHONUNBUFFERED=1
COPY --from=builder /usr/local /usr/local
COPY --from=builder /app /app
EXPOSE 8080
CMD ["uvicorn", "policyguard.main:app", "--host", "0.0.0.0", "--port", "8080"]
