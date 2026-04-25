package com.policyguard.service.review;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.policyguard.domain.ReviewQueueItem;
import com.policyguard.repository.ReviewQueueItemRepository;

@Service
public class ReviewQueueService {

    private static final Logger log = LoggerFactory.getLogger(ReviewQueueService.class);
    static final String REDIS_KEY = "review:queue";

    private static final Set<String> VALID_DECISIONS = Set.of("approved", "rejected", "overridden");

    private final ReviewQueueItemRepository reviewQueueItemRepository;
    private final StringRedisTemplate redisTemplate;

    public ReviewQueueService(ReviewQueueItemRepository reviewQueueItemRepository,
                              StringRedisTemplate redisTemplate) {
        this.reviewQueueItemRepository = reviewQueueItemRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public ReviewQueueItem enqueue(String queryId, String escalationReason, String riskCategory) {
        String itemId = "rev-" + UUID.randomUUID();

        ReviewQueueItem item = new ReviewQueueItem();
        item.setItemId(itemId);
        item.setQueryId(queryId);
        item.setEscalationReason(escalationReason);
        item.setRiskCategory(riskCategory);
        item.setStatus("pending");

        ReviewQueueItem saved = reviewQueueItemRepository.save(item);

        // Best-effort Redis push — Postgres is source of truth
        try {
            redisTemplate.opsForList().rightPush(REDIS_KEY, itemId);
        } catch (Exception e) {
            log.warn("Failed to push item {} to Redis queue: {}", itemId, e.getMessage());
        }

        return saved;
    }

    public List<ReviewQueueItem> listPending() {
        return reviewQueueItemRepository.findByStatusOrderByCreatedAtAsc("pending");
    }

    @Transactional
    public ReviewQueueItem resolve(String itemId, String reviewerId, String decision,
                                   String notes, String overrideAnswer) {
        ReviewQueueItem item = reviewQueueItemRepository.findByItemId(itemId)
                .orElseThrow(() -> new NoSuchElementException("Review queue item not found: " + itemId));

        if (!VALID_DECISIONS.contains(decision)) {
            throw new IllegalArgumentException("Invalid decision '" + decision
                    + "'; must be one of: approved, rejected, overridden");
        }

        if (!"pending".equals(item.getStatus())) {
            throw new IllegalStateException("already resolved");
        }

        if ("overridden".equals(decision) && (overrideAnswer == null || overrideAnswer.isBlank())) {
            throw new IllegalArgumentException("overrideAnswer is required for 'overridden' decision");
        }

        item.setStatus(decision);
        item.setReviewerId(reviewerId);
        // For overridden, store the reviewer's direct answer in reviewerNotes
        item.setReviewerNotes("overridden".equals(decision) ? overrideAnswer : notes);
        item.setResolvedAt(OffsetDateTime.now(ZoneOffset.UTC));

        ReviewQueueItem saved = reviewQueueItemRepository.save(item);

        // Best-effort Redis removal
        try {
            redisTemplate.opsForList().remove(REDIS_KEY, 0, itemId);
        } catch (Exception e) {
            log.warn("Failed to remove item {} from Redis queue: {}", itemId, e.getMessage());
        }

        return saved;
    }
}
