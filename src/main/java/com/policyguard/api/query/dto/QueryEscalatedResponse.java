package com.policyguard.api.query.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record QueryEscalatedResponse(
        @JsonProperty("query_id")        String queryId,
        String status,
        String reason,
        @JsonProperty("review_item_id")  String reviewItemId,
        String message
) {}
