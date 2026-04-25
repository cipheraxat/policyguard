package com.policyguard.api.review.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReviewQueueResponse(
        List<ReviewQueueItemDto> items,
        @JsonProperty("total_pending") int totalPending
) {}
