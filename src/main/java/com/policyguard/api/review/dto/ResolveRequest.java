package com.policyguard.api.review.dto;

public record ResolveRequest(
        String reviewerId,
        String decision,
        String notes,
        String overrideAnswer
) {}
