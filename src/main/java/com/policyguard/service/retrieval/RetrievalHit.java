package com.policyguard.service.retrieval;

import java.util.Map;

/**
 * Represents a single chunk retrieved from the hybrid search pipeline.
 *
 * <p>The {@code score} field carries the <em>semantic cosine similarity</em>
 * (1 − cosine_distance, range [0, 1]) when the hit was found by the vector
 * search leg.  For hits that surfaced <em>only</em> via full-text search (FTS),
 * the score is set to the Reciprocal Rank Fusion (RRF) score so that downstream
 * confidence calculations always have a non-null numeric quality signal.
 */
public record RetrievalHit(
        String chunkId,
        String documentId,
        String paragraphRef,
        String text,
        double score,
        Map<String, Object> metadata
) {}
