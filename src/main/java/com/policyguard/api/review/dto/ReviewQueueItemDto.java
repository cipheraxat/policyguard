package com.policyguard.api.review.dto;

import java.time.OffsetDateTime;

public record ReviewQueueItemDto(
        String itemId,
        String queryId,
        String originalQuestion,
        String escalationReason,
        String riskCategory,
        String status,
        OffsetDateTime createdAt
) {}
