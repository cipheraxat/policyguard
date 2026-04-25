package com.policyguard.service.pii;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PiiRedactionGatewayTest {

    @Mock
    private PresidioClient presidioClient;

    private PiiRedactionGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new PiiRedactionGateway(presidioClient);
    }

    // ── right-to-left offset preservation ────────────────────────────────────

    @Test
    void replacesEntitiesRightToLeft_preservingOffsets() {
        // "Hello John Smith, call 555-1234"
        //  0    6   10     17    24
        String text = "Hello John Smith, call 555-1234";
        // John Smith: [6,16), phone: [22,30)
        when(presidioClient.analyze(eq(text), anyString())).thenReturn(List.of(
                new PresidioEntity("PERSON",       6, 16, 0.9),
                new PresidioEntity("PHONE_NUMBER", 23, 31, 0.85)
        ));

        RedactionResult result = gateway.redact(text);

        assertThat(result.redactedText()).isEqualTo("Hello <PERSON>, call <PHONE>");
        assertThat(result.wasRedacted()).isTrue();
        assertThat(result.entitiesFound()).hasSize(2);
    }

    @Test
    void replacesAdjacentEntities_correctlyWithRightToLeft() {
        // "JohnDoe" — two adjacent entity spans: [0,4) PERSON and [4,7) PERSON (contrived)
        String text = "JohnDoe";
        when(presidioClient.analyze(eq(text), anyString())).thenReturn(List.of(
                new PresidioEntity("PERSON", 0, 4, 0.9),
                new PresidioEntity("PERSON", 4, 7, 0.9)
        ));

        RedactionResult result = gateway.redact(text);

        // Right-to-left: replace [4,7) first → "John<PERSON>", then [0,4) → "<PERSON><PERSON>"
        assertThat(result.redactedText()).isEqualTo("<PERSON><PERSON>");
    }

    // ── placeholder mapping ───────────────────────────────────────────────────

    @Test
    void mapsAllKnownEntityTypes() {
        assertThat(PiiRedactionGateway.toPlaceholder("PERSON"))         .isEqualTo("<PERSON>");
        assertThat(PiiRedactionGateway.toPlaceholder("EMAIL_ADDRESS"))  .isEqualTo("<EMAIL>");
        assertThat(PiiRedactionGateway.toPlaceholder("PHONE_NUMBER"))   .isEqualTo("<PHONE>");
        assertThat(PiiRedactionGateway.toPlaceholder("CREDIT_CARD"))    .isEqualTo("<CREDIT_CARD>");
        assertThat(PiiRedactionGateway.toPlaceholder("US_SSN"))         .isEqualTo("<SSN>");
        assertThat(PiiRedactionGateway.toPlaceholder("US_BANK_NUMBER")) .isEqualTo("<BANK_ACCOUNT>");
        assertThat(PiiRedactionGateway.toPlaceholder("IP_ADDRESS"))     .isEqualTo("<IP>");
    }

    @Test
    void unknownEntityType_usesGenericPlaceholder() {
        String text = "My IBAN is DE89370400440532013000";
        when(presidioClient.analyze(eq(text), anyString())).thenReturn(List.of(
                new PresidioEntity("IBAN_CODE", 11, 33, 0.8)
        ));

        RedactionResult result = gateway.redact(text);

        assertThat(result.redactedText()).isEqualTo("My IBAN is <IBAN_CODE>");
        assertThat(PiiRedactionGateway.toPlaceholder("IBAN_CODE")).isEqualTo("<IBAN_CODE>");
    }

    // ── idempotency ───────────────────────────────────────────────────────────

    @Test
    void idempotent_alreadyRedactedTextProducesSameResult() {
        // First pass: Presidio detects an entity
        String original = "Contact John at john@example.com";
        String afterFirstRedaction = "Contact <PERSON> at <EMAIL>";

        // Second pass: Presidio finds nothing in already-redacted text
        when(presidioClient.analyze(eq(afterFirstRedaction), anyString()))
                .thenReturn(List.of());

        RedactionResult secondPass = gateway.redact(afterFirstRedaction);

        assertThat(secondPass.redactedText()).isEqualTo(afterFirstRedaction);
        assertThat(secondPass.wasRedacted()).isFalse();
        assertThat(secondPass.entitiesFound()).isEmpty();
    }

    // ── empty / no PII ────────────────────────────────────────────────────────

    @Test
    void noEntities_returnsUnchangedText() {
        String text = "This document has no PII.";
        when(presidioClient.analyze(eq(text), anyString())).thenReturn(List.of());

        RedactionResult result = gateway.redact(text);

        assertThat(result.redactedText()).isEqualTo(text);
        assertThat(result.wasRedacted()).isFalse();
    }
}
