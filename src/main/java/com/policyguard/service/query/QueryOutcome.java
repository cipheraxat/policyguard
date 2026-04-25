package com.policyguard.service.query;

import java.util.List;

import com.policyguard.service.citation.Citation;

/**
 * Sealed outcome type returned by {@link QueryService#handle}.
 * The controller maps each variant to its HTTP response DTO + status code.
 */
public sealed interface QueryOutcome
        permits QueryOutcome.Answered, QueryOutcome.Escalated, QueryOutcome.Refused {

    record Answered(
            String queryId,
            String answer,
            List<Citation> citations,
            double confidenceScore,
            int retrievalHitsCount
    ) implements QueryOutcome {}

    record Escalated(
            String queryId,
            String reason,
            String reviewItemId,
            String message
    ) implements QueryOutcome {}

    record Refused(
            String queryId,
            String reason,
            String message
    ) implements QueryOutcome {}
}
