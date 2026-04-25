package com.policyguard.api.query.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record QueryAnsweredResponse(
        @JsonProperty("query_id")         String queryId,
        String status,
        String answer,
        List<CitationDto> citations,
        @JsonProperty("confidence_score") double confidenceScore,
        @JsonProperty("retrieval_hits")   int retrievalHits
) {}
