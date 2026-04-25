package com.policyguard.fixtures;

import com.policyguard.service.ingestion.PdfExtractionService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link PolicyFixtureFactory} produces the expected PDF fixtures
 * without requiring a Spring context (PDFBox runs as a plain library).
 */
class PolicyFixtureFactoryTest {

    private static final PolicyFixtureFactory FACTORY = new PolicyFixtureFactory();
    private static final PdfExtractionService EXTRACTOR = new PdfExtractionService();

    private static List<PolicyFixtureFactory.PolicyFixture> fixtures;
    private static Map<String, String> extractedText; // documentId → extracted text

    @BeforeAll
    static void buildAll() {
        fixtures = FACTORY.buildAll();
        extractedText = fixtures.stream().collect(
                Collectors.toMap(
                        PolicyFixtureFactory.PolicyFixture::documentId,
                        f -> EXTRACTOR.extract(f.pdfBytes())
                ));
    }

    // ── count + shape ──────────────────────────────────────────────────────────

    @Test
    void buildAll_returns8Fixtures() {
        assertThat(fixtures).hasSize(8);
    }

    @Test
    void buildAll_hasExpectedDocumentIds() {
        List<String> ids = fixtures.stream().map(PolicyFixtureFactory.PolicyFixture::documentId).toList();
        assertThat(ids).containsExactly(
                "POL-TEST-001", "SOP-TEST-002", "CTL-TEST-003", "FAQ-TEST-004",
                "POL-TEST-005", "SOP-TEST-006", "POL-TEST-007", "FAQ-TEST-008"
        );
    }

    @Test
    void buildAll_hasExpectedDocTypes() {
        Map<String, String> types = fixtures.stream()
                .collect(Collectors.toMap(PolicyFixtureFactory.PolicyFixture::documentId,
                         PolicyFixtureFactory.PolicyFixture::docType));
        assertThat(types).containsEntry("POL-TEST-001", "policy")
                .containsEntry("SOP-TEST-002", "sop")
                .containsEntry("CTL-TEST-003", "control")
                .containsEntry("FAQ-TEST-004", "faq")
                .containsEntry("POL-TEST-005", "policy")
                .containsEntry("SOP-TEST-006", "sop")
                .containsEntry("POL-TEST-007", "policy")
                .containsEntry("FAQ-TEST-008", "faq");
    }

    @Test
    void eachFixture_hasTitleMetadataAndNonEmptyPdfBytes() {
        for (PolicyFixtureFactory.PolicyFixture f : fixtures) {
            assertThat(f.title()).isNotBlank();
            assertThat(f.pdfBytes()).isNotEmpty();
            assertThat(f.metadata()).isNotEmpty();
        }
    }

    // ── PDF extraction ─────────────────────────────────────────────────────────

    @Test
    void extractedText_containsDocumentTitle_forAllFixtures() {
        for (PolicyFixtureFactory.PolicyFixture f : fixtures) {
            String text = extractedText.get(f.documentId());
            assertThat(text)
                    .as("Extracted text for %s should contain title '%s'", f.documentId(), f.title())
                    .containsIgnoringCase(f.title().substring(0, 10)); // first 10 chars as proxy
        }
    }

    @Test
    void extractedText_isMultiPage_byVolumeOfText() {
        // A single A4 page with our layout holds ~52 lines × 92 chars ≈ ~4800 chars.
        // Multi-page PDFs should consistently produce more than 3000 chars per doc.
        for (PolicyFixtureFactory.PolicyFixture f : fixtures) {
            String text = extractedText.get(f.documentId());
            assertThat(text.length())
                    .as("Extracted text for %s should be substantial (multi-page)", f.documentId())
                    .isGreaterThan(3000);
        }
    }

    // ── PII presence in high-PII docs ─────────────────────────────────────────

    @Test
    void highPiiDocs_extractedTextContainsFakeEmailDomain() {
        Set<String> highPiiIds = Set.of("CTL-TEST-003", "FAQ-TEST-004", "SOP-TEST-006");
        for (String id : highPiiIds) {
            String text = extractedText.get(id);
            assertThat(text)
                    .as("High-PII doc %s should contain @company-test.example", id)
                    .contains("@company-test.example");
        }
    }

    @Test
    void allHighPiiDocs_extractedTextContainsSsnPattern() {
        // CTL-TEST-003 and SOP-TEST-006 explicitly contain SSN-format references
        for (String id : List.of("CTL-TEST-003", "SOP-TEST-006")) {
            String text = extractedText.get(id);
            assertThat(text)
                    .as("High-PII doc %s should contain SSN pattern 000-00-00xx", id)
                    .containsPattern("000-00-00\\d\\d");
        }
    }

    // ── high-risk content ──────────────────────────────────────────────────────

    @Test
    void highRiskDocs_containRiskKeywords() {
        // Each high-risk document must contain at least one keyword that risk-classifier
        // patterns are designed to detect (GDPR, HIPAA, exception, customer data, disclose).
        Map<String, List<String>> expected = Map.of(
                "POL-TEST-001", List.of("GDPR", "exception"),
                "SOP-TEST-002", List.of("GDPR"),
                "CTL-TEST-003", List.of("exception"),
                "SOP-TEST-006", List.of("GDPR", "PCI-DSS"),
                "POL-TEST-007", List.of("HIPAA", "exception")
        );
        for (Map.Entry<String, List<String>> entry : expected.entrySet()) {
            String docId = entry.getKey();
            String text = extractedText.get(docId);
            boolean found = entry.getValue().stream()
                    .anyMatch(kw -> text.toLowerCase().contains(kw.toLowerCase()));
            assertThat(found)
                    .as("High-risk doc %s should contain one of: %s", docId, entry.getValue())
                    .isTrue();
        }
    }

    @Test
    void highRiskDocs_customerDataLanguage_inSop006() {
        String text = extractedText.get("SOP-TEST-006");
        // Must contain language about accessing / viewing / sharing customer data
        assertThat(text).containsIgnoringCase("customer data");
    }

    // ── build(id) delegation ───────────────────────────────────────────────────

    @Test
    void build_returnsCorrectFixture_byDocumentId() {
        PolicyFixtureFactory.PolicyFixture f = FACTORY.build("POL-TEST-001");
        assertThat(f.documentId()).isEqualTo("POL-TEST-001");
        assertThat(f.docType()).isEqualTo("policy");
    }

    @Test
    void build_throwsForUnknownId() {
        assertThatThrownBy(() -> FACTORY.build("UNKNOWN-999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN-999");
    }

    // ── specific content spot-checks ───────────────────────────────────────────

    @Test
    void pol001_containsSevenYearRetentionRule() {
        String text = extractedText.get("POL-TEST-001");
        assertThat(text).containsIgnoringCase("seven (7) years");
    }

    @Test
    void pol001_containsComplianceOfficerName() {
        String text = extractedText.get("POL-TEST-001");
        assertThat(text).contains("Jane Doe-TEST");
    }

    @Test
    void sop002_contains72HourContext() {
        // SOP-TEST-002 escalation references timing; and POL-TEST-007 has 72-hour rule
        String text = extractedText.get("POL-TEST-007");
        assertThat(text).containsIgnoringCase("seventy-two (72) hours");
    }

    @Test
    void ctl003_containsJohnSmithFake() {
        String text = extractedText.get("CTL-TEST-003");
        assertThat(text).contains("John Smith-FAKE");
    }

    @Test
    void sop006_containsAes256AndTls() {
        String text = extractedText.get("SOP-TEST-006");
        assertThat(text).containsIgnoringCase("AES-256");
        assertThat(text).containsIgnoringCase("TLS 1.3");
    }
}
