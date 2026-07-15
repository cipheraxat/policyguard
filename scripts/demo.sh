#!/usr/bin/env bash
set -euo pipefail
BASE="${BASE_URL:-http://localhost:8080}"

echo "== health =="
curl -sf "$BASE/health" | python3 -m json.tool

echo "== query (retention) =="
curl -sf -X POST "$BASE/api/query" \
  -H "Content-Type: application/json" \
  -d '{"question":"How long must customer PII be retained after account closure?","userId":"demo-01"}' \
  | python3 -m json.tool

echo "== query (escalation) =="
curl -sf -X POST "$BASE/api/query" \
  -H "Content-Type: application/json" \
  -d '{"question":"Can we request a waiver of the GDPR policy for a VIP customer?","userId":"demo-01"}' \
  | python3 -m json.tool || true

echo "== review queue =="
curl -sf "$BASE/api/review-queue" | python3 -m json.tool

echo "Demo complete."
