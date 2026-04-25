package com.policyguard.service.pii;

import java.util.List;
import java.util.Map;

/**
 * Result of a PII redaction pass.
 *
 * @param redactedText     the input text with PII replaced by typed placeholders
 * @param entitiesFound    each entity as a map with keys {@code type}, {@code start}, {@code end}
 * @param wasRedacted      true if at least one entity was replaced
 */
public record RedactionResult(
        String redactedText,
        List<Map<String, Object>> entitiesFound,
        boolean wasRedacted
) {}
