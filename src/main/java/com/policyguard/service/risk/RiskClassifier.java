package com.policyguard.service.risk;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.policyguard.config.PolicyguardProperties;

import jakarta.annotation.PostConstruct;

/**
 * Classifies a prompt as HIGH or LOW risk by matching it against a set of
 * compiled regular expression patterns loaded from {@code policyguard.risk.patterns}.
 *
 * <p>The first matching pattern wins; if no pattern matches the prompt is LOW risk.
 * All patterns are compiled with {@link Pattern#CASE_INSENSITIVE} so that
 * case variations in user input are handled transparently.
 *
 * <p>If the patterns list is empty or null at startup a warning is logged and all
 * prompts are classified as LOW — this is a safe degraded mode rather than a
 * hard failure.
 */
@Service
public class RiskClassifier {

    private static final Logger log = LoggerFactory.getLogger(RiskClassifier.class);

    private final PolicyguardProperties properties;

    /** Compiled patterns in priority order. */
    private List<CompiledPattern> compiledPatterns;

    public RiskClassifier(PolicyguardProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void compilePatterns() {
        List<PolicyguardProperties.Risk.RiskPatternConfig> configs =
                properties.getRisk().getPatterns();

        if (configs == null || configs.isEmpty()) {
            log.warn("No risk patterns configured under policyguard.risk.patterns — "
                   + "all prompts will be classified as LOW risk.");
            compiledPatterns = List.of();
            return;
        }

        List<CompiledPattern> compiled = new ArrayList<>(configs.size());
        for (PolicyguardProperties.Risk.RiskPatternConfig cfg : configs) {
            try {
                Pattern p = Pattern.compile(cfg.getRegex(), Pattern.CASE_INSENSITIVE);
                compiled.add(new CompiledPattern(cfg.getCategory(), p));
            } catch (Exception e) {
                log.error("Failed to compile risk pattern for category '{}': {}",
                        cfg.getCategory(), e.getMessage());
            }
        }
        compiledPatterns = List.copyOf(compiled);
        log.info("Compiled {} risk patterns", compiledPatterns.size());
    }

    /**
     * Classifies the given prompt.  The first matching pattern determines the outcome.
     *
     * @param prompt the (PII-redacted) user prompt
     * @return {@link RiskAssessment} with riskLevel HIGH (requiresReview=true) on first match,
     *         or LOW (requiresReview=false, category=null) when no pattern matches
     */
    public RiskAssessment classify(String prompt) {
        if (prompt == null) return new RiskAssessment("LOW", null, false);

        for (CompiledPattern cp : compiledPatterns) {
            if (cp.pattern().matcher(prompt).find()) {
                return new RiskAssessment("HIGH", cp.category(), true);
            }
        }
        return new RiskAssessment("LOW", null, false);
    }

    private record CompiledPattern(String category, Pattern pattern) {}
}
