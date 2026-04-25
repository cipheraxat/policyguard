package com.policyguard.api.documents;

/**
 * Response body returned by {@code POST /api/documents}.
 *
 * @param documentId          the assigned document identifier
 * @param status              processing status (always {@code "processing"})
 * @param chunksCreated       number of chunks embedded and stored
 * @param piiEntitiesRedacted number of PII entities replaced during redaction
 */
public record IngestionResponse(
        String documentId,
        String status,
        int chunksCreated,
        int piiEntitiesRedacted
) {}
