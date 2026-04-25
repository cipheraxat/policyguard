package com.policyguard.service.risk;

import java.util.List;

import com.policyguard.config.PolicyguardProperties;
import com.policyguard.config.PolicyguardProperties.Risk.RiskPatternConfig;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiskClassifierTest {

    private PolicyguardProperties properties;
    private RiskClassifier classifier;

    /** Creates and wires a classifier with the 4 spec patterns from application.yml. */
    @BeforeEach
    void setUp() {
        properties = new PolicyguardProperties();
        properties.getRisk().setPatterns(List.of(
                pattern("regulatory_interpretation",
                        "\\b(FINRA|SEC|GDPR|PCI-DSS|SOX|HIPAA)\\b.*\\b(interpret|apply|compliance requirement)\\b"),
                pattern("customer_data_exposure",
                        "\\b(customer|client|user)\\b.*\\b(data|SSN|account|PII)\\b.*\\b(access|view|share|disclose)\\b"),
                pattern("policy_exception",
                        "\\b(exception|waiver|override|bypass)\\b.*\\b(policy|control|requirement)\\b"),
                pattern("financial_advice",
                        "\\b(advise|recommend|should we|can we)\\b.*\\b(invest|allocate|transfer|withdraw)\\b")
        ));
        classifier = new RiskClassifier(properties);
        classifier.compilePatterns();  // normally called by @PostConstruct
    }

    private static RiskPatternConfig pattern(String category, String regex) {
        RiskPatternConfig cfg = new RiskPatternConfig();
        cfg.setCategory(category);
        cfg.setRegex(regex);
        return cfg;
    }

    // ── regulatory_interpretation ─────────────────────────────────────────────

    @Test
    void regulatoryInterpretation_gdprApply_isHigh() {
        RiskAssessment result = classifier.classify("How should we apply GDPR compliance requirement?");
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.category()).isEqualTo("regulatory_interpretation");
        assertThat(result.requiresReview()).isTrue();
    }

    @Test
    void regulatoryInterpretation_hipaaInterpret_isHigh() {
        // HIPAA (regulatory keyword) must appear before interpret in the sentence
        RiskAssessment result = classifier.classify("HIPAA guidelines require us to interpret patient data rules.");
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.category()).isEqualTo("regulatory_interpretation");
    }

    @Test
    void regulatoryInterpretation_soxApply_isHigh() {
        // SOX must appear before apply in the sentence
        RiskAssessment result = classifier.classify("SOX rules: how do we apply them here?");
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.category()).isEqualTo("regulatory_interpretation");
    }

    // ── customer_data_exposure ────────────────────────────────────────────────

    @Test
    void customerDataExposure_shareCustomerSSN_isHigh() {
        // customer → SSN → share (all three tokens in order, using exact word "share")
        RiskAssessment result = classifier.classify("customer SSN: is it permitted to share with external vendors?");
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.category()).isEqualTo("customer_data_exposure");
        assertThat(result.requiresReview()).isTrue();
    }

    @Test
    void customerDataExposure_viewUserPII_isHigh() {
        // user → PII → view (all three tokens in order)
        RiskAssessment result = classifier.classify("user PII records: who is allowed to view them?");
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.category()).isEqualTo("customer_data_exposure");
    }

    @Test
    void customerDataExposure_disclosureClientAccount_isHigh() {
        // client → account → disclose (all three tokens in order)
        RiskAssessment result = classifier.classify("client account data — can we disclose it?");
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.category()).isEqualTo("customer_data_exposure");
    }

    // ── policy_exception ──────────────────────────────────────────────────────

    @Test
    void policyException_bypassPolicy_isHigh() {
        RiskAssessment result = classifier.classify("Can we bypass the policy for this customer?");
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.category()).isEqualTo("policy_exception");
        assertThat(result.requiresReview()).isTrue();
    }

    @Test
    void policyException_waiverRequirement_isHigh() {
        RiskAssessment result = classifier.classify("We need a waiver for this control requirement.");
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.category()).isEqualTo("policy_exception");
    }

    @Test
    void policyException_overrideControl_isHigh() {
        RiskAssessment result = classifier.classify("Request to override the control for now.");
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.category()).isEqualTo("policy_exception");
    }

    // ── financial_advice ──────────────────────────────────────────────────────

    @Test
    void financialAdvice_shouldWeInvest_isHigh() {
        RiskAssessment result = classifier.classify("Should we invest the surplus funds?");
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.category()).isEqualTo("financial_advice");
        assertThat(result.requiresReview()).isTrue();
    }

    @Test
    void financialAdvice_canWeTransfer_isHigh() {
        RiskAssessment result = classifier.classify("Can we transfer the budget to the reserve?");
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.category()).isEqualTo("financial_advice");
    }

    @Test
    void financialAdvice_recommendWithdraw_isHigh() {
        RiskAssessment result = classifier.classify("I recommend we withdraw the excess funds now.");
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.category()).isEqualTo("financial_advice");
    }

    // ── LOW risk (benign prompts) ─────────────────────────────────────────────

    @Test
    void benignPrompt_dataRetentionPolicy_isLow() {
        RiskAssessment result = classifier.classify("What is the data retention policy duration?");
        assertThat(result.riskLevel()).isEqualTo("LOW");
        assertThat(result.category()).isNull();
        assertThat(result.requiresReview()).isFalse();
    }

    @Test
    void benignPrompt_onboardingProcess_isLow() {
        RiskAssessment result = classifier.classify("What are the steps for employee onboarding?");
        assertThat(result.riskLevel()).isEqualTo("LOW");
    }

    @Test
    void nullPrompt_isLow() {
        RiskAssessment result = classifier.classify(null);
        assertThat(result.riskLevel()).isEqualTo("LOW");
        assertThat(result.requiresReview()).isFalse();
    }

    // ── case insensitivity ────────────────────────────────────────────────────

    @Test
    void caseInsensitive_upperCaseBypass_isHigh() {
        RiskAssessment result = classifier.classify("CAN WE BYPASS THE POLICY?");
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.category()).isEqualTo("policy_exception");
    }

    @Test
    void caseInsensitive_mixedCaseGdpr_isHigh() {
        // Gdpr must appear before apply for the pattern to fire
        RiskAssessment result = classifier.classify("How do Gdpr rules Apply to our system?");
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.category()).isEqualTo("regulatory_interpretation");
    }

    // ── empty patterns defensive ──────────────────────────────────────────────

    @Test
    void emptyPatterns_classifiesEverythingAsLow() {
        properties.getRisk().setPatterns(List.of());
        classifier.compilePatterns();
        RiskAssessment result = classifier.classify("GDPR apply compliance requirement bypass policy");
        assertThat(result.riskLevel()).isEqualTo("LOW");
    }
}
