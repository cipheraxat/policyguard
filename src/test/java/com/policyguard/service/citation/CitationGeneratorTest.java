package com.policyguard.service.citation;

import java.util.List;
import java.util.Map;

import com.policyguard.config.PolicyguardProperties;
import com.policyguard.service.retrieval.RetrievalHit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

@ExtendWith(MockitoExtension.class)
class CitationGeneratorTest {

    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec requestSpec;
    @Mock ChatClient.CallResponseSpec callSpec;

    private CitationGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new CitationGenerator(chatClient, new PolicyguardProperties());
        // Wire the fluent ChatClient chain
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
    }

    private static RetrievalHit hit(String chunkId, String docId, String para, String text, double score) {
        return new RetrievalHit(chunkId, docId, para, text, score, Map.of());
    }

    @Test
    void parsesVerifiableCitations_correctly() {
        List<RetrievalHit> hits = List.of(
                hit("chk-1", "POL-001", "Section 3.1, Paragraph 2",
                        "Customer PII must be retained for 7 years.", 0.90));

        when(callSpec.content()).thenReturn(
                "PII must be retained for 7 years [Doc: POL-001, Para: Section 3.1, Paragraph 2].");

        CitationResult result = generator.generate("Retention policy?", hits);

        assertThat(result.citations()).hasSize(1);
        Citation c = result.citations().get(0);
        assertThat(c.chunkId()).isEqualTo("chk-1");
        assertThat(c.documentId()).isEqualTo("POL-001");
        assertThat(c.paragraphRef()).isEqualTo("Section 3.1, Paragraph 2");
        assertThat(c.textSnippet()).contains("retained for 7 years");
    }

    @Test
    void unverifiableCitation_hasNullChunkId() {
        List<RetrievalHit> hits = List.of(
                hit("chk-1", "POL-001", "Section 1", "some text", 0.80));

        // LLM references a (doc, para) pair that is not in hits
        when(callSpec.content()).thenReturn(
                "See policy [Doc: POL-999, Para: Section 99].");

        CitationResult result = generator.generate("question", hits);

        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).chunkId()).isNull();
        assertThat(result.citations().get(0).documentId()).isEqualTo("POL-999");
    }

    @Test
    void confidence_threeHits_meanOfTop3() {
        List<RetrievalHit> hits = List.of(
                hit("c1", "D1", "P1", "t1", 0.90),
                hit("c2", "D1", "P2", "t2", 0.80),
                hit("c3", "D1", "P3", "t3", 0.70));

        when(callSpec.content()).thenReturn("answer with no citations");

        CitationResult result = generator.generate("q", hits);

        // mean(0.90, 0.80, 0.70) = 0.80
        assertThat(result.confidence()).isCloseTo(0.80, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void confidence_oneHit_thatHitsScore() {
        List<RetrievalHit> hits = List.of(hit("c1", "D1", "P1", "t", 0.72));

        when(callSpec.content()).thenReturn("answer");

        CitationResult result = generator.generate("q", hits);

        assertThat(result.confidence()).isCloseTo(0.72, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void confidence_zeroHits_returnsZero() {
        when(callSpec.content()).thenReturn("I cannot answer this based on the available policy documents.");

        CitationResult result = generator.generate("q", List.of());

        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void confidence_moreThanThreeHits_onlyTop3Counted() {
        List<RetrievalHit> hits = List.of(
                hit("c1", "D1", "P1", "t1", 0.90),
                hit("c2", "D1", "P2", "t2", 0.80),
                hit("c3", "D1", "P3", "t3", 0.70),
                hit("c4", "D1", "P4", "t4", 0.10));  // this should NOT be counted

        when(callSpec.content()).thenReturn("answer");

        CitationResult result = generator.generate("q", hits);

        // mean(0.90, 0.80, 0.70) = 0.80 (hit 4 excluded)
        assertThat(result.confidence()).isCloseTo(0.80, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void textSnippet_truncatedAt200Chars() {
        String longText = "A".repeat(300);
        List<RetrievalHit> hits = List.of(hit("chk-1", "DOC-1", "Sec 1", longText, 0.9));

        when(callSpec.content()).thenReturn("[Doc: DOC-1, Para: Sec 1]");

        CitationResult result = generator.generate("q", hits);

        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).textSnippet()).hasSize(200);
    }

    @Test
    void responseText_isPassedThroughUnmodified() {
        when(callSpec.content()).thenReturn("The answer is 42.");

        CitationResult result = generator.generate("q", List.of());

        assertThat(result.responseText()).isEqualTo("The answer is 42.");
    }

    @Test
    void hitsRetained_inCitationResult() {
        List<RetrievalHit> hits = List.of(hit("c1", "D1", "P1", "text", 0.85));
        when(callSpec.content()).thenReturn("answer");

        CitationResult result = generator.generate("q", hits);

        assertThat(result.hits()).isSameAs(hits);
    }

    @Test
    void multipleCitations_allParsed() {
        List<RetrievalHit> hits = List.of(
                hit("c1", "DOC-1", "Sec 1", "text1", 0.9),
                hit("c2", "DOC-2", "Sec 2", "text2", 0.8));

        when(callSpec.content()).thenReturn(
                "First point [Doc: DOC-1, Para: Sec 1]. Second point [Doc: DOC-2, Para: Sec 2].");

        CitationResult result = generator.generate("q", hits);

        assertThat(result.citations()).hasSize(2);
        assertThat(result.citations().get(0).chunkId()).isEqualTo("c1");
        assertThat(result.citations().get(1).chunkId()).isEqualTo("c2");
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvSource(delimiter = '|', value = {
            "[Doc: POL-001, Para: Section 3.1]                       | true  | exact match",
            "[Doc:  POL-001 ,  Para:  Section 3.1 ]                  | true  | extra whitespace inside tag",
            "[Doc:POL-001,Para:Section 3.1]                          | true  | no whitespace inside tag",
            "Some prose [Doc: POL-001, Para: Section 3.1] more text  | true  | tag embedded in prose",
            "[Doc: pol-001, Para: Section 3.1]                       | false | docId case mismatch (strict)",
            "[Doc: POL-001, Para: section 3.1]                       | false | paraRef case mismatch (strict)",
            "[Doc: POL-002, Para: Section 3.1]                       | false | unknown docId"
    })
    void citationDriftBehavior_isStrictOnCaseLenientOnWhitespace(
            String llmOutput, boolean expectedVerified, String description) {
        List<RetrievalHit> hits = List.of(
                hit("chk-1", "POL-001", "Section 3.1", "text", 0.9));

        when(callSpec.content()).thenReturn(llmOutput);

        CitationResult result = generator.generate("q", hits);

        assertThat(result.citations())
                .as("expected one citation parsed for: %s", description)
                .hasSize(1);
        Citation c = result.citations().get(0);
        if (expectedVerified) {
            assertThat(c.chunkId()).as(description).isEqualTo("chk-1");
        } else {
            assertThat(c.chunkId()).as(description).isNull();
        }
    }

    @Test
    void citationWithExtraWhitespace_isMatchedByTrimming() {
        List<RetrievalHit> hits = List.of(
                hit("chk-1", "POL-001", "Section 3.1", "text", 0.9));

        // LLM may include extra spaces in the citation tag
        when(callSpec.content()).thenReturn("[Doc:  POL-001 ,  Para:  Section 3.1 ]");

        CitationResult result = generator.generate("q", hits);

        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).chunkId()).isEqualTo("chk-1");
    }
}
