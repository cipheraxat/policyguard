#!/usr/bin/env bash
# scripts/demo.sh — end-to-end PolicyGuard demo
# Usage: ./scripts/demo.sh
# Requires: Docker, Java 21, Maven, jq

set -euo pipefail

BASE_URL="${POLICYGUARD_URL:-http://localhost:8080}"
REVIEWER_ID="demo-reviewer-01"

# ── helpers ──────────────────────────────────────────────────────────────────
info()  { printf '\033[0;34m[INFO]\033[0m  %s\n' "$*"; }
ok()    { printf '\033[0;32m[OK]\033[0m    %s\n' "$*"; }
err()   { printf '\033[0;31m[ERROR]\033[0m %s\n' "$*" >&2; exit 1; }
sep()   { printf '\n%s\n\n' "────────────────────────────────────────────────────"; }

wait_healthy() {
    local max=60
    info "Waiting for PolicyGuard to become healthy (max ${max}s)…"
    for i in $(seq 1 "$max"); do
        if curl -sf "${BASE_URL}/actuator/health" | grep -q '"status":"UP"'; then
            ok "Server is UP"
            return 0
        fi
        sleep 1
    done
    err "Server did not become healthy after ${max}s"
}

post_query() {
    local description="$1" question="$2" user_id="$3"
    printf '\n>> Query: %s\n' "$description"
    curl -sf -X POST "${BASE_URL}/api/query" \
        -H "Content-Type: application/json" \
        -d "{\"question\":\"${question}\",\"userId\":\"${user_id}\"}" | jq .
}

# ── 1. Health check ───────────────────────────────────────────────────────────
sep
info "Step 1 — Health check"
wait_healthy

# ── 2. Seed fixtures ──────────────────────────────────────────────────────────
sep
info "Step 2 — Seed policy documents (idempotent)"
# The app must already be running with --seed flag, or call seed endpoint if exposed.
# This script assumes the app was started with: mvn spring-boot:run -Dspring-boot.run.arguments=--seed
ok "Seed triggered via ApplicationRunner (--seed flag on startup)"

# ── 3. Submit queries ─────────────────────────────────────────────────────────
sep
info "Step 3 — Submit policy queries"

# Answered query
post_query \
    "PII retention policy (should be answered)" \
    "How long must customer PII be retained after account closure?" \
    "demo-user-01"

# Escalated query (matches policy_exception risk pattern)
post_query \
    "GDPR waiver request (should escalate to review)" \
    "Can we request a waiver of the GDPR policy for a VIP customer?" \
    "demo-user-02"

# Refused query (no matching policy content)
post_query \
    "Antarctica refund policy (should be refused)" \
    "What is the refund policy for employees in Antarctica?" \
    "demo-user-03"

# Customer data question
post_query \
    "Customer data access (may escalate or answer)" \
    "Who can access customer PII data for compliance audits?" \
    "demo-user-04"

# ── 4. List review queue ──────────────────────────────────────────────────────
sep
info "Step 4 — List pending review queue items"
QUEUE=$(curl -sf "${BASE_URL}/api/review?status=PENDING")
echo "$QUEUE" | jq .

PENDING_ID=$(echo "$QUEUE" | jq -r 'if type=="array" then .[0].id else .id end // empty' 2>/dev/null || true)

# ── 5. Resolve a pending review item ─────────────────────────────────────────
if [ -n "${PENDING_ID:-}" ]; then
    sep
    info "Step 5 — Resolve review item ${PENDING_ID} (APPROVED)"
    curl -sf -X POST "${BASE_URL}/api/review/${PENDING_ID}/resolve" \
        -H "Content-Type: application/json" \
        -H "X-Reviewer-Id: ${REVIEWER_ID}" \
        -d "{\"reviewerId\":\"${REVIEWER_ID}\",\"decision\":\"APPROVED\",\"notes\":\"Verified by compliance team\"}" | jq .
    ok "Review item ${PENDING_ID} resolved"
else
    info "Step 5 — No pending review items found (nothing to resolve)"
fi

# ── 6. Fetch audit log for one query ─────────────────────────────────────────
sep
info "Step 6 — Sample audit log (first query)"
FIRST_QUERY_ID=$(curl -sf "${BASE_URL}/api/audit?page=0&size=1" | jq -r '.[0].queryId // empty' 2>/dev/null || true)
if [ -n "${FIRST_QUERY_ID:-}" ]; then
    curl -sf "${BASE_URL}/api/audit?queryId=${FIRST_QUERY_ID}" | jq .
fi

sep
ok "Demo complete!"
