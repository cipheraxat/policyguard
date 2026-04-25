package com.policyguard.api.audit.dto;

import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuditEventDto(
        @JsonProperty("event_type") String eventType,
        OffsetDateTime timestamp,
        String actor,
        Map<String, Object> input,
        Map<String, Object> output
) {}
