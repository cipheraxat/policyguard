package com.policyguard.api.query.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record QueryRefusedResponse(
        @JsonProperty("query_id") String queryId,
        String status,
        String reason,
        String message
) {}
