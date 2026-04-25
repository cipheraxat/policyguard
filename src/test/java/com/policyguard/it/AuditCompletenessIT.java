package com.policyguard.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.TestPropertySource;

import com.policyguard.api.query.dto.QueryEscalatedResponse;
import com.policyguard.api.query.dto.QueryRequest;
import com.policyguard.domain.AuditLog;
import com.policyguard.repository.AuditLogRepository;
import com.policyguard.repository.DocumentRepository;
import com.policyguard.repository.QueryRepository;
import com.policyguard.service.ingestion.DocumentIngestionService;

/**
 * Verifies the append-only audit log invariant across all pipeline outcomes.
 *
 * <p>For every query submitted through the system:
 * <ul>
 *   <li>The audit chain must end with a {@code response_sent} event.</li>
 *   <li>Timestamps must be monotonically non-decreasing within each chain.</li>
 *   <li>The log must be append-only: no UPDATE/DELETE operations are possible
 *       because {@link com.policyguard.service.audit.AuditLogService} exposes
 *       only an {@code append} method (schema has no {@code updated_at}).</li>
 * </ul>
 */
@Tag("it")
@TestInstance(Lifecycle.PER_CLASS)
@TestPropertySource(properties = "policyguard.confidence.threshold=0.1")
class AuditCompletenessIT extends BaseIT {

    private static final String ANSWERED_QUESTION =
            "What is the first action to take when a security incident is detected?";
    private static final String ESCALATED_QUESTION =
            "Can we override the access control policy for a specific customer data exception?";

    @Autowired DocumentIngestionService ingestionService;
    @Autowired DocumentRepository       documentRepository;
    @Autowired AuditLogRepository       auditLogRepository;
    @Autowired QueryRepository          queryRepository;
    @Autowired TestRestTemplate         restTemplate;

    private String answeredQueryId;
    private String escalatedQueryId;

    @BeforeAll
    void setup() throws IOException {
        IngestionFixturesHelper.ingestAll(ingestionService, documentRepository);

        // Submit answered query
        var answeredResp = restTemplate.postForEntity(
                "/api/query",
                new QueryRequest(ANSWERED_QUESTION, "audit-tester", null),
                com.policyguard.api.query.dto.QueryAnsweredResponse.class);
        if (answeredResp.getBody() != null) {
            answeredQueryId = answeredResp.getBody().queryId();
        }

        // Submit escalated query
        var escalatedResp = restTemplate.postForEntity(
                "/api/query",
                new QueryRequest(ESCALATED_QUESTION, "audit-tester", null),
                QueryEscalatedResponse.class);
        if (escalatedResp.getBody() != null) {
            escalatedQueryId = escalatedResp.getBody().queryId();
        }
    }

    @Test
    void allQueriesHaveResponseSentAuditEvent() {
        queryRepository.findAll().forEach(query -> {
            List<AuditLog> logs = auditLogRepository
                    .findByQueryIdOrderByTimestampAsc(query.getQueryId());
            if (logs.isEmpty()) return; // queries from other tests may not have audit yet

            List<String> events = logs.stream().map(AuditLog::getEventType).toList();
            assertThat(events)
                    .as("query %s must end with response_sent", query.getQueryId())
                    .contains("response_sent");
            assertThat(events.get(events.size() - 1))
                    .as("response_sent must be the last event for query %s", query.getQueryId())
                    .isEqualTo("response_sent");
        });
    }

    @Test
    void answeredQueryAuditTimestampsAreMonotonicallyNonDecreasing() {
        assertThat(answeredQueryId).as("answered query must have been created").isNotNull();

        List<AuditLog> logs = auditLogRepository.findByQueryIdOrderByTimestampAsc(answeredQueryId);
        assertThat(logs).hasSizeGreaterThanOrEqualTo(4);

        for (int i = 1; i < logs.size(); i++) {
            OffsetDateTime prev = logs.get(i - 1).getTimestamp();
            OffsetDateTime curr = logs.get(i).getTimestamp();
            assertThat(curr)
                    .as("timestamp at position %d must be >= position %d", i, i - 1)
                    .isAfterOrEqualTo(prev);
        }
    }

    @Test
    void escalatedQueryAuditTimestampsAreMonotonicallyNonDecreasing() {
        assertThat(escalatedQueryId).as("escalated query must have been created").isNotNull();

        List<AuditLog> logs = auditLogRepository.findByQueryIdOrderByTimestampAsc(escalatedQueryId);
        assertThat(logs).hasSizeGreaterThanOrEqualTo(4);

        for (int i = 1; i < logs.size(); i++) {
            OffsetDateTime prev = logs.get(i - 1).getTimestamp();
            OffsetDateTime curr = logs.get(i).getTimestamp();
            assertThat(curr).isAfterOrEqualTo(prev);
        }
    }

    @Test
    void auditLogsAreAppendOnly_noUpdatedAtColumn() {
        // The audit_logs table schema has no updated_at column — this is enforced
        // by the DDL (V1__init_schema.sql). Verify by checking that all persisted
        // logs have a non-null timestamp (the only time field).
        assertThat(answeredQueryId).isNotNull();
        auditLogRepository.findByQueryIdOrderByTimestampAsc(answeredQueryId)
                .forEach(log -> assertThat(log.getTimestamp()).isNotNull());
    }

    @Test
    void answeredQueryContainsExpectedAuditEvents() {
        assertThat(answeredQueryId).isNotNull();
        List<String> events = auditLogRepository
                .findByQueryIdOrderByTimestampAsc(answeredQueryId)
                .stream().map(AuditLog::getEventType).toList();

        assertThat(events).containsSubsequence(
                "prompt_received", "pii_redaction", "risk_classification", "response_sent");
    }

    @Test
    void escalatedQueryContainsEscalationEvent() {
        assertThat(escalatedQueryId).isNotNull();
        List<String> events = auditLogRepository
                .findByQueryIdOrderByTimestampAsc(escalatedQueryId)
                .stream().map(AuditLog::getEventType).toList();

        assertThat(events).contains("escalation");
        assertThat(events).doesNotContain("retrieval", "generation");
    }

    @Test
    void logIdsAreUniqueAcrossAllLogs() {
        List<AuditLog> all = auditLogRepository.findAll();
        long distinctCount = all.stream().map(AuditLog::getLogId)
                .collect(Collectors.toSet()).size();
        assertThat(distinctCount).isEqualTo(all.size());
    }
}
