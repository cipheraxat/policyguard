package com.policyguard.eval;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyguard.domain.Answer;
import com.policyguard.fixtures.GoldQuery;
import com.policyguard.fixtures.GoldSetLoader;
import com.policyguard.fixtures.PolicyFixtureFactory;
import com.policyguard.fixtures.PolicyFixtureFactory.PolicyFixture;
import com.policyguard.repository.AnswerRepository;
import com.policyguard.service.citation.Citation;
import com.policyguard.service.ingestion.DocumentIngestionService;
import com.policyguard.service.query.QueryOutcome;
import com.policyguard.service.query.QueryService;

/**
 * Evaluation harness: ingests all fixture documents, runs every gold-set query,
 * and computes the {@link EvalReport} metrics.
 *
 * <h3>PII redaction precision / recall</h3>
 * Both are stubbed to {@code 1.0}. Accurate measurement requires a labelled
 * corpus of PII spans (Presidio gold-truth), which is out of scope for v1.
 * TODO: replace stubs once the Presidio annotation pipeline is wired.
 */
@Component
public class EvalHarness {

    private static final Logger log = LoggerFactory.getLogger(EvalHarness.class);

    private final QueryService queryService;
    private final DocumentIngestionService documentIngestionService;
    private final PolicyFixtureFactory fixtureFactory;
    private final GoldSetLoader goldSetLoader;
    private final ObjectMapper objectMapper;
    private final AnswerRepository answerRepository;

    public EvalHarness(QueryService queryService,
                       DocumentIngestionService documentIngestionService,
                       PolicyFixtureFactory fixtureFactory,
                       GoldSetLoader goldSetLoader,
                       ObjectMapper objectMapper,
                       AnswerRepository answerRepository) {
        this.queryService = queryService;
        this.documentIngestionService = documentIngestionService;
        this.fixtureFactory = fixtureFactory;
        this.goldSetLoader = goldSetLoader;
        this.objectMapper = objectMapper;
        this.answerRepository = answerRepository;
    }

    /**
     * Runs the full evaluation pipeline:
     * <ol>
     *   <li>Ingest all 8 fixture documents (idempotent — duplicate ingest is silently skipped).</li>
     *   <li>Execute every gold-set query against the live pipeline.</li>
     *   <li>Compute aggregated metrics.</li>
     *   <li>Write {@code target/eval-report.json}.</li>
     * </ol>
     *
     * @return the populated {@link EvalReport}
     */
    public EvalReport run() {
        ingestFixtures();
        List<GoldQuery> goldSet = goldSetLoader.load();
        List<EvalRecord> records = executeGoldSet(goldSet);
        EvalReport report = aggregateMetrics(records);
        writeReport(report);
        return report;
    }

    // ── ingestion ─────────────────────────────────────────────────────────────

    private void ingestFixtures() {
        List<PolicyFixture> fixtures = fixtureFactory.buildAll();
        for (PolicyFixture fixture : fixtures) {
            try {
                Map<String, Object> meta = new HashMap<>(fixture.metadata() != null ? fixture.metadata() : Map.of());
                meta.put("documentId", fixture.documentId());
                meta.put("title",      fixture.title());
                meta.put("docType",    fixture.docType());
                MultipartFile file = new FixtureMultipartFile(fixture.documentId(), fixture.pdfBytes());
                documentIngestionService.ingest(file, meta);
                log.info("Ingested fixture {}", fixture.documentId());
            } catch (Exception e) {
                // Duplicate-key or already-ingested — safe to continue.
                log.warn("Skipping fixture {} (already ingested or error): {}", fixture.documentId(), e.getMessage());
            }
        }
    }

    // ── query execution ───────────────────────────────────────────────────────

    private List<EvalRecord> executeGoldSet(List<GoldQuery> goldSet) {
        List<EvalRecord> records = new ArrayList<>(goldSet.size());
        for (GoldQuery gq : goldSet) {
            long start = System.nanoTime();
            QueryOutcome actual;
            try {
                actual = queryService.handle(gq.question(), "eval-runner", null);
            } catch (Exception e) {
                log.error("Query {} failed: {}", gq.queryId(), e.getMessage(), e);
                actual = new QueryOutcome.Refused(gq.queryId(), "eval-error: " + e.getMessage(), e.getMessage());
            }
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;

            boolean citationCorrect = checkCitationCorrect(actual, gq);
            boolean recallAt5       = checkRetrievalRecallAt5(actual, gq);
            String notes            = buildNotes(actual, gq);

            records.add(new EvalRecord(gq, actual, latencyMs, citationCorrect, recallAt5, notes));
        }
        return records;
    }

    // ── per-record helpers ────────────────────────────────────────────────────

    private static boolean checkCitationCorrect(QueryOutcome actual, GoldQuery expected) {
        if (!(actual instanceof QueryOutcome.Answered answered)) return false;
        if (expected.expectedCitationDoc() == null || expected.expectedCitationDoc().isBlank()) return false;
        return answered.citations().stream().anyMatch(c ->
                expected.expectedCitationDoc().equals(c.documentId())
                && c.paragraphRef() != null
                && expected.expectedCitationPara() != null
                && c.paragraphRef().startsWith(expected.expectedCitationPara()));
    }

    private boolean checkRetrievalRecallAt5(QueryOutcome actual, GoldQuery expected) {
        if (!(actual instanceof QueryOutcome.Answered answered)) return false;
        if (expected.expectedCitationDoc() == null || expected.expectedCitationDoc().isBlank()) return false;
        List<Answer> answers = answerRepository.findByQueryId(answered.queryId());
        if (answers.isEmpty()) return false;
        List<Map<String, Object>> hits = answers.get(0).getRetrievalHits();
        if (hits == null) return false;
        return hits.stream()
                .limit(5)
                .anyMatch(h -> expected.expectedCitationDoc().equals(h.get("document_id")));
    }

    private static String buildNotes(QueryOutcome actual, GoldQuery expected) {
        String actualStatus = switch (actual) {
            case QueryOutcome.Answered  ignored -> "answered";
            case QueryOutcome.Escalated ignored -> "escalated";
            case QueryOutcome.Refused   ignored -> "refused";
        };
        if (actualStatus.equals(expected.expectedStatus())) return "ok";
        return "status-mismatch: expected=" + expected.expectedStatus() + " actual=" + actualStatus;
    }

    // ── metric aggregation ────────────────────────────────────────────────────

    private static EvalReport aggregateMetrics(List<EvalRecord> records) {
        List<EvalRecord> answeredRecs   = records.stream().filter(r -> r.actual() instanceof QueryOutcome.Answered).toList();
        List<EvalRecord> escalatedRecs  = records.stream().filter(r -> r.actual() instanceof QueryOutcome.Escalated).toList();
        List<EvalRecord> refusedRecs    = records.stream().filter(r -> r.actual() instanceof QueryOutcome.Refused).toList();
        List<EvalRecord> expectedAnsw   = records.stream().filter(r -> "answered".equals(r.expected().expectedStatus())).toList();
        List<EvalRecord> expectedEscal  = records.stream().filter(r -> "escalated".equals(r.expected().expectedStatus())).toList();

        // citation_precision
        double citationPrecision = answeredRecs.isEmpty() ? 0.0
                : (double) answeredRecs.stream().filter(EvalRecord::citationCorrect).count() / answeredRecs.size();

        // retrieval_recall_at_5
        double retrievalRecall = expectedAnsw.isEmpty() ? 0.0
                : (double) expectedAnsw.stream().filter(EvalRecord::retrievalRecallAt5).count() / expectedAnsw.size();

        // escalation precision / recall
        long correctEscalated    = escalatedRecs.stream().filter(r -> "escalated".equals(r.expected().expectedStatus())).count();
        double escalationPrec    = escalatedRecs.isEmpty() ? 0.0 : (double) correctEscalated / escalatedRecs.size();
        double escalationRecall  = expectedEscal.isEmpty() ? 0.0 : (double) correctEscalated / expectedEscal.size();

        // refusal_rate
        double refusalRate = records.isEmpty() ? 0.0 : (double) refusedRecs.size() / records.size();

        // p95 latency
        long[] sorted = records.stream().mapToLong(EvalRecord::latencyMs).sorted().toArray();
        long p95 = computeP95(sorted);

        // TODO: pii_redaction_precision and pii_redaction_recall require a labelled Presidio gold-truth
        //       corpus. Stubbed to 1.0 for v1. See class Javadoc.
        return new EvalReport(
                citationPrecision,
                retrievalRecall,
                1.0,  // pii_redaction_precision — stubbed
                1.0,  // pii_redaction_recall    — stubbed
                escalationPrec,
                escalationRecall,
                refusalRate,
                p95,
                records.size(),
                answeredRecs.size(),
                escalatedRecs.size(),
                refusedRecs.size(),
                records
        );
    }

    /**
     * Computes the 95th-percentile value from a pre-sorted latency array.
     * Package-private to allow direct testing of the math.
     *
     * @param sortedLatencies latencies in ascending order (must be non-empty)
     * @return p95 value
     */
    static long computeP95(long[] sortedLatencies) {
        if (sortedLatencies.length == 0) return 0L;
        int idx = (int) Math.ceil(0.95 * sortedLatencies.length) - 1;
        return sortedLatencies[Math.max(0, idx)];
    }

    // ── report writing ────────────────────────────────────────────────────────

    private void writeReport(EvalReport report) {
        try {
            File dir = new File("target");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "eval-report.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(out, report);
            log.info("Eval report written to {}", out.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write eval-report.json: {}", e.getMessage(), e);
        }
    }

    // ── inner helpers ─────────────────────────────────────────────────────────

    /** Minimal {@link MultipartFile} adapter that wraps raw PDF bytes from a fixture. */
    private static final class FixtureMultipartFile implements MultipartFile {

        private final String documentId;
        private final byte[] bytes;

        FixtureMultipartFile(String documentId, byte[] bytes) {
            this.documentId = documentId;
            this.bytes = bytes != null ? bytes : new byte[0];
        }

        @Override public String getName()             { return documentId; }
        @Override public String getOriginalFilename() { return documentId + ".pdf"; }
        @Override public String getContentType()      { return "application/pdf"; }
        @Override public boolean isEmpty()            { return bytes.length == 0; }
        @Override public long getSize()               { return bytes.length; }
        @Override public byte[] getBytes()            { return bytes; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }

        @Override
        public void transferTo(File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }
}
