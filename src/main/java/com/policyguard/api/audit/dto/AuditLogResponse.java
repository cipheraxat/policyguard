package com.policyguard.api.audit.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuditLogResponse(
        @JsonProperty("query_id") String queryId,
        List<AuditEventDto> events
) {}
