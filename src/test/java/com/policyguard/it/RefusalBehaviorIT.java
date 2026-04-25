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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import com.policyguard.api.query.dto.QueryRequest;
import com.policyguard.api.query.dto.QueryRefusedResponse;
import com.policyguard.domain.AuditLog;
import com.policyguard.repository.AuditLogRepository;
import com.policyguard.repository.AnswerRepository;
import com.policyguard.repository.DocumentRepository;
import com.policyguard.service.ingestion.DocumentIngestionService;

/**
 * Verifies that queries which cannot be answered are correctly refused.
 *
 * <p>The confidence threshold is raised to 0.99 so that the stub embedding model's
 * cosine-similarity scores (≈ 0.75) fall below the threshold, exercising the
 * "confidence below threshold → REFUSE" path in {@link com.policyguard.service.gate.ConfidenceGate}.
 */
@Tag("it")
@TestInstance(Lifecycle.PER_CLASS)
@TestPropertySource(properties = "policyguard.confidence.threshold=0.99")
class RefusalBehaviorIT extends BaseIT {

    /**
     * Gold query qry-gold-003: topic (Antarctica) is absent from the policy corpus.
     * With threshold=0.99 the confidence gate refuses regardless of retrieval results.
     */
    private static final String REFUSED_QUESTION =
            "What is the refund policy for employees in Antarctica?";

    @Autowired DocumentIngestionService ingestionService;
    @Autowired DocumentRepository       documentRepository;
    @Autowired AuditLogRepository       auditLogRepository;
    @Autowired AnswerRepository         answerRepository;
    @Autowired TestRestTemplate         restTemplate;

    @BeforeAll
    void ingestFixtures() throws IOException {
        IngestionFixturesHelper.ingestAll(ingestionService, documentRepository);
    }

    @Test
    void refusedQueryReturns200() {
        ResponseEntity<QueryRefusedResponse> response = restTemplate.postForEntity(
                "/api/query",
                new QueryRequest(REFUSED_QUESTION, "test-user", null),
                QueryRefusedResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void refusedQueryHasRefusedStatus() {
        ResponseEntity<QueryRefusedResponse> response = restTemplate.postForEntity(
                "/api/query",
                new QueryRequest(REFUSED_QUESTION, "test-user", null),
                QueryRefusedResponse.class);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("refused");
    }

    @Test
    void refusedQueryReasonMentionsThresholdOrNoDocuments() {
        ResponseEntity<QueryRefusedResponse> response = restTemplate.postForEntity(
                "/api/query",
                new QueryRequest(REFUSED_QUESTION, "test-user", null),
                QueryRefusedResponse.class);

        assertThat(response.getBody()).isNotNull();
        String reason = response.getBody().reason();
        assertThat(reason)
                .as("reason should mention threshold or no documents")
                .satisfiesAnyOf(
                        r -> assertThat(r).containsIgnoringCase("threshold"),
                        r -> assertThat(r).containsIgnoringCase("no relevant"),
                        r -> assertThat(r).containsIgnoringCase("cannot be verified"));
    }

    @Test
    void refusedQueryStillPersistsAnswerRowForTraceability() {
        ResponseEntity<QueryRefusedResponse> response = restTemplate.postForEntity(
                "/api/query",
                new QueryRequest(REFUSED_QUESTION, "test-user", null),
                QueryRefusedResponse.class);

        assertThat(response.getBody()).isNotNull();
        String queryId = response.getBody().queryId();

        // An Answer row is always persisted (even for refusals) for audit traceability
        assertThat(answerRepository.findByQueryId(queryId)).isNotEmpty();
    }

    @Test
    void refusedQueryAuditEndsWithResponseSent() {
        ResponseEntity<QueryRefusedResponse> response = restTemplate.postForEntity(
                "/api/query",
                new QueryRequest(REFUSED_QUESTION, "test-user", null),
                QueryRefusedResponse.class);

        assertThat(response.getBody()).isNotNull();
        String queryId = response.getBody().queryId();

        List<AuditLog> logs = auditLogRepository.findByQueryIdOrderByTimestampAsc(queryId);
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(logs.size() - 1).getEventType()).isEqualTo("response_sent");
    }

    @SuppressWarnings("unchecked")
    @Test
    void refusedQueryAnswerHasEmptyCitations() {
        ResponseEntity<QueryRefusedResponse> response = restTemplate.postForEntity(
                "/api/query",
                new QueryRequest(REFUSED_QUESTION, "test-user", null),
                QueryRefusedResponse.class);

        assertThat(response.getBody()).isNotNull();
        String queryId = response.getBody().queryId();

        answerRepository.findByQueryId(queryId).forEach(answer -> {
            List<Map<String, Object>> citations = answer.getCitations();
            assertThat(citations).as("refused answer should have no citations").isEmpty();
        });
    }
}
