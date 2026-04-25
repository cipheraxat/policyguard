package com.policyguard.service.pii;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Redacts PII from text by calling Presidio and replacing detected spans
 * right-to-left (highest offset first) so earlier offsets remain valid
 * after each substitution.
 *
 * <p>Placeholders are deterministic so redacted text round-trips cleanly
 * (idempotent: running redact on already-redacted text produces the same result).
 */
@Service
public class PiiRedactionGateway {

    private final PresidioClient presidioClient;

    public PiiRedactionGateway(PresidioClient presidioClient) {
        this.presidioClient = presidioClient;
    }

    public RedactionResult redact(String text) {
        List<PresidioEntity> entities = presidioClient.analyze(text, "en");

        // Sort descending by start so we replace from right to left,
        // keeping earlier offsets stable.
        List<PresidioEntity> sorted = entities.stream()
                .sorted(Comparator.comparingInt(PresidioEntity::start).reversed())
                .toList();

        StringBuilder sb = new StringBuilder(text);
        for (PresidioEntity entity : sorted) {
            String placeholder = toPlaceholder(entity.entityType());
            sb.replace(entity.start(), entity.end(), placeholder);
        }

        List<Map<String, Object>> entitiesFound = entities.stream()
                .map(e -> Map.<String, Object>of(
                        "type", e.entityType(),
                        "start", e.start(),
                        "end", e.end()))
                .toList();

        return new RedactionResult(sb.toString(), entitiesFound, !entities.isEmpty());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    static String toPlaceholder(String entityType) {
        return switch (entityType) {
            case "PERSON"         -> "<PERSON>";
            case "EMAIL_ADDRESS"  -> "<EMAIL>";
            case "PHONE_NUMBER"   -> "<PHONE>";
            case "CREDIT_CARD"    -> "<CREDIT_CARD>";
            case "US_SSN"         -> "<SSN>";
            case "US_BANK_NUMBER" -> "<BANK_ACCOUNT>";
            case "IP_ADDRESS"     -> "<IP>";
            default               -> "<" + entityType + ">";
        };
    }
}
