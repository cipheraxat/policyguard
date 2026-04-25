package com.policyguard.service.gate;

import java.util.List;

import org.springframework.stereotype.Service;

import com.policyguard.config.PolicyguardProperties;
import com.policyguard.service.citation.Citation;
import com.policyguard.service.retrieval.RetrievalHit;

/**
 * Guards the answer path by verifying that the retrieval confidence is above the
 * configured threshold <em>and</em> that every parsed citation can be matched to
 * an actual retrieved chunk.
 *
 * <h3>Explicit-refusal bypass</h3>
 * <p>When the LLM itself responds with the canonical "I cannot answer…" sentence,
 * the pipeline should skip the gate entirely and pass the refusal through to the
 * caller — the model has already declined in-scope.  Use {@link #isExplicitRefusal(String)}
 * to detect this case before calling {@link #evaluate}.
 */
@Service
public class ConfidenceGate {

    /**
     * The exact sentence the LLM is instructed to output when it cannot answer.
     * Must be matched exactly (trimmed) so the gate is not triggered by partial matches.
     */
    static final String EXPLICIT_REFUSAL_TEXT =
            "I cannot answer this based on the available policy documents.";

    private final PolicyguardProperties properties;

    public ConfidenceGate(PolicyguardProperties properties) {
        this.properties = properties;
    }

    /**
     * Evaluates whether the generated answer should be passed through or refused.
     *
     * <p>Rules (applied in order):
     * <ol>
     *   <li>If {@code confidence} is below the configured threshold → REFUSE.</li>
     *   <li>If any citation has a {@code null} chunkId (unverifiable against the
     *       retrieved chunks) → REFUSE.</li>
     *   <li>Otherwise → ANSWER.</li>
     * </ol>
     *
     * <p><strong>Note:</strong> this method should <em>not</em> be called when
     * {@link #isExplicitRefusal(String)} returns {@code true}; in that case the
     * pipeline should surface the LLM's own refusal message directly.
     *
     * @param confidence confidence score (0.0–1.0)
     * @param citations  citations parsed from the LLM response
     * @param hits       retrieval hits used to build the prompt (unused by current rules
     *                   but provided for future extensibility)
     * @return gate outcome with decision and human-readable reason
     */
    public GateOutcome evaluate(double confidence, List<Citation> citations,
                                List<RetrievalHit> hits) {
        double threshold = properties.getConfidence().getThreshold();

        if (confidence < threshold) {
            return new GateOutcome(GateDecision.REFUSE,
                    "Retrieval confidence below threshold (" + confidence + " < " + threshold + ")");
        }

        boolean hasUnverifiable = citations.stream()
                .anyMatch(c -> c.chunkId() == null);
        if (hasUnverifiable) {
            return new GateOutcome(GateDecision.REFUSE,
                    "Citations could not be verified against retrieved chunks");
        }

        return new GateOutcome(GateDecision.ANSWER, "Confidence and citations verified");
    }

    /**
     * Returns {@code true} when the response text exactly matches the LLM's
     * canonical in-scope refusal sentence.  In this case the pipeline should pass
     * the response through without running the confidence gate — the model has
     * properly declined and no hallucination risk exists.
     *
     * @param text the raw LLM response text (may be null)
     * @return {@code true} if the text is the explicit refusal sentence
     */
    public boolean isExplicitRefusal(String text) {
        return text != null && EXPLICIT_REFUSAL_TEXT.equals(text.strip());
    }
}
