package com.policyguard.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.policyguard.repository.DocumentChunkRepository;
import com.policyguard.repository.DocumentRepository;
import com.policyguard.service.ingestion.DocumentIngestionService;
import com.policyguard.service.ingestion.IngestionResult;

/**
 * Verifies the full ingestion pipeline against a real Postgres+pgvector container.
 *
 * <p>This test class owns the DB clean-up: it deletes all rows before ingesting
 * its 8 fixtures so it can assert exact counts regardless of execution order.
 */
@Tag("it")
@TestInstance(Lifecycle.PER_CLASS)
class DocumentIngestionIT extends BaseIT {

    @Autowired DocumentIngestionService ingestionService;
    @Autowired DocumentRepository       documentRepository;
    @Autowired DocumentChunkRepository  chunkRepository;
    @Autowired JdbcTemplate             jdbc;

    private List<IngestionResult> results;

    @BeforeAll
    void ingestFixtures() throws IOException {
        // Clean slate — delete FK-ordered
        jdbc.execute("DELETE FROM audit_logs");
        jdbc.execute("DELETE FROM answers");
        jdbc.execute("DELETE FROM review_queue");
        jdbc.execute("DELETE FROM queries");
        jdbc.execute("DELETE FROM document_chunks");
        jdbc.execute("DELETE FROM documents");

        results = IngestionFixturesHelper.ingestAll(ingestionService, documentRepository);
    }

    @Test
    void eightDocumentsPersisted() {
        assertThat(documentRepository.count()).isEqualTo(8);
    }

    @Test
    void chunksExceedDocumentCount() {
        long total = chunkRepository.count();
        // Each doc has multiple pages / sections → expect many chunks
        assertThat(total).isGreaterThan(8);
    }

    @Test
    void eachChunkHasNonNullEmbedding() {
        chunkRepository.findAll().forEach(chunk -> {
            assertThat(chunk.getEmbedding())
                    .as("embedding for chunk %s", chunk.getChunkId())
                    .isNotNull()
                    .hasSizeGreaterThan(0);
        });
    }

    @Test
    void eachChunkHasParagraphRef() {
        chunkRepository.findAll().forEach(chunk ->
            assertThat(chunk.getParagraphRef())
                    .as("paragraphRef for chunk %s", chunk.getChunkId())
                    .isNotNull()
                    .isNotBlank());
    }

    @Test
    void ingestionResultsContainDocumentIds() {
        assertThat(results).hasSize(8);
        results.forEach(r -> assertThat(r.documentId()).isNotBlank());
    }

    @Test
    void chunksLinkedToParentDocuments() {
        chunkRepository.findAll().forEach(chunk ->
            assertThat(documentRepository.existsByDocumentId(chunk.getDocumentId()))
                    .as("chunk %s references unknown documentId %s",
                            chunk.getChunkId(), chunk.getDocumentId())
                    .isTrue());
    }
}
