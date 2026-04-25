package com.policyguard.fixtures;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the gold-set JSON loads correctly and satisfies structural
 * contracts required by the eval harness.
 *
 * <p>Risk patterns are hard-coded here (matching {@code application.yml})
 * to avoid pulling in a Spring context.
 */
class GoldSetLoaderTest {

    // ── risk patterns mirrored from application.yml ────────────────────────────

    private static final Map<String, Pattern> RISK_PATTERNS = Map.of(
            "regulatory_interpretation",
            Pattern.compile(
                    "\\b(FINRA|SEC|GDPR|PCI-DSS|SOX|HIPAA)\\b.*\\b(interpret|apply|compliance requirement)\\b",
                    Pattern.CASE_INSENSITIVE),
            "customer_data_exposure",
            Pattern.compile(
                    "\\b(customer|client|user)\\b.*\\b(data|SSN|account|PII)\\b.*\\b(access|view|share|disclose)\\b",
                    Pattern.CASE_INSENSITIVE),
            "policy_exception",
            Pattern.compile(
                    "\\b(exception|waiver|override|bypass)\\b.*\\b(policy|control|requirement)\\b",
                    Pattern.CASE_INSENSITIVE),
            "financial_advice",
            Pattern.compile(
                    "\\b(advise|recommend|should we|can we)\\b.*\\b(invest|allocate|transfer|withdraw)\\b",
                    Pattern.CASE_INSENSITIVE)
    );

    // ── fixture document IDs ───────────────────────────────────────────────────

    private static final Set<String> FIXTURE_IDS = Set.of(
            "POL-TEST-001", "SOP-TEST-002", "CTL-TEST-003", "FAQ-TEST-004",
            "POL-TEST-005", "SOP-TEST-006", "POL-TEST-007", "FAQ-TEST-008"
    );

    /** Lowercase word sets (length > 6) extracted from each fixture title. */
    private static final Set<String> FIXTURE_TITLE_KEYWORDS = buildFixtureTitleKeywords();

    // ── state ──────────────────────────────────────────────────────────────────

    private static List<GoldQuery> queries;
    private static List<GoldQuery> answered;
    private static List<GoldQuery> escalated;
    private static List<GoldQuery> refused;

    @BeforeAll
    static void loadAll() {
        queries   = new GoldSetLoader().load();
        answered  = queries.stream().filter(q -> "answered".equals(q.expectedStatus())).toList();
        escalated = queries.stream().filter(q -> "escalated".equals(q.expectedStatus())).toList();
        refused   = queries.stream().filter(q -> "refused".equals(q.expectedStatus())).toList();
    }

    // ── cardinality ───────────────────────────────────────────────────────────

    @Test
    void loadsAtLeast40Queries() {
        assertThat(queries).hasSizeGreaterThanOrEqualTo(40);
    }

    @Test
    void distributionMeetsMinimumThresholds() {
        assertThat(answered).as("answered queries").hasSizeGreaterThanOrEqualTo(20);
        assertThat(escalated).as("escalated queries").hasSizeGreaterThanOrEqualTo(8);
        assertThat(refused).as("refused queries").hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void allQueryIdsAreUniqueAndNonBlank() {
        List<String> ids = queries.stream().map(GoldQuery::queryId).toList();
        assertThat(ids).doesNotContainNull();
        assertThat(ids).doesNotHaveDuplicates();
        ids.forEach(id -> assertThat(id).isNotBlank());
    }

    @Test
    void allQuestionsAreNonBlank() {
        queries.forEach(q ->
                assertThat(q.question())
                        .as("question for %s", q.queryId())
                        .isNotBlank());
    }

    // ── answered queries ───────────────────────────────────────────────────────

    @Test
    void answeredQueries_haveCitationDocFromFixtureSet() {
        for (GoldQuery q : answered) {
            assertThat(q.expectedCitationDoc())
                    .as("expected_citation_doc for %s must be a known fixture ID", q.queryId())
                    .isIn(FIXTURE_IDS);
        }
    }

    @Test
    void answeredQueries_haveNonBlankCitationParagraph() {
        for (GoldQuery q : answered) {
            assertThat(q.expectedCitationPara())
                    .as("expected_citation_para for %s must not be blank", q.queryId())
                    .isNotBlank();
        }
    }

    @Test
    void answeredQueries_haveNullEscalationAndRefusalFields() {
        for (GoldQuery q : answered) {
            assertThat(q.expectedEscalationReason())
                    .as("answered query %s should have null escalation reason", q.queryId())
                    .isNull();
            assertThat(q.expectedReason())
                    .as("answered query %s should have null refusal reason", q.queryId())
                    .isNull();
        }
    }

    // ── escalated queries ──────────────────────────────────────────────────────

    @Test
    void escalatedQueries_haveHighRiskCategory() {
        for (GoldQuery q : escalated) {
            assertThat(q.riskCategory())
                    .as("escalated query %s should have risk_category=HIGH", q.queryId())
                    .isEqualTo("HIGH");
        }
    }

    @Test
    void escalatedQueries_escalationReasonMatchesKnownCategory() {
        for (GoldQuery q : escalated) {
            assertThat(q.expectedEscalationReason())
                    .as("escalated query %s must have a non-null escalation reason", q.queryId())
                    .isNotNull()
                    .isIn(RISK_PATTERNS.keySet());
        }
    }

    @Test
    void escalatedQueries_questionMatchesItsEscalationPattern() {
        for (GoldQuery q : escalated) {
            Pattern p = RISK_PATTERNS.get(q.expectedEscalationReason());
            assertThat(p)
                    .as("No pattern registered for reason '%s' (query %s)",
                            q.expectedEscalationReason(), q.queryId())
                    .isNotNull();
            boolean matches = p.matcher(q.question()).find();
            assertThat(matches)
                    .as("Question '%s' (query %s) should match pattern for '%s'",
                            q.question(), q.queryId(), q.expectedEscalationReason())
                    .isTrue();
        }
    }

    @Test
    void allFourRiskCategoriesRepresented_atLeastTwiceEach() {
        Map<String, Long> counts = escalated.stream()
                .collect(Collectors.groupingBy(GoldQuery::expectedEscalationReason, Collectors.counting()));
        RISK_PATTERNS.keySet().forEach(cat ->
                assertThat(counts.getOrDefault(cat, 0L))
                        .as("Risk category '%s' should appear at least twice in escalated queries", cat)
                        .isGreaterThanOrEqualTo(2L));
    }

    // ── refused queries ────────────────────────────────────────────────────────

    @Test
    void refusedQueries_haveNonBlankReason() {
        for (GoldQuery q : refused) {
            assertThat(q.expectedReason())
                    .as("refused query %s should have a non-blank reason", q.queryId())
                    .isNotBlank();
        }
    }

    @Test
    void refusedQueries_distinctiveKeywordsNotInFixtureTitles() {
        // Heuristic: words in the refused question that are > 6 chars long
        // should not overlap with the key words in the fixture titles
        // (this is not airtight but catches obviously bad refused queries).
        for (GoldQuery q : refused) {
            Set<String> questionKeywords = Arrays.stream(q.question().split("\\W+"))
                    .filter(w -> w.length() > 6)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
            Set<String> overlap = questionKeywords.stream()
                    .filter(FIXTURE_TITLE_KEYWORDS::contains)
                    .collect(Collectors.toSet());
            assertThat(overlap)
                    .as("Refused query '%s' (id=%s) should not share key words with fixture titles",
                            q.question(), q.queryId())
                    .isEmpty();
        }
    }

    // ── spec examples ──────────────────────────────────────────────────────────

    @Test
    void specExampleQueries_presentWithCorrectFields() {
        Map<String, GoldQuery> byId = queries.stream()
                .collect(Collectors.toMap(GoldQuery::queryId, q -> q));

        GoldQuery g001 = byId.get("qry-gold-001");
        assertThat(g001).isNotNull();
        assertThat(g001.expectedStatus()).isEqualTo("answered");
        assertThat(g001.expectedCitationDoc()).isEqualTo("POL-TEST-001");
        assertThat(g001.expectedCitationPara()).isEqualTo("Section 4.1, Paragraph 2");

        GoldQuery g002 = byId.get("qry-gold-002");
        assertThat(g002).isNotNull();
        assertThat(g002.expectedStatus()).isEqualTo("escalated");
        assertThat(g002.expectedEscalationReason()).isEqualTo("policy_exception");
        assertThat(g002.riskCategory()).isEqualTo("HIGH");

        GoldQuery g003 = byId.get("qry-gold-003");
        assertThat(g003).isNotNull();
        assertThat(g003.expectedStatus()).isEqualTo("refused");
        assertThat(g003.expectedReason()).isNotBlank();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static Set<String> buildFixtureTitleKeywords() {
        List<String> titles = List.of(
                "Data Retention and Privacy Policy",
                "Incident Response Procedure",
                "Access Control Matrix",
                "Employee Benefits FAQ",
                "Acceptable Use Policy",
                "Customer Data Handling",
                "Information Security Policy",
                "IT Support FAQ"
        );
        return titles.stream()
                .flatMap(t -> Arrays.stream(t.split("\\W+")))
                .filter(w -> w.length() > 6)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }
}
