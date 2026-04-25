package com.policyguard.service.pii;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single entity detected by the Presidio Analyzer REST API.
 */
public record PresidioEntity(
        @JsonProperty("entity_type") String entityType,
        int start,
        int end,
        double score
) {}
