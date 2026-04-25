package com.policyguard.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.policyguard.config.PolicyguardProperties;
import com.policyguard.domain.Answer;
import com.policyguard.domain.Query;
import com.policyguard.domain.ReviewQueueItem;
import com.policyguard.repository.AnswerRepository;
import com.policyguard.repository.QueryRepository;
import com.policyguard.service.audit.AuditLogService;
import com.policyguard.service.citation.Citation;
import com.policyguard.service.citation.CitationGenerator;
import com.policyguard.service.citation.CitationResult;
import com.policyguard.service.gate.ConfidenceGate;
import com.policyguard.service.gate.GateDecision;
import com.policyguard.service.gate.GateOutcome;
import com.policyguard.service.pii.PiiRedactionGateway;
import com.policyguard.service.pii.RedactionResult;
import com.policyguard.service.retrieval.HybridRetriever;
import com.policyguard.service.retrieval.RetrievalHit;
import com.policyguard.service.review.ReviewQueueService;
import com.policyguard.service.risk.RiskAssessment;
import com.policyguard.service.risk.RiskClassifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueryServiceTest {

    @Mock private PiiRedactionGateway piiRedactionGateway;
    @Mock private RiskClassifier riskClassifier;
    @Mock private HybridRetriever hybridRetriever;
    @Mock private CitationGenerator citationGenerator;
    @Mock private ConfidenceGate confidenceGate;
    @Mock private ReviewQueueService reviewQueueService;
    @Mock private AuditLogService auditLogService;
    @Mock private QueryRepository queryRepository;
    @Mock private AnswerRepository answerRepository;

    private QueryService queryService;

    @BeforeEach
    void setUp() {
        PolicyguardProperties props = new PolicyguardProperties();
        props.getRetrieval().setTopK(5);

        // queryRepository.save() returns the saved entity
        when(queryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(queryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(answerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        queryService = new QueryService(piiRedactionGateway, riskClassifier, hybridRetriever,
                citationGenerator, confidenceGate, reviewQueueService,
                auditLogService, queryRepository, answerRepository, props);
    }

    // ── Test 1: high-risk prompt escalates without touching retrieval/LLM ────

    @Test
    void highRiskPrompt_escalates_noRetrievalOrGeneration() {
        when(piiRedactionGateway.redact(any())).thenReturn(
                new RedactionResult("what is the SSN rule?", List.of(), false));
        when(riskClassifier.classify(any())).thenReturn(
                new RiskAssessment("HIGH", "regulatory_interpretation", true));

        ReviewQueueItem item = new ReviewQueueItem();
        item.setItemId("rev-abc00001");
        item.setQueryId("qry-test");
        when(reviewQueueService.enqueue(any(), any(), any())).thenReturn(item);

        QueryOutcome outcome = queryService.handle("what is the SSN rule?", "user-1", null);

        assertThat(outcome).isInstanceOf(QueryOutcome.Escalated.class);
        QueryOutcome.Escalated escalated = (QueryOutcome.Escalated) outcome;
        assertThat(escalated.reviewItemId()).isEqualTo("rev-abc00001");
        assertThat(escalated.message()).contains("compliance team");

        verifyNoInteractions(hybridRetriever, citationGenerator, confidenceGate);
    }

    // ── Test 2: low-risk with confident answer → Answered + persists Answer ──

    @Test
    void lowRiskConfidentAnswer_returnsAnsweredAndPersists() {
        when(piiRedactionGateway.redact(any())).thenReturn(
                new RedactionResult("what is the leave policy?", List.of(), false));
        when(riskClassifier.classify(any())).thenReturn(
                new RiskAssessment("LOW", null, false));

        RetrievalHit hit = new RetrievalHit("chk-1", "doc-1", "Section 1, Paragraph 1",
                "Employees get 20 days.", 0.9, Map.of());
        when(hybridRetriever.retrieve(any(), any(), anyInt())).thenReturn(List.of(hit));

        Citation citation = new Citation("chk-1", "doc-1", "Section 1, Paragraph 1",
                "Employees get 20 days.");
        CitationResult cr = new CitationResult("Employees get 20 days annual leave.",
                List.of(citation), 0.9, List.of(hit));
        when(citationGenerator.generate(any(), any())).thenReturn(cr);
        when(confidenceGate.isExplicitRefusal(any())).thenReturn(false);
        when(confidenceGate.evaluate(any(double.class), any(), any())).thenReturn(
                new GateOutcome(GateDecision.ANSWER, "Confidence and citations verified"));

        QueryOutcome outcome = queryService.handle("what is the leave policy?", "user-1", null);

        assertThat(outcome).isInstanceOf(QueryOutcome.Answered.class);
        QueryOutcome.Answered answered = (QueryOutcome.Answered) outcome;
        assertThat(answered.answer()).isEqualTo("Employees get 20 days annual leave.");
        assertThat(answered.citations()).hasSize(1);
        assertThat(answered.confidenceScore()).isEqualTo(0.9);
        assertThat(answered.retrievalHitsCount()).isEqualTo(1);

        verify(answerRepository).save(any(Answer.class));
        verify(queryRepository, times(2)).saveAndFlush(any(Query.class)); // initial + status update

        // Verify audit events: prompt_received, pii_redaction, risk_classification,
        //   retrieval, generation, response_sent  (6 total)
        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService, times(6)).append(any(), eventCaptor.capture(), any(), any(), any());
        List<String> events = eventCaptor.getAllValues();
        assertThat(events).containsExactly(
                "prompt_received", "pii_redaction", "risk_classification",
                "retrieval", "generation", "response_sent");
    }

    // ── Test 3: low-risk with empty retrieval hits → refused ─────────────────

    @Test
    void lowRiskEmptyHits_returnsRefused() {
        when(piiRedactionGateway.redact(any())).thenReturn(
                new RedactionResult("unknown topic question", List.of(), false));
        when(riskClassifier.classify(any())).thenReturn(
                new RiskAssessment("LOW", null, false));
        when(hybridRetriever.retrieve(any(), any(), anyInt())).thenReturn(List.of());

        QueryOutcome outcome = queryService.handle("unknown topic question", "user-1", null);

        assertThat(outcome).isInstanceOf(QueryOutcome.Refused.class);
        QueryOutcome.Refused refused = (QueryOutcome.Refused) outcome;
        assertThat(refused.reason()).contains("No relevant documents");

        verify(citationGenerator, never()).generate(any(), any());
        verify(confidenceGate, never()).evaluate(any(double.class), any(), any());
        verify(answerRepository).save(any(Answer.class));
    }

    // ── Test 4: low-risk, gate refuses (low confidence) → refused ────────────

    @Test
    void lowRiskGateRefuses_returnsRefused() {
        when(piiRedactionGateway.redact(any())).thenReturn(
                new RedactionResult("what is policy X?", List.of(), false));
        when(riskClassifier.classify(any())).thenReturn(
                new RiskAssessment("LOW", null, false));

        RetrievalHit hit = new RetrievalHit("chk-1", "doc-1", "Section 1, Paragraph 1",
                "Some text.", 0.5, Map.of());
        when(hybridRetriever.retrieve(any(), any(), anyInt())).thenReturn(List.of(hit));

        CitationResult cr = new CitationResult("Some partial answer.", List.of(), 0.5, List.of(hit));
        when(citationGenerator.generate(any(), any())).thenReturn(cr);
        when(confidenceGate.isExplicitRefusal(any())).thenReturn(false);
        when(confidenceGate.evaluate(any(double.class), any(), any())).thenReturn(
                new GateOutcome(GateDecision.REFUSE,
                        "Retrieval confidence below threshold (0.5 < 0.65)"));

        QueryOutcome outcome = queryService.handle("what is policy X?", "user-1", null);

        assertThat(outcome).isInstanceOf(QueryOutcome.Refused.class);
        QueryOutcome.Refused refused = (QueryOutcome.Refused) outcome;
        assertThat(refused.reason()).contains("below threshold");
        assertThat(refused.message()).contains("I cannot answer");

        verify(confidenceGate).evaluate(eq(0.5), any(), any());
        verify(answerRepository).save(any(Answer.class));
    }

    // ── Test 5: LLM explicit refusal → refused, gate not consulted ───────────

    @Test
    void llmExplicitRefusal_returnsRefused_gateNotConsulted() {
        when(piiRedactionGateway.redact(any())).thenReturn(
                new RedactionResult("some ambiguous question", List.of(), false));
        when(riskClassifier.classify(any())).thenReturn(
                new RiskAssessment("LOW", null, false));

        RetrievalHit hit = new RetrievalHit("chk-1", "doc-1", "Section 1, Paragraph 1",
                "Some text.", 0.8, Map.of());
        when(hybridRetriever.retrieve(any(), any(), anyInt())).thenReturn(List.of(hit));

        String explicitRefusal = "I cannot answer this based on the available policy documents.";
        CitationResult cr = new CitationResult(explicitRefusal, List.of(), 0.8, List.of(hit));
        when(citationGenerator.generate(any(), any())).thenReturn(cr);
        when(confidenceGate.isExplicitRefusal(eq(explicitRefusal))).thenReturn(true);

        QueryOutcome outcome = queryService.handle("some ambiguous question", "user-1", null);

        assertThat(outcome).isInstanceOf(QueryOutcome.Refused.class);
        QueryOutcome.Refused refused = (QueryOutcome.Refused) outcome;
        assertThat(refused.reason()).contains("Model declined");

        verify(confidenceGate).isExplicitRefusal(explicitRefusal);
        verify(confidenceGate, never()).evaluate(any(double.class), any(), any());
        verify(answerRepository).save(any(Answer.class));
    }
}
