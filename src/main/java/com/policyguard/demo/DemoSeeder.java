package com.policyguard.demo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.policyguard.fixtures.PolicyFixtureFactory;
import com.policyguard.repository.DocumentRepository;
import com.policyguard.service.ingestion.DocumentIngestionService;
import com.policyguard.service.ingestion.IngestionResult;

/**
 * Standalone seeder that ingests all 8 synthetic policy fixtures.
 *
 * <p>Activated by passing {@code --seed} on the command line, e.g.:
 * <pre>
 *   mvn -Pstub spring-boot:run -Dspring-boot.run.arguments=--seed
 * </pre>
 *
 * <p>Idempotent: documents that already exist (by documentId) are skipped.
 */
@Component
public class DemoSeeder implements ApplicationRunner {

    private final DocumentIngestionService ingestionService;
    private final DocumentRepository documentRepository;

    public DemoSeeder(DocumentIngestionService ingestionService,
                      DocumentRepository documentRepository) {
        this.ingestionService = ingestionService;
        this.documentRepository = documentRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!args.containsOption("seed")) {
            return;
        }

        List<PolicyFixtureFactory.PolicyFixture> fixtures =
                new PolicyFixtureFactory().buildAll();

        int ingested = 0;
        int skipped  = 0;

        for (PolicyFixtureFactory.PolicyFixture fixture : fixtures) {
            if (documentRepository.existsByDocumentId(fixture.documentId())) {
                System.out.printf("[DemoSeeder] SKIP  %s (already exists)%n",
                        fixture.documentId());
                skipped++;
                continue;
            }

            MultipartFile file = new ByteArrayMultipartFile(
                    fixture.documentId() + ".pdf",
                    "application/pdf",
                    fixture.pdfBytes());

            Map<String, Object> meta = new HashMap<>(fixture.metadata());
            meta.put("documentId", fixture.documentId());
            meta.put("title",      fixture.title());
            meta.put("docType",    fixture.docType());

            IngestionResult result = ingestionService.ingest(file, meta);
            System.out.printf("[DemoSeeder] INGEST %s — %d chunks, %d PII entities%n",
                    result.documentId(), result.chunksCreated(), result.piiEntitiesRedacted());
            ingested++;
        }

        System.out.printf("[DemoSeeder] Done. Ingested=%d  Skipped=%d%n", ingested, skipped);
    }

    /** Minimal in-memory {@link MultipartFile} backed by a byte array. */
    private record ByteArrayMultipartFile(
            String filename, String contentType, byte[] content)
            implements MultipartFile {

        @Override public String getName()             { return "file"; }
        @Override public String getOriginalFilename() { return filename; }
        @Override public String getContentType()      { return contentType; }
        @Override public boolean isEmpty()            { return content.length == 0; }
        @Override public long getSize()               { return content.length; }
        @Override public byte[] getBytes()            { return content; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override
        public void transferTo(java.io.File dest) throws IllegalStateException, IOException {
            try (var out = new java.io.FileOutputStream(dest)) {
                out.write(content);
            }
        }
    }
}
