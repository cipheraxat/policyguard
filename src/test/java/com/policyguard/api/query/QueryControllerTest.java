package com.policyguard.api.query;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyguard.service.citation.Citation;
import com.policyguard.service.query.QueryOutcome;
import com.policyguard.service.query.QueryService;

@WebMvcTest(QueryController.class)
class QueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private QueryService queryService;

    // ── 200 Answered ─────────────────────────────────────────────────────────

    @Test
    void postQuery_answered_returns200WithResponseShape() throws Exception {
        Citation citation = new Citation("chk-1", "doc-1", "Section 1, Paragraph 1", "Some text.");
        QueryOutcome.Answered answered = new QueryOutcome.Answered(
                "qry-abc123456789", "The policy is X.", List.of(citation), 0.87, 3);
        when(queryService.handle(any(), any(), isNull())).thenReturn(answered);

        String body = objectMapper.writeValueAsString(
                new QueryBody("What is the leave policy?", "user-1", null));

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query_id").value("qry-abc123456789"))
                .andExpect(jsonPath("$.status").value("answered"))
                .andExpect(jsonPath("$.answer").value("The policy is X."))
                .andExpect(jsonPath("$.confidence_score").value(0.87))
                .andExpect(jsonPath("$.retrieval_hits").value(3))
                .andExpect(jsonPath("$.citations[0].chunk_id").value("chk-1"))
                .andExpect(jsonPath("$.citations[0].document_id").value("doc-1"))
                .andExpect(jsonPath("$.citations[0].paragraph_ref").value("Section 1, Paragraph 1"))
                .andExpect(jsonPath("$.citations[0].text_snippet").value("Some text."));
    }

    // ── 200 Refused ──────────────────────────────────────────────────────────

    @Test
    void postQuery_refused_returns200WithResponseShape() throws Exception {
        QueryOutcome.Refused refused = new QueryOutcome.Refused(
                "qry-xyz987654321",
                "No relevant documents found",
                "I cannot answer this question based on the available policy documents.");
        when(queryService.handle(any(), any(), isNull())).thenReturn(refused);

        String body = objectMapper.writeValueAsString(
                new QueryBody("Random unknown topic", "user-2", null));

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query_id").value("qry-xyz987654321"))
                .andExpect(jsonPath("$.status").value("refused"))
                .andExpect(jsonPath("$.reason").value("No relevant documents found"))
                .andExpect(jsonPath("$.message").exists());
    }

    // ── 202 Escalated ────────────────────────────────────────────────────────

    @Test
    void postQuery_escalated_returns202WithResponseShape() throws Exception {
        QueryOutcome.Escalated escalated = new QueryOutcome.Escalated(
                "qry-esc111111111",
                "regulatory_interpretation detected",
                "rev-abc00001",
                "This question has been routed to the compliance team for review.");
        when(queryService.handle(any(), any(), isNull())).thenReturn(escalated);

        String body = objectMapper.writeValueAsString(
                new QueryBody("Interpret GDPR article 17 for us", "user-3", null));

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.query_id").value("qry-esc111111111"))
                .andExpect(jsonPath("$.status").value("escalated"))
                .andExpect(jsonPath("$.review_item_id").value("rev-abc00001"))
                .andExpect(jsonPath("$.message").exists());
    }

    // ── 400 blank question ───────────────────────────────────────────────────

    @Test
    void postQuery_blankQuestion_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                new QueryBody("", "user-1", null));

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postQuery_blankUserId_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                new QueryBody("What is the policy?", "", null));

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    /** Local record for test request serialisation. */
    record QueryBody(String question, String userId, Object filters) {}
}
