package com.policyguard.api.review.dto;

import java.time.OffsetDateTime;

public record ResolveResponse(
        String itemId,
        String status,
        String finalAnswer,
        OffsetDateTime resolvedAt
) {}
