package com.policyguard.eval;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Aggregated metrics produced by {@link EvalHarness#run()}.
 *
 * <p>PII redaction precision and recall are stubbed to {@code 1.0} pending
 * Presidio gold-truth labelling (out of scope for v1). See README § "Eval
 * Limitations" and the TODO in {@link EvalHarness}.
 *
 * <p>All fields are serialised with snake_case keys so the written
 * {@code target/eval-report.json} matches the spec schema.
 */
public record EvalReport(
        @JsonProperty("citation_precision")      double citationPrecision,
        @JsonProperty("retrieval_recall_at_5")   double retrievalRecallAt5,
        @JsonProperty("pii_redaction_precision") double piiRedactionPrecision,
        @JsonProperty("pii_redaction_recall")    double piiRedactionRecall,
        @JsonProperty("escalation_precision")    double escalationPrecision,
        @JsonProperty("escalation_recall")       double escalationRecall,
        @JsonProperty("refusal_rate")            double refusalRate,
        @JsonProperty("p95_latency_ms")          long   p95LatencyMs,
        @JsonProperty("total_queries")           int    totalQueries,
        @JsonProperty("answered")                int    answered,
        @JsonProperty("escalated")               int    escalated,
        @JsonProperty("refused")                 int    refused,
        @JsonProperty("details")                 List<EvalRecord> details
) {}
