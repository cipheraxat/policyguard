package com.policyguard.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import com.policyguard.api.query.dto.QueryEscalatedResponse;
import com.policyguard.api.query.dto.QueryRequest;
import com.policyguard.api.review.dto.ResolveRequest;
import com.policyguard.api.review.dto.ResolveResponse;
import com.policyguard.domain.AuditLog;
import com.policyguard.repository.AuditLogRepository;
import com.policyguard.repository.DocumentRepository;
import com.policyguard.repository.QueryRepository;
import com.policyguard.service.ingestion.DocumentIngestionService;

/**
 * Exercises the full human-review approval flow:
 * <ol>
 *   <li>Submit a high-risk query → 202 escalated.</li>
 *   <li>Resolve via {@code POST /api/review/{itemId}/resolve} with {@code decision=approved}.</li>
 *   <li>Assert the final answer is present, the query status flipped to {@code answered},
 *       and the audit chain ends with a {@code response_sent} event attributed to
 *       {@code reviewer-{reviewerId}}.</li>
 * </ol>
 */
@Tag("it")
@TestInstance(Lifecycle.PER_CLASS)
@TestPropertySource(properties = "policyguard.confidence.threshold=0.1")
class ReviewWorkflowIT extends BaseIT {

    private static final String ESCALATED_QUESTION =
            "Can we request a waiver of the GDPR policy for a VIP customer?";
    private static final String REVIEWER_ID = "reviewer-alice";

    @Autowired DocumentIngestionService ingestionService;
    @Autowired DocumentRepository       documentRepository;
    @Autowired AuditLogRepository       auditLogRepository;
    @Autowired QueryRepository          queryRepository;
    @Autowired TestRestTemplate         restTemplate;

    private String reviewItemId;
    private String escalatedQueryId;

    @BeforeAll
    void setup() throws IOException {
        IngestionFixturesHelper.ingestAll(ingestionService, documentRepository);

        // Escalate
        ResponseEntity<QueryEscalatedResponse> escalated = restTemplate.postForEntity(
                "/api/query",
                new QueryRequest(ESCALATED_QUESTION, "test-user", null),
                QueryEscalatedResponse.class);

        assertThat(escalated.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(escalated.getBody()).isNotNull();

        reviewItemId    = escalated.getBody().reviewItemId();
        escalatedQueryId = escalated.getBody().queryId();
    }

    @Test
    void resolveApprovedReturns200() {
        ResolveResponse resp = resolveItem(reviewItemId, "approved");
        assertThat(resp).isNotNull();
        assertThat(resp.status()).isEqualTo("approved");
    }

    @Test
    void resolveApprovedReturnsFinalAnswer() {
        ResolveResponse resp = resolveItem(reviewItemId, "approved");
        assertThat(resp).isNotNull();
        // The pipeline ran after approval → a final answer should be present
        // (may be null only if the approved query is also refused by confidence gate,
        //  but threshold=0.1 ensures it passes)
        assertThat(resp.finalAnswer()).isNotNull().isNotBlank();
    }

    @Test
    void queryStatusFlipsToAnsweredAfterApproval() {
        resolveItem(reviewItemId, "approved");

        assertThat(queryRepository.findByQueryId(escalatedQueryId))
                .isPresent()
                .hasValueSatisfying(q -> assertThat(q.getStatus()).isEqualTo("answered"));
    }

    @Test
    void auditChainAfterApprovalContainsReviewerResponseSent() {
        resolveItem(reviewItemId, "approved");

        List<AuditLog> logs = auditLogRepository
                .findByQueryIdOrderByTimestampAsc(escalatedQueryId);

        // The last response_sent event should be attributed to the reviewer
        List<AuditLog> responseSentLogs = logs.stream()
                .filter(l -> "response_sent".equals(l.getEventType()))
                .toList();
        assertThat(responseSentLogs).as("at least one response_sent event").isNotEmpty();

        // After approval, the reviewer-triggered response_sent is the last one
        AuditLog last = responseSentLogs.get(responseSentLogs.size() - 1);
        assertThat(last.getActor()).startsWith("reviewer-");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ResolveResponse resolveItem(String itemId, String decision) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Reviewer-Id", REVIEWER_ID);
        headers.set("Content-Type", "application/json");

        ResolveRequest body = new ResolveRequest(REVIEWER_ID, decision, "IT test approval", null);

        ResponseEntity<ResolveResponse> resp = restTemplate.exchange(
                "/api/review/" + itemId + "/resolve",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                ResolveResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }
}
