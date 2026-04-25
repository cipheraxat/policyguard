package com.policyguard.service.citation;

import java.util.List;

import com.policyguard.service.retrieval.RetrievalHit;

/**
 * The result produced by {@link CitationGenerator}: the raw LLM response text,
 * the list of parsed (and optionally verified) citations, the computed
 * confidence score, and the underlying retrieval hits that were used as context.
 */
public record CitationResult(
        String responseText,
        List<Citation> citations,
        double confidence,
        List<RetrievalHit> hits
) {}
