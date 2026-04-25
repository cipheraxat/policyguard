package com.policyguard.service.ingestion;

/**
 * Summary returned by {@link DocumentIngestionService#ingest}.
 *
 * @param documentId          the persisted document identifier
 * @param chunksCreated       number of chunks embedded and stored
 * @param piiEntitiesRedacted number of PII entities replaced during redaction
 */
public record IngestionResult(
        String documentId,
        int chunksCreated,
        int piiEntitiesRedacted
) {}
