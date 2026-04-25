package com.policyguard.api.documents;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyguard.service.ingestion.DocumentIngestionService;
import com.policyguard.service.ingestion.IngestionResult;

/**
 * REST endpoint for document ingestion.
 *
 * <p>{@code POST /api/documents} accepts a multipart upload with:
 * <ul>
 *   <li>{@code file} — the PDF file</li>
 *   <li>{@code metadata} — optional JSON string with {@code documentId}, {@code title},
 *       {@code docType}</li>
 * </ul>
 * Returns {@code 202 Accepted} with an {@link IngestionResponse}.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService ingestionService;
    private final ObjectMapper objectMapper;

    public DocumentController(DocumentIngestionService ingestionService, ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<IngestionResponse> ingest(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "metadata", required = false) String metadataJson) {
        try {
            Map<String, Object> metadata = parseMetadata(metadataJson);
            IngestionResult result = ingestionService.ingest(file, metadata);

            IngestionResponse response = new IngestionResponse(
                    result.documentId(),
                    "processing",
                    result.chunksCreated(),
                    result.piiEntitiesRedacted());

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (Exception e) {
            throw new RuntimeException("Ingestion failed: " + e.getMessage(), e);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> parseMetadata(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(json, new TypeReference<>() {});
    }
}
