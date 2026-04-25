package com.policyguard.fixtures;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single entry in the evaluation gold set, describing one test question and
 * its expected pipeline outcome (answered / escalated / refused).
 */
public record GoldQuery(
        @JsonProperty("query_id")               String queryId,
        @JsonProperty("question")               String question,
        @JsonProperty("expected_status")        String expectedStatus,
        @JsonProperty("expected_citation_doc")  String expectedCitationDoc,
        @JsonProperty("expected_citation_para") String expectedCitationPara,
        @JsonProperty("expected_escalation_reason") String expectedEscalationReason,
        @JsonProperty("expected_reason")        String expectedReason,
        @JsonProperty("risk_category")          String riskCategory
) {}
