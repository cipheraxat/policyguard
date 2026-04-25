package com.policyguard.it;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.mock.web.MockMultipartFile;

import com.policyguard.fixtures.PolicyFixtureFactory;
import com.policyguard.repository.DocumentRepository;
import com.policyguard.service.ingestion.DocumentIngestionService;
import com.policyguard.service.ingestion.IngestionResult;

/**
 * Shared helper: ingests all 8 synthetic policy fixtures into the running
 * Postgres container.  Idempotent — skips any document whose {@code documentId}
 * already exists so multiple test classes can call this without conflicts.
 */
public final class IngestionFixturesHelper {

    private IngestionFixturesHelper() {}

    /**
     * Ingests each of the 8 fixtures produced by {@link PolicyFixtureFactory}.
     * If a document already exists (checked via {@link DocumentRepository}) its
     * ingestion is skipped and a synthetic {@link IngestionResult} (chunksCreated=0)
     * is added to preserve the list size.
     *
     * @return one {@link IngestionResult} per fixture (8 total)
     */
    public static List<IngestionResult> ingestAll(
            DocumentIngestionService ingestionService,
            DocumentRepository documentRepository) throws IOException {

        List<PolicyFixtureFactory.PolicyFixture> fixtures =
                new PolicyFixtureFactory().buildAll();
        List<IngestionResult> results = new ArrayList<>(fixtures.size());

        for (PolicyFixtureFactory.PolicyFixture fixture : fixtures) {
            if (documentRepository.existsByDocumentId(fixture.documentId())) {
                results.add(new IngestionResult(fixture.documentId(), 0, 0));
                continue;
            }

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    fixture.documentId() + ".pdf",
                    "application/pdf",
                    fixture.pdfBytes());

            Map<String, Object> meta = new HashMap<>(fixture.metadata());
            meta.put("documentId", fixture.documentId());
            meta.put("title",      fixture.title());
            meta.put("docType",    fixture.docType());

            results.add(ingestionService.ingest(file, meta));
        }
        return results;
    }
}
