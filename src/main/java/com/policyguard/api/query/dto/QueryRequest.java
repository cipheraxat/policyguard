package com.policyguard.api.query.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record QueryRequest(
        @NotBlank String question,
        @NotBlank String userId,
        Map<String, Object> filters
) {}
