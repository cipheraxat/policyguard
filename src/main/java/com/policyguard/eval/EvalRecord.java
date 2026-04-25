package com.policyguard.eval;

import com.policyguard.fixtures.GoldQuery;
import com.policyguard.service.query.QueryOutcome;

/**
 * Captures the per-query result of one evaluation run, pairing the expected
 * gold-set entry with the actual pipeline outcome and derived boolean signals.
 */
public record EvalRecord(
        GoldQuery expected,
        QueryOutcome actual,
        long latencyMs,
        boolean citationCorrect,
        boolean retrievalRecallAt5,
        String notes
) {}
