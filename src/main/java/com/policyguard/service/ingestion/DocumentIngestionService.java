package com.policyguard.service.ingestion;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.policyguard.domain.Document;
import com.policyguard.domain.DocumentChunk;
import com.policyguard.repository.DocumentRepository;
import com.policyguard.repository.DocumentChunkRepository;
import com.policyguard.service.chunking.Chunk;
import com.policyguard.service.chunking.ChunkingService;
import com.policyguard.service.pii.PiiRedactionGateway;
import com.policyguard.service.pii.RedactionResult;

/**
 * Orchestrates the full document ingestion pipeline:
 * PDF extraction → PII redaction → chunking → batch embedding → persistence.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final PdfExtractionService pdfExtractor;
    private final PiiRedactionGateway piiGateway;
    private final ChunkingService chunkingService;
    private final EmbeddingModel embeddingModel;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DataSource dataSource;

    public DocumentIngestionService(
            PdfExtractionService pdfExtractor,
            PiiRedactionGateway piiGateway,
            ChunkingService chunkingService,
            EmbeddingModel embeddingModel,
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            DataSource dataSource) {
        this.pdfExtractor = pdfExtractor;
        this.piiGateway = piiGateway;
        this.chunkingService = chunkingService;
        this.embeddingModel = embeddingModel;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.dataSource = dataSource;
    }

    /**
     * Ingest a PDF document: extract, redact PII, chunk, embed, and persist.
     *
     * @param file     the uploaded PDF
     * @param metadata optional metadata map (may contain {@code documentId}, {@code title},
     *                 {@code docType})
     * @return ingestion summary
     */
    @Transactional
    public IngestionResult ingest(MultipartFile file, Map<String, Object> metadata) throws IOException {
        // 1. Extract text
        byte[] pdfBytes = file.getBytes();
        String rawText = pdfExtractor.extract(pdfBytes);

        // 2. Redact PII
        RedactionResult redaction = piiGateway.redact(rawText);
        String redactedText = redaction.redactedText();
        int piiCount = redaction.entitiesFound().size();

        // 3. Resolve document identity
        String documentId = resolveDocumentId(metadata);
        String title = resolveTitle(file.getOriginalFilename(), metadata);
        String docType = metadata != null && metadata.containsKey("docType")
                ? String.valueOf(metadata.get("docType")) : "policy";

        // 4. Persist Document
        Document doc = new Document();
        doc.setDocumentId(documentId);
        doc.setTitle(title);
        doc.setDocType(docType);
        doc.setSourcePath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown");
        doc.setContentRaw(redactedText);
        doc.setMetadata(metadata);
        documentRepository.save(doc);

        // 5. Chunk
        List<Chunk> chunks = chunkingService.chunk(redactedText);

        // 6. Batch embed
        List<String> texts = chunks.stream().map(Chunk::text).toList();
        List<float[]> embeddings = batchEmbed(texts);

        // 7. Persist chunks
        List<DocumentChunk> chunkEntities = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            DocumentChunk entity = new DocumentChunk();
            entity.setChunkId(documentId + "-c" + i);
            entity.setDocumentId(documentId);
            entity.setParagraphRef(chunk.paragraphRef());
            entity.setText(chunk.text());
            entity.setEmbedding(embeddings.get(i));
            chunkEntities.add(entity);
        }
        chunkRepository.saveAll(chunkEntities);

        // 8. ANALYZE (best-effort, outside transaction via direct DataSource connection)
        analyzeChunksTable();

        return new IngestionResult(documentId, chunks.size(), piiCount);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<float[]> batchEmbed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(texts, null));
        return response.getResults().stream()
                .map(e -> e.getOutput())
                .toList();
    }

    private static String resolveDocumentId(Map<String, Object> metadata) {
        if (metadata != null && metadata.containsKey("documentId")) {
            return String.valueOf(metadata.get("documentId"));
        }
        return "DOC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private static String resolveTitle(String filename, Map<String, Object> metadata) {
        if (metadata != null && metadata.containsKey("title")) {
            return String.valueOf(metadata.get("title"));
        }
        if (filename != null && !filename.isBlank()) {
            int dot = filename.lastIndexOf('.');
            return dot > 0 ? filename.substring(0, dot) : filename;
        }
        return "Untitled";
    }

    /** Runs {@code ANALYZE document_chunks} outside any active transaction. */
    private void analyzeChunksTable() {
        try (Connection conn = dataSource.getConnection()) {
            boolean prev = conn.getAutoCommit();
            conn.setAutoCommit(true);
            try {
                conn.createStatement().execute("ANALYZE document_chunks");
            } finally {
                conn.setAutoCommit(prev);
            }
        } catch (SQLException e) {
            log.warn("ANALYZE document_chunks failed (best-effort): {}", e.getMessage());
        }
    }
}
