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

import com.policyguard.api.query.dto.QueryAnsweredResponse;
import com.policyguard.api.query.dto.QueryRequest;
import com.policyguard.domain.AuditLog;
import com.policyguard.repository.AuditLogRepository;
import com.policyguard.repository.DocumentRepository;
import com.policyguard.service.ingestion.DocumentIngestionService;

/**
 * End-to-end test of the "answered" query pipeline.
 *
 * <p>Confidence threshold is lowered to 0.1 so that the stub embedding model's
 * cosine-similarity scores (≈ 0.75 for any two texts) always pass the gate.
 */
@Tag("it")
@TestInstance(Lifecycle.PER_CLASS)
@TestPropertySource(properties = "policyguard.confidence.threshold=0.1")
class QueryPipelineIT extends BaseIT {

    /** Gold query qry-gold-001: answered about customer PII retention. */
    private static final String ANSWERED_QUESTION =
            "How long must customer PII be retained after account closure";

    @Autowired DocumentIngestionService ingestionService;
    @Autowired DocumentRepository       documentRepository;
    @Autowired AuditLogRepository       auditLogRepository;
    @Autowired TestRestTemplate         restTemplate;

    @BeforeAll
    void ingestFixtures() throws IOException {
        IngestionFixturesHelper.ingestAll(ingestionService, documentRepository);
    }

    @Test
    void answeredQueryReturns200WithQueryId() {
        ResponseEntity<QueryAnsweredResponse> response = restTemplate.postForEntity(
                "/api/query",
                new QueryRequest(ANSWERED_QUESTION, "test-user", null),
                QueryAnsweredResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        QueryAnsweredResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.queryId()).isNotBlank();
        assertThat(body.status()).isEqualTo("answered");
    }

    @Test
    void answeredQueryHasCitations() {
        ResponseEntity<QueryAnsweredResponse> response = restTemplate.postForEntity(
                "/api/query",
                new QueryRequest(ANSWERED_QUESTION, "test-user", null),
                QueryAnsweredResponse.class);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().citations()).isNotEmpty();
        assertThat(response.getBody().citations().get(0).documentId()).isNotBlank();
    }

    @Test
    void answeredQueryAuditChainIsComplete() {
        ResponseEntity<QueryAnsweredResponse> response = restTemplate.postForEntity(
                "/api/query",
                new QueryRequest(ANSWERED_QUESTION, "test-user", null),
                QueryAnsweredResponse.class);

        assertThat(response.getBody()).isNotNull();
        String queryId = response.getBody().queryId();

        List<AuditLog> logs = auditLogRepository.findByQueryIdOrderByTimestampAsc(queryId);
        List<String> eventTypes = logs.stream().map(AuditLog::getEventType).toList();

        assertThat(eventTypes).containsExactly(
                "prompt_received", "pii_redaction", "risk_classification",
                "retrieval", "generation", "response_sent");
    }

    @Test
    void answeredQueryHasPositiveConfidenceScore() {
        ResponseEntity<QueryAnsweredResponse> response = restTemplate.postForEntity(
                "/api/query",
                new QueryRequest(ANSWERED_QUESTION, "test-user", null),
                QueryAnsweredResponse.class);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().confidenceScore()).isGreaterThan(0.0);
    }

    @Test
    void filterByDocumentIdScopesRetrieval() {
        // Queries with a documentId filter still return answers (may have fewer hits)
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/query",
                new QueryRequest(ANSWERED_QUESTION, "test-user",
                        Map.of("documentId", "POL-TEST-001")),
                Map.class);

        assertThat(response.getStatusCode()).isIn(HttpStatus.OK);
    }
}
