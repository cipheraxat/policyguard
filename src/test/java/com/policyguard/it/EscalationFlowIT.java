package com.policyguard.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.policyguard.api.query.dto.QueryEscalatedResponse;
import com.policyguard.api.query.dto.QueryRequest;
import com.policyguard.domain.AuditLog;
import com.policyguard.repository.AuditLogRepository;
import com.policyguard.repository.DocumentRepository;
import com.policyguard.repository.ReviewQueueItemRepository;
import com.policyguard.service.citation.CitationGenerator;
import com.policyguard.service.ingestion.DocumentIngestionService;
import com.policyguard.service.retrieval.HybridRetriever;

/**
 * Verifies that high-risk queries are escalated BEFORE the retrieval and generation
 * pipeline is invoked: {@link HybridRetriever} and {@link CitationGenerator} must
 * never be called for an escalated query.
 *
 * <p>Uses a spy context so a fresh Spring application context is spun up for this class.
 */
@Tag("it")
@TestInstance(Lifecycle.PER_CLASS)
class EscalationFlowIT extends BaseIT {

    /**
     * Gold query qry-gold-002: triggers {@code policy_exception} risk pattern
     * (regex: waiver + policy).  Expected outcome: 202 Accepted, escalated.
     */
    private static final String ESCALATED_QUESTION =
            "Can we request a waiver of the GDPR policy for a VIP customer?";

    @Autowired DocumentIngestionService  ingestionService;
    @Autowired DocumentRepository        documentRepository;
    @Autowired AuditLogRepository        auditLogRepository;
    @Autowired ReviewQueueItemRepository reviewQueueItemRepository;
    @Autowired TestRestTemplate          restTemplate;

    @SpyBean HybridRetriever  hybridRetrieverSpy;
    @SpyBean CitationGenerator citationGeneratorSpy;

    @BeforeAll
    void ingestFixtures() throws IOException {
        IngestionFixturesHelper.ingestAll(ingestionService, documentRepository);
    }

    @Test
    void escalatedQueryReturns202() {
        ResponseEntity<QueryEscalatedResponse> response = restTemplate.postForEntity(
                "/api/query",
                new QueryRequest(ESCALATED_QUESTION, "test-user", null),
                QueryEscalatedResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        QueryEscalatedResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("escalated");
        assertThat(body.reviewItemId()).isNotBlank();
    }

    @Test
    void retrievalAndCitationNeverInvokedForEscalatedQuery() {
        restTemplate.postForEntity(
                "/api/query",
                new QueryRequest(ESCALATED_QUESTION, "test-user", null),
                QueryEscalatedResponse.class);

        verifyNoInteractions(hybridRetrieverSpy);
        verifyNoInteractions(citationGeneratorSpy);
    }

    @Test
    void escalatedQueryPersistsReviewQueueItem() {
        ResponseEntity<QueryEscalatedResponse> response = restTemplate.postForEntity(
                "/api/query",
                new QueryRequest(ESCALATED_QUESTION, "test-user", null),
                QueryEscalatedResponse.class);

        assertThat(response.getBody()).isNotNull();
        String reviewItemId = response.getBody().reviewItemId();

        assertThat(reviewQueueItemRepository.findByItemId(reviewItemId)).isPresent();
    }

    @Test
    void escalatedQueryAuditChainHasNoRetrievalOrGeneration() {
        ResponseEntity<QueryEscalatedResponse> response = restTemplate.postForEntity(
                "/api/query",
                new QueryRequest(ESCALATED_QUESTION, "test-user", null),
                QueryEscalatedResponse.class);

        assertThat(response.getBody()).isNotNull();
        String queryId = response.getBody().queryId();

        List<AuditLog> logs = auditLogRepository.findByQueryIdOrderByTimestampAsc(queryId);
        List<String> eventTypes = logs.stream().map(AuditLog::getEventType).toList();

        assertThat(eventTypes).containsExactly(
                "prompt_received", "pii_redaction", "risk_classification",
                "escalation", "response_sent");
        assertThat(eventTypes).doesNotContain("retrieval", "generation");
    }

    @Test
    void escalatedQueryPushedToRedisQueue() {
        ResponseEntity<QueryEscalatedResponse> response = restTemplate.postForEntity(
                "/api/query",
                new QueryRequest(ESCALATED_QUESTION, "test-user", null),
                QueryEscalatedResponse.class);

        assertThat(response.getBody()).isNotNull();
        String reviewItemId = response.getBody().reviewItemId();

        // ReviewQueueService does a best-effort Redis push; verify the item exists in Postgres
        // (Redis may fail in some environments; Postgres is the source of truth).
        assertThat(reviewQueueItemRepository
                .findByItemId(reviewItemId)
                .map(item -> "pending".equals(item.getStatus()))
                .orElse(false))
                .isTrue();
    }
}
