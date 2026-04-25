package com.policyguard.service.citation;

/**
 * A single paragraph-level citation extracted from the LLM response.
 *
 * <p>{@code chunkId} is {@code null} when the citation referenced a
 * {@code [Doc: X, Para: Y]} tag that could not be matched against any of the
 * actual retrieved chunks.  The {@link com.policyguard.service.gate.ConfidenceGate}
 * treats an unverifiable citation (null chunkId) as a signal to REFUSE.
 */
public record Citation(
        String chunkId,
        String documentId,
        String paragraphRef,
        String textSnippet
) {}
