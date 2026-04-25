package com.policyguard.service.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.policyguard.domain.ReviewQueueItem;
import com.policyguard.repository.ReviewQueueItemRepository;

@ExtendWith(MockitoExtension.class)
class ReviewQueueServiceTest {

    @Mock
    private ReviewQueueItemRepository repository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    private ReviewQueueService service;

    @BeforeEach
    void setUp() {
        // Lenient: some tests throw before reaching Redis; avoid UnnecessaryStubbingException
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        service = new ReviewQueueService(repository, redisTemplate);
    }

    @Test
    void enqueue_savesEntityWithPendingStatus_andPushesToRedis() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReviewQueueItem result = service.enqueue("qry-001", "regulatory_interpretation", "HIGH");

        assertThat(result.getItemId()).startsWith("rev-");
        assertThat(result.getStatus()).isEqualTo("pending");
        assertThat(result.getQueryId()).isEqualTo("qry-001");
        assertThat(result.getEscalationReason()).isEqualTo("regulatory_interpretation");
        assertThat(result.getRiskCategory()).isEqualTo("HIGH");
        verify(repository).save(any(ReviewQueueItem.class));
        verify(listOperations).rightPush(eq(ReviewQueueService.REDIS_KEY), anyString());
    }

    @Test
    void enqueue_redisFailure_logsWarningButDoesNotAbort() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Redis connection refused"))
                .when(listOperations).rightPush(anyString(), anyString());

        // Should not throw despite Redis failure
        ReviewQueueItem result = service.enqueue("qry-002", "policy_exception", "HIGH");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("pending");
        verify(repository).save(any());
    }

    @Test
    void resolve_approved_updatesStatusAndReturnsItem() {
        ReviewQueueItem item = pendingItem("rev-abc12345", "qry-003");
        when(repository.findByItemId("rev-abc12345")).thenReturn(Optional.of(item));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReviewQueueItem result = service.resolve("rev-abc12345", "reviewer-1", "approved", "looks good", null);

        assertThat(result.getStatus()).isEqualTo("approved");
        assertThat(result.getReviewerId()).isEqualTo("reviewer-1");
        assertThat(result.getResolvedAt()).isNotNull();
        verify(listOperations).remove(eq(ReviewQueueService.REDIS_KEY), anyLong(), eq("rev-abc12345"));
    }

    @Test
    void resolve_rejected_updatesStatus() {
        ReviewQueueItem item = pendingItem("rev-def12345", "qry-004");
        when(repository.findByItemId("rev-def12345")).thenReturn(Optional.of(item));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReviewQueueItem result = service.resolve("rev-def12345", "reviewer-2", "rejected", "not appropriate", null);

        assertThat(result.getStatus()).isEqualTo("rejected");
        assertThat(result.getReviewerNotes()).isEqualTo("not appropriate");
    }

    @Test
    void resolve_overridden_storesOverrideAnswerInNotes() {
        ReviewQueueItem item = pendingItem("rev-ghi12345", "qry-005");
        when(repository.findByItemId("rev-ghi12345")).thenReturn(Optional.of(item));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReviewQueueItem result = service.resolve("rev-ghi12345", "reviewer-3", "overridden",
                "some notes", "The policy requires X.");

        assertThat(result.getStatus()).isEqualTo("overridden");
        // overrideAnswer stored in reviewerNotes for overridden decisions
        assertThat(result.getReviewerNotes()).isEqualTo("The policy requires X.");
    }

    @Test
    void resolve_overridden_requiresNonBlankOverrideAnswer() {
        ReviewQueueItem item = pendingItem("rev-jkl12345", "qry-006");
        when(repository.findByItemId("rev-jkl12345")).thenReturn(Optional.of(item));

        assertThatThrownBy(() ->
                service.resolve("rev-jkl12345", "reviewer-4", "overridden", "notes", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overrideAnswer");

        assertThatThrownBy(() ->
                service.resolve("rev-jkl12345", "reviewer-4", "overridden", "notes", "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void resolve_alreadyResolved_throwsIllegalStateException() {
        ReviewQueueItem item = pendingItem("rev-mno12345", "qry-007");
        item.setStatus("approved");
        when(repository.findByItemId("rev-mno12345")).thenReturn(Optional.of(item));

        assertThatThrownBy(() ->
                service.resolve("rev-mno12345", "reviewer-5", "rejected", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already resolved");
    }

    @Test
    void resolve_itemNotFound_throwsNoSuchElementException() {
        when(repository.findByItemId("rev-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.resolve("rev-missing", "reviewer-6", "approved", null, null))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void resolve_invalidDecision_throwsIllegalArgumentException() {
        ReviewQueueItem item = pendingItem("rev-pqr12345", "qry-008");
        when(repository.findByItemId("rev-pqr12345")).thenReturn(Optional.of(item));

        assertThatThrownBy(() ->
                service.resolve("rev-pqr12345", "reviewer-7", "INVALID", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid decision");
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
}
