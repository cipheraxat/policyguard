package com.policyguard.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyguard.domain.Answer;
import com.policyguard.fixtures.GoldQuery;
import com.policyguard.fixtures.GoldSetLoader;
import com.policyguard.fixtures.PolicyFixtureFactory;
import com.policyguard.fixtures.PolicyFixtureFactory.PolicyFixture;
import com.policyguard.repository.AnswerRepository;
import com.policyguard.service.citation.Citation;
import com.policyguard.service.ingestion.DocumentIngestionService;
import com.policyguard.service.ingestion.IngestionResult;
import com.policyguard.service.query.QueryOutcome;
import com.policyguard.service.query.QueryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Pure Mockito tests for {@link EvalHarness}.
 *
 * <p>Uses a synthetic gold set of 6 queries with known expected outcomes and
 * verifies that the metric math (precision / recall / p95) is correct.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvalHarnessTest {

    @Mock QueryService               queryService;
    @Mock DocumentIngestionService   documentIngestionService;
    @Mock PolicyFixtureFactory       fixtureFactory;
    @Mock GoldSetLoader              goldSetLoader;
    @Mock AnswerRepository           answerRepository;

    EvalHarness harness;

    // ── synthetic gold set ────────────────────────────────────────────────────

    /**
     * 6 queries:
     *  q1 → expected answered, POL-001/§1   → actual: Answered, citation POL-001/§1 Overview  (CORRECT)
     *  q2 → expected answered, POL-002/§2   → actual: Answered, citation POL-002/§X Nope       (WRONG para)
     *  q3 → expected answered, POL-003/§3   → actual: Answered, no citations                   (WRONG)
     *  q4 → expected escalated              → actual: Escalated                                 (MATCH)
     *  q5 → expected escalated              → actual: Escalated                                 (MATCH)
     *  q6 → expected refused                → actual: Refused                                   (MATCH)
     *
     * Expected metrics (verified in tests):
     *   answered=3, escalated=2, refused=1, total=6
     *   citation_precision       = 1/3  ≈ 0.3333
     *   retrieval_recall_at_5    = 2/3  ≈ 0.6667  (q1 + q3 have hit; q2 does not)
     *   escalation_precision     = 2/2  = 1.0
     *   escalation_recall        = 2/2  = 1.0
     *   refusal_rate             = 1/6  ≈ 0.1667
     */
    private static final List<GoldQuery> GOLD_SET = List.of(
            new GoldQuery("G1", "q1 text", "answered",  "POL-001", "§1",  null, null, null),
            new GoldQuery("G2", "q2 text", "answered",  "POL-002", "§2",  null, null, null),
            new GoldQuery("G3", "q3 text", "answered",  "POL-003", "§3",  null, null, null),
            new GoldQuery("G4", "q4 text", "escalated", null,      null,  "regulatory_interpretation", null, "regulatory_interpretation"),
            new GoldQuery("G5", "q5 text", "escalated", null,      null,  "policy_exception", null, "policy_exception"),
            new GoldQuery("G6", "q6 text", "refused",   null,      null,  null, "no_hits", null)
    );

    // Answered outcomes
    private static final QueryOutcome ANS_Q1 = new QueryOutcome.Answered(
            "qry-q1", "answer1",
            List.of(new Citation("c1", "POL-001", "§1 Overview", "snippet")),
            0.9, 5);

    private static final QueryOutcome ANS_Q2 = new QueryOutcome.Answered(
            "qry-q2", "answer2",
            List.of(new Citation("c2", "POL-002", "§X Nope", "snippet")),
            0.8, 3);

    private static final QueryOutcome ANS_Q3 = new QueryOutcome.Answered(
            "qry-q3", "answer3",
            List.of(),   // no citations
            0.7, 4);

    private static final QueryOutcome ESC_Q4 = new QueryOutcome.Escalated(
            "qry-q4", "regulatory_interpretation detected", "rev-1", "Routed for review.");

    private static final QueryOutcome ESC_Q5 = new QueryOutcome.Escalated(
            "qry-q5", "policy_exception detected", "rev-2", "Routed for review.");

    private static final QueryOutcome REF_Q6 = new QueryOutcome.Refused(
            "qry-q6", "No relevant documents found", "Cannot answer.");

    @BeforeEach
    void setUp() throws Exception {
        harness = new EvalHarness(queryService, documentIngestionService,
                fixtureFactory, goldSetLoader, new ObjectMapper(), answerRepository);

        // ── fixture factory stub ──
        List<PolicyFixture> fakeFixtures = List.of(
                new PolicyFixture("POL-F1", "policy", "Fixture 1", new byte[]{1}, Map.of()),
                new PolicyFixture("POL-F2", "sop",    "Fixture 2", new byte[]{2}, Map.of())
        );
        when(fixtureFactory.buildAll()).thenReturn(fakeFixtures);
        when(documentIngestionService.ingest(any(), any()))
                .thenReturn(new IngestionResult("POL-F1", 3, 0));

        // ── gold set stub ──
        when(goldSetLoader.load()).thenReturn(GOLD_SET);

        // ── query service stubs ──
        when(queryService.handle("q1 text", "eval-runner", null)).thenReturn(ANS_Q1);
        when(queryService.handle("q2 text", "eval-runner", null)).thenReturn(ANS_Q2);
        when(queryService.handle("q3 text", "eval-runner", null)).thenReturn(ANS_Q3);
        when(queryService.handle("q4 text", "eval-runner", null)).thenReturn(ESC_Q4);
        when(queryService.handle("q5 text", "eval-runner", null)).thenReturn(ESC_Q5);
        when(queryService.handle("q6 text", "eval-runner", null)).thenReturn(REF_Q6);

        // ── answer repository stubs (retrieval hits) ──
        // q1: hit on POL-001 → recall = true
        Answer a1 = answerWithHits(List.of(Map.of("document_id", "POL-001", "score", 0.9)));
        when(answerRepository.findByQueryId("qry-q1")).thenReturn(List.of(a1));

        // q2: no POL-002 hit → recall = false
        Answer a2 = answerWithHits(List.of(Map.of("document_id", "POL-999", "score", 0.5)));
        when(answerRepository.findByQueryId("qry-q2")).thenReturn(List.of(a2));

        // q3: hit on POL-003 → recall = true
        Answer a3 = answerWithHits(List.of(Map.of("document_id", "POL-003", "score", 0.8)));
        when(answerRepository.findByQueryId("qry-q3")).thenReturn(List.of(a3));
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ingestFixtures: DocumentIngestionService called once per fixture")
    void ingestsAllFixtures() throws Exception {
        harness.run();
        verify(documentIngestionService, times(2)).ingest(any(), any());
    }

    @Test
    @DisplayName("outcome counts are correct")
    void outcomeCounts() {
        EvalReport report = harness.run();
        assertThat(report.totalQueries()).isEqualTo(6);
        assertThat(report.answered()).isEqualTo(3);
        assertThat(report.escalated()).isEqualTo(2);
        assertThat(report.refused()).isEqualTo(1);
    }

    @Test
    @DisplayName("citation_precision = 1/3")
    void citationPrecision() {
        EvalReport report = harness.run();
        assertThat(report.citationPrecision()).isCloseTo(1.0 / 3.0, offset(1e-9));
    }

    @Test
    @DisplayName("retrieval_recall_at_5 = 2/3 (q1 and q3 have hits, q2 does not)")
    void retrievalRecallAt5() {
        EvalReport report = harness.run();
        assertThat(report.retrievalRecallAt5()).isCloseTo(2.0 / 3.0, offset(1e-9));
    }

    @Test
    @DisplayName("escalation_precision = 1.0 and escalation_recall = 1.0")
    void escalationMetrics() {
        EvalReport report = harness.run();
        assertThat(report.escalationPrecision()).isCloseTo(1.0, offset(1e-9));
        assertThat(report.escalationRecall()).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    @DisplayName("refusal_rate = 1/6")
    void refusalRate() {
        EvalReport report = harness.run();
        assertThat(report.refusalRate()).isCloseTo(1.0 / 6.0, offset(1e-9));
    }

    @Test
    @DisplayName("pii precision and recall are stubbed to 1.0")
    void piiMetricsAreStubbed() {
        EvalReport report = harness.run();
        assertThat(report.piiRedactionPrecision()).isEqualTo(1.0);
        assertThat(report.piiRedactionRecall()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("details list contains one entry per gold query")
    void detailsSize() {
        EvalReport report = harness.run();
        assertThat(report.details()).hasSize(6);
    }

    // ── p95 pinning ───────────────────────────────────────────────────────────
    // Isolated in a nested class so Mockito strict-stubbing doesn't flag the
    // parent @BeforeEach stubs as unused (p95 tests call only a static method).

    @Nested
    @DisplayName("P95 latency calculation")
    class P95Test {

        @Test
        @DisplayName("6 queries → index ceil(0.95*6)-1 = 5 → value 600")
        void sixQueries() {
            long[] latencies = {100, 200, 300, 400, 500, 600};
            assertThat(EvalHarness.computeP95(latencies)).isEqualTo(600);
        }

        @Test
        @DisplayName("20 queries → index ceil(0.95*20)-1 = 18 → value 190")
        void twentyQueries() {
            long[] latencies = new long[20];
            for (int i = 0; i < 20; i++) latencies[i] = (i + 1) * 10L;
            assertThat(EvalHarness.computeP95(latencies)).isEqualTo(190);
        }

        @Test
        @DisplayName("single query → returns that query's latency")
        void singleQuery() {
            assertThat(EvalHarness.computeP95(new long[]{42})).isEqualTo(42);
        }

        @Test
        @DisplayName("10 queries → index ceil(0.95*10)-1 = 9 → value 1000")
        void tenQueries() {
            long[] latencies = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
            assertThat(EvalHarness.computeP95(latencies)).isEqualTo(1000);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Answer answerWithHits(List<Map<String, Object>> hits) {
        Answer a = new Answer();
        a.setRetrievalHits(hits);
        return a;
    }
}
