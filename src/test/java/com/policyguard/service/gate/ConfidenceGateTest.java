package com.policyguard.service.gate;

import java.util.List;
import java.util.Map;

import com.policyguard.config.PolicyguardProperties;
import com.policyguard.service.citation.Citation;
import com.policyguard.service.retrieval.RetrievalHit;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfidenceGateTest {

    private ConfidenceGate gate;
    private PolicyguardProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PolicyguardProperties();
        properties.getConfidence().setThreshold(0.65);
        gate = new ConfidenceGate(properties);
    }

    private static Citation verifiableCitation(String chunkId) {
        return new Citation(chunkId, "DOC-1", "Sec 1", "snippet");
    }

    private static Citation unverifiableCitation() {
        return new Citation(null, "DOC-X", "Sec 99", null);
    }

    private static List<RetrievalHit> noHits() {
        return List.of();
    }

    // ── threshold boundary tests ──────────────────────────────────────────────

    @Test
    void exactThreshold_isAnswer() {
        GateOutcome outcome = gate.evaluate(0.65, List.of(verifiableCitation("c1")), noHits());
        assertThat(outcome.decision()).isEqualTo(GateDecision.ANSWER);
    }

    @Test
    void justBelowThreshold_isRefuse() {
        GateOutcome outcome = gate.evaluate(0.6499, List.of(verifiableCitation("c1")), noHits());
        assertThat(outcome.decision()).isEqualTo(GateDecision.REFUSE);
        assertThat(outcome.reason()).contains("below threshold");
        assertThat(outcome.reason()).contains("0.6499");
        assertThat(outcome.reason()).contains("0.65");
    }

    @Test
    void highConfidence_noCitations_isAnswer() {
        GateOutcome outcome = gate.evaluate(0.95, List.of(), noHits());
        assertThat(outcome.decision()).isEqualTo(GateDecision.ANSWER);
    }

    @Test
    void aboveThreshold_allVerifiable_isAnswer() {
        List<Citation> citations = List.of(verifiableCitation("c1"), verifiableCitation("c2"));
        GateOutcome outcome = gate.evaluate(0.80, citations, noHits());
        assertThat(outcome.decision()).isEqualTo(GateDecision.ANSWER);
    }

    // ── unverifiable citation tests ───────────────────────────────────────────

    @Test
    void unverifiableCitation_isRefuse_evenWithHighConfidence() {
        List<Citation> citations = List.of(verifiableCitation("c1"), unverifiableCitation());
        GateOutcome outcome = gate.evaluate(0.95, citations, noHits());
        assertThat(outcome.decision()).isEqualTo(GateDecision.REFUSE);
        assertThat(outcome.reason()).contains("could not be verified");
    }

    @Test
    void onlyUnverifiableCitation_isRefuse() {
        GateOutcome outcome = gate.evaluate(0.90, List.of(unverifiableCitation()), noHits());
        assertThat(outcome.decision()).isEqualTo(GateDecision.REFUSE);
    }

    @Test
    void lowConfidence_takes_priority_over_unverifiableCitation() {
        // Both problems present — low confidence rule fires first
        GateOutcome outcome = gate.evaluate(0.40, List.of(unverifiableCitation()), noHits());
        assertThat(outcome.decision()).isEqualTo(GateDecision.REFUSE);
        assertThat(outcome.reason()).contains("below threshold");
    }

    // ── isExplicitRefusal tests ───────────────────────────────────────────────

    @Test
    void isExplicitRefusal_exactSentence_returnsTrue() {
        assertThat(gate.isExplicitRefusal(
                "I cannot answer this based on the available policy documents.")).isTrue();
    }

    @Test
    void isExplicitRefusal_withLeadingTrailingWhitespace_returnsTrue() {
        assertThat(gate.isExplicitRefusal(
                "  I cannot answer this based on the available policy documents.  ")).isTrue();
    }

    @Test
    void isExplicitRefusal_differentText_returnsFalse() {
        assertThat(gate.isExplicitRefusal("The answer is 42.")).isFalse();
    }

    @Test
    void isExplicitRefusal_partialMatch_returnsFalse() {
        assertThat(gate.isExplicitRefusal(
                "I cannot answer this based on the available policy documents. See also...")).isFalse();
    }

    @Test
    void isExplicitRefusal_null_returnsFalse() {
        assertThat(gate.isExplicitRefusal(null)).isFalse();
    }

    @Test
    void isExplicitRefusal_emptyString_returnsFalse() {
        assertThat(gate.isExplicitRefusal("")).isFalse();
    }

    // ── configurable threshold ────────────────────────────────────────────────

    @Test
    void customThreshold_0point8_belowRefuses() {
        properties.getConfidence().setThreshold(0.80);
        GateOutcome outcome = gate.evaluate(0.79, List.of(verifiableCitation("c1")), noHits());
        assertThat(outcome.decision()).isEqualTo(GateDecision.REFUSE);
    }

    @Test
    void customThreshold_0point8_atThresholdAnswers() {
        properties.getConfidence().setThreshold(0.80);
        GateOutcome outcome = gate.evaluate(0.80, List.of(verifiableCitation("c1")), noHits());
        assertThat(outcome.decision()).isEqualTo(GateDecision.ANSWER);
    }
}
