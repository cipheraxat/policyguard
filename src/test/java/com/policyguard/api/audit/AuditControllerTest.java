package com.policyguard.api.audit;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.policyguard.domain.AuditLog;
import com.policyguard.repository.AuditLogRepository;

@WebMvcTest(AuditController.class)
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditLogRepository auditLogRepository;

    @Test
    void getAuditLog_returnsEventsOrderedByTimestamp() throws Exception {
        OffsetDateTime t1 = OffsetDateTime.now().minusMinutes(5);
        OffsetDateTime t2 = OffsetDateTime.now().minusMinutes(2);
        OffsetDateTime t3 = OffsetDateTime.now();

        AuditLog log1 = auditLog("log-001", "qry-001", "prompt_received", "user", t1,
                Map.of("prompt", "question"), Map.of("redacted", false));
        AuditLog log2 = auditLog("log-002", "qry-001", "retrieval", "system", t2,
                Map.of("topK", 5), Map.of("hits", 3));
        AuditLog log3 = auditLog("log-003", "qry-001", "response_sent", "system", t3,
                Map.of("answer", "text"), Map.of("confidence", 0.85));

        // Repository returns already-ordered list (findByQueryIdOrderByTimestampAsc)
        when(auditLogRepository.findByQueryIdOrderByTimestampAsc("qry-001"))
                .thenReturn(List.of(log1, log2, log3));

        mockMvc.perform(get("/api/audit/qry-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query_id").value("qry-001"))
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.events.length()").value(3))
                .andExpect(jsonPath("$.events[0].event_type").value("prompt_received"))
                .andExpect(jsonPath("$.events[1].event_type").value("retrieval"))
                .andExpect(jsonPath("$.events[2].event_type").value("response_sent"));
    }

    @Test
    void getAuditLog_noEvents_returns200WithEmptyList() throws Exception {
        when(auditLogRepository.findByQueryIdOrderByTimestampAsc("qry-unknown"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/audit/qry-unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query_id").value("qry-unknown"))
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.events.length()").value(0));
    }

    @Test
    void getAuditLog_exposesActorAndInputOutput() throws Exception {
        AuditLog log = auditLog("log-004", "qry-002", "escalation", "system",
                OffsetDateTime.now(), Map.of("risk", "HIGH"), Map.of("itemId", "rev-001"));

        when(auditLogRepository.findByQueryIdOrderByTimestampAsc("qry-002"))
                .thenReturn(List.of(log));

        mockMvc.perform(get("/api/audit/qry-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].actor").value("system"))
                .andExpect(jsonPath("$.events[0].input.risk").value("HIGH"))
                .andExpect(jsonPath("$.events[0].output.itemId").value("rev-001"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AuditLog auditLog(String logId, String queryId, String eventType, String actor,
                               OffsetDateTime timestamp, Map<String, Object> input,
                               Map<String, Object> output) {
        AuditLog log = new AuditLog();
        log.setLogId(logId);
        log.setQueryId(queryId);
        log.setEventType(eventType);
        log.setActor(actor);
        log.setTimestamp(timestamp);
        log.setInputData(input);
        log.setOutputData(output);
        return log;
    }
}
