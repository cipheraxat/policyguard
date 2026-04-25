package com.policyguard.api.review;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyguard.domain.Query;
import com.policyguard.domain.ReviewQueueItem;
import com.policyguard.repository.QueryRepository;
import com.policyguard.service.citation.Citation;
import com.policyguard.service.query.QueryOutcome;
import com.policyguard.service.query.QueryService;
import com.policyguard.service.review.ReviewQueueService;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReviewQueueService reviewQueueService;

    @MockBean
    private QueryRepository queryRepository;

    @MockBean
    private QueryService queryService;

    @Test
    void listPending_returnsItemsWithOriginalQuestion() throws Exception {
        ReviewQueueItem item = pendingItem("rev-abc12345", "qry-001");

        Query query = new Query();
        query.setQueryId("qry-001");
        query.setOriginalPrompt("What is the GDPR policy?");
        query.setStatus("escalated");

        when(reviewQueueService.listPending()).thenReturn(List.of(item));
        when(queryRepository.findByQueryId("qry-001")).thenReturn(Optional.of(query));

        mockMvc.perform(get("/api/review-queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_pending").value(1))
                .andExpect(jsonPath("$.items[0].itemId").value("rev-abc12345"))
                .andExpect(jsonPath("$.items[0].queryId").value("qry-001"))
                .andExpect(jsonPath("$.items[0].originalQuestion").value("What is the GDPR policy?"))
                .andExpect(jsonPath("$.items[0].escalationReason").value("regulatory_interpretation"))
                .andExpect(jsonPath("$.items[0].status").value("pending"));
    }

    @Test
    void resolve_missingReviewerHeader_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(
                new ResolveRequestBody("reviewer-1", "approved", null, null));

        mockMvc.perform(post("/api/review/rev-abc12345/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resolve_mismatchedReviewerHeader_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(
                new ResolveRequestBody("reviewer-1", "approved", null, null));

        mockMvc.perform(post("/api/review/rev-abc12345/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Reviewer-Id", "reviewer-WRONG")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resolve_invalidDecision_returns400() throws Exception {
        when(reviewQueueService.resolve(any(), any(), eq("BADVALUE"), any(), any()))
                .thenThrow(new IllegalArgumentException("Invalid decision"));

        String body = objectMapper.writeValueAsString(
                new ResolveRequestBody("reviewer-1", "BADVALUE", null, null));

        mockMvc.perform(post("/api/review/rev-abc12345/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Reviewer-Id", "reviewer-1")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resolve_alreadyResolved_returns400() throws Exception {
        when(reviewQueueService.resolve(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("already resolved"));

        String body = objectMapper.writeValueAsString(
                new ResolveRequestBody("reviewer-1", "rejected", null, null));

        mockMvc.perform(post("/api/review/rev-abc12345/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Reviewer-Id", "reviewer-1")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resolve_itemNotFound_returns404() throws Exception {
        when(reviewQueueService.resolve(any(), any(), any(), any(), any()))
                .thenThrow(new NoSuchElementException("not found"));

        String body = objectMapper.writeValueAsString(
                new ResolveRequestBody("reviewer-1", "approved", null, null));

        mockMvc.perform(post("/api/review/rev-missing/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Reviewer-Id", "reviewer-1")
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolve_approved_returnsPipelineAnswer() throws Exception {
        ReviewQueueItem item = resolvedItem("rev-abc12345", "approved", null);
        when(reviewQueueService.resolve(any(), any(), eq("approved"), any(), any()))
                .thenReturn(item);

        Query query = new Query();
        query.setQueryId("qry-001");
        query.setOriginalPrompt("What is the GDPR policy?");
        query.setStatus("escalated");
        when(queryRepository.findByQueryId("qry-001")).thenReturn(Optional.of(query));

        QueryOutcome.Answered answered = new QueryOutcome.Answered(
                "qry-001", "The policy requires X.", List.of(), 0.85, 3);
        when(queryService.handleApproved(eq("qry-001"), eq("What is the GDPR policy?"),
                eq("reviewer-1"))).thenReturn(answered);

        String body = objectMapper.writeValueAsString(
                new ResolveRequestBody("reviewer-1", "approved", "looks good", null));

        mockMvc.perform(post("/api/review/rev-abc12345/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Reviewer-Id", "reviewer-1")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value("rev-abc12345"))
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.finalAnswer").value("The policy requires X."));
    }

    @Test
    void resolve_overridden_returnsFinalAnswer() throws Exception {
        ReviewQueueItem item = resolvedItem("rev-abc12345", "overridden", "The policy requires X.");
        when(reviewQueueService.resolve(any(), any(), eq("overridden"), any(), any()))
                .thenReturn(item);

        String body = objectMapper.writeValueAsString(
                new ResolveRequestBody("reviewer-1", "overridden", null, "The policy requires X."));

        mockMvc.perform(post("/api/review/rev-abc12345/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Reviewer-Id", "reviewer-1")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("overridden"))
                .andExpect(jsonPath("$.finalAnswer").value("The policy requires X."));
    }

    @Test
    void resolve_rejected_returnsFinalAnswerNull() throws Exception {
        ReviewQueueItem item = resolvedItem("rev-abc12345", "rejected", null);
        when(reviewQueueService.resolve(any(), any(), eq("rejected"), any(), any()))
                .thenReturn(item);

        String body = objectMapper.writeValueAsString(
                new ResolveRequestBody("reviewer-1", "rejected", "not appropriate", null));

        mockMvc.perform(post("/api/review/rev-abc12345/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Reviewer-Id", "reviewer-1")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.finalAnswer").doesNotExist());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ReviewQueueItem pendingItem(String itemId, String queryId) {
        ReviewQueueItem item = new ReviewQueueItem();
        item.setItemId(itemId);
        item.setQueryId(queryId);
        item.setEscalationReason("regulatory_interpretation");
        item.setRiskCategory("HIGH");
        item.setStatus("pending");
        return item;
    }

    private ReviewQueueItem resolvedItem(String itemId, String status, String reviewerNotes) {
        ReviewQueueItem item = new ReviewQueueItem();
        item.setItemId(itemId);
        item.setQueryId("qry-001");
        item.setEscalationReason("regulatory_interpretation");
        item.setRiskCategory("HIGH");
        item.setStatus(status);
        item.setReviewerId("reviewer-1");
        item.setReviewerNotes(reviewerNotes);
        item.setResolvedAt(OffsetDateTime.now());
        return item;
    }

    /** Local record used only for test request serialisation. */
    record ResolveRequestBody(String reviewerId, String decision, String notes, String overrideAnswer) {}
}
