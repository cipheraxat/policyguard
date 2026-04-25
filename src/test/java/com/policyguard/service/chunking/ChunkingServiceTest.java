package com.policyguard.service.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChunkingServiceTest {

    private ChunkingService service;

    @BeforeEach
    void setUp() {
        // maxChars=50, overlap=10 for easier reasoning in tests
        service = new ChunkingService(50, 10);
    }

    // ── empty / blank ─────────────────────────────────────────────────────────

    @Test
    void emptyInput_returnsEmptyList() {
        assertThat(service.chunk("")).isEmpty();
        assertThat(service.chunk("   ")).isEmpty();
        assertThat(service.chunk(null)).isEmpty();
    }

    // ── section heading detection ─────────────────────────────────────────────

    @Test
    void numericSectionHeading_isDetectedAndRefFormatted() {
        String text = "1 Introduction\n\nThis is the first paragraph.\n\n2 Background\n\nSecond section body.";
        List<Chunk> chunks = service.chunk(text);

        // "1 Introduction" is a heading → ref "Section 1"
        // "This is the first paragraph." → ref "Section 1, Paragraph 1"
        // "2 Background" is a heading → ref "Section 2"
        // "Second section body." → ref "Section 2, Paragraph 1"
        assertThat(chunks).anySatisfy(c ->
                assertThat(c.paragraphRef()).isEqualTo("Section 1"));
        assertThat(chunks).anySatisfy(c ->
                assertThat(c.paragraphRef()).isEqualTo("Section 1, Paragraph 1"));
        assertThat(chunks).anySatisfy(c ->
                assertThat(c.paragraphRef()).isEqualTo("Section 2"));
        assertThat(chunks).anySatisfy(c ->
                assertThat(c.paragraphRef()).isEqualTo("Section 2, Paragraph 1"));
    }

    @Test
    void sectionKeywordHeading_isDetected() {
        String text = "Section 3.2 Data Retention\n\nRetain records for 7 years.";
        List<Chunk> chunks = service.chunk(text);

        assertThat(chunks).anySatisfy(c ->
                assertThat(c.paragraphRef()).startsWith("Section 3.2"));
    }

    @Test
    void nestedSectionNumber_isDetected() {
        String text = "10.4.1 Sub-section\n\nContent here.";
        List<Chunk> chunks = service.chunk(text);

        assertThat(chunks).anySatisfy(c ->
                assertThat(c.paragraphRef()).contains("10.4.1"));
    }

    // ── paragraph numbering ───────────────────────────────────────────────────

    @Test
    void paragraphsWithoutSection_numberedFromOne() {
        String text = "First paragraph text.\n\nSecond paragraph text.\n\nThird.";
        List<Chunk> chunks = service.chunk(text);

        assertThat(chunks).anySatisfy(c -> assertThat(c.paragraphRef()).isEqualTo("Paragraph 1"));
        assertThat(chunks).anySatisfy(c -> assertThat(c.paragraphRef()).isEqualTo("Paragraph 2"));
        assertThat(chunks).anySatisfy(c -> assertThat(c.paragraphRef()).isEqualTo("Paragraph 3"));
    }

    @Test
    void paragraphNumberingResetsOnNewSection() {
        String text = "1 First Section\n\nPara A.\n\nPara B.\n\n2 Second Section\n\nPara C.";
        List<Chunk> chunks = service.chunk(text);

        // "Para A." is Section 1, Paragraph 1; "Para B." is Section 1, Paragraph 2
        assertThat(chunks).anySatisfy(c ->
                assertThat(c.paragraphRef()).isEqualTo("Section 1, Paragraph 1"));
        assertThat(chunks).anySatisfy(c ->
                assertThat(c.paragraphRef()).isEqualTo("Section 1, Paragraph 2"));
        // "Para C." is Section 2, Paragraph 1 (counter reset)
        assertThat(chunks).anySatisfy(c ->
                assertThat(c.paragraphRef()).isEqualTo("Section 2, Paragraph 1"));
    }

    // ── long paragraph splitting ──────────────────────────────────────────────

    @Test
    void shortParagraph_fitsInOneChunk() {
        // maxChars=50; text is 20 chars → single chunk
        String text = "Short paragraph text.";
        List<Chunk> chunks = service.chunk(text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo("Short paragraph text.");
    }

    @Test
    void longParagraph_isSplitRespectingMaxChars() {
        // maxChars=50, so a 130-char paragraph should produce multiple chunks, each ≤ 50 chars
        String longText = "A".repeat(130);
        List<Chunk> chunks = service.chunk(longText);

        assertThat(chunks.size()).isGreaterThan(1);
        for (Chunk chunk : chunks) {
            assertThat(chunk.text().length()).isLessThanOrEqualTo(50);
        }
    }

    @Test
    void longParagraph_overlapApplied() {
        // maxChars=50, overlap=10, stride=40
        // "AAAA...A" (80 chars)
        // Chunk 1: [0,50), Chunk 2: [40,80) → first 10 chars of chunk 2 == last 10 of chunk 1
        String text = "A".repeat(80);
        List<Chunk> chunks = service.chunk(text);

        assertThat(chunks).hasSize(2);
        // The last 10 chars of chunk 1 should equal the first 10 chars of chunk 2
        String tail1 = chunks.get(0).text().substring(chunks.get(0).text().length() - 10);
        String head2 = chunks.get(1).text().substring(0, 10);
        assertThat(head2).isEqualTo(tail1);
    }

    @Test
    void longParagraph_allChunksInheritSameParagraphRef() {
        String text = "B".repeat(130);
        List<Chunk> chunks = service.chunk(text);

        assertThat(chunks).allSatisfy(c ->
                assertThat(c.paragraphRef()).isEqualTo("Paragraph 1"));
    }
}
