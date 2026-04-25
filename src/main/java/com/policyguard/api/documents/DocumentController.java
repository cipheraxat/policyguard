package com.policyguard.api.documents;

import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyguard.config.PolicyguardProperties;
import com.policyguard.service.ingestion.DocumentIngestionService;
import com.policyguard.service.ingestion.IngestionResult;

/**
 * REST endpoint for document ingestion.
 *
 * <p>{@code POST /api/documents} accepts a multipart upload with:
 * <ul>
 *   <li>{@code file} — the PDF file (validated: content-type {@code application/pdf}
 *       or {@code .pdf} extension; size capped at
 *       {@code policyguard.ingestion.max-file-bytes})</li>
 *   <li>{@code metadata} — optional JSON string with {@code documentId}, {@code title},
 *       {@code docType}</li>
 * </ul>
 * Returns {@code 202 Accepted} with an {@link IngestionResponse}.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentIngestionService ingestionService;
    private final ObjectMapper objectMapper;
    private final PolicyguardProperties properties;

    public DocumentController(DocumentIngestionService ingestionService,
                              ObjectMapper objectMapper,
                              PolicyguardProperties properties) {
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<?> ingest(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "metadata", required = false) String metadataJson) throws java.io.IOException {

        // ── Multipart validation ─────────────────────────────────────────────
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "file is empty"));
        }
        long maxBytes = properties.getIngestion().getMaxFileBytes();
        if (file.getSize() > maxBytes) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of(
                            "error", "file exceeds maximum size",
                            "max_bytes", maxBytes,
                            "received_bytes", file.getSize()));
        }
        if (!isPdf(file)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(Map.of(
                            "error", "only application/pdf uploads are accepted",
                            "received_content_type", String.valueOf(file.getContentType())));
        }

        Map<String, Object> metadata;
        try {
            metadata = parseMetadata(metadataJson);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "invalid metadata JSON: " + e.getOriginalMessage()));
        }

        IngestionResult result = ingestionService.ingest(file, metadata);

        IngestionResponse response = new IngestionResponse(
                result.documentId(),
                "processing",
                result.chunksCreated(),
                result.piiEntitiesRedacted());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleIngestionFailure(Exception ex) {
        log.error("Document ingestion failed", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "ingestion failed: " + ex.getClass().getSimpleName()));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private boolean isPdf(MultipartFile file) {
        String ct = file.getContentType();
        if (MediaType.APPLICATION_PDF_VALUE.equals(ct)) {
            return true;
        }
        String name = file.getOriginalFilename();
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private Map<String, Object> parseMetadata(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(json, new TypeReference<>() {});
    }
}
