package com.policyguard.service.retrieval;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyguard.config.PolicyguardProperties;
import com.policyguard.repository.DocumentChunkRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

@ExtendWith(MockitoExtension.class)
class HybridRetrieverTest {

    @Mock EmbeddingModel embeddingModel;
    @Mock DocumentChunkRepository chunkRepository;

    private HybridRetriever retriever;
    private PolicyguardProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PolicyguardProperties();
        properties.getRetrieval().setTopK(5);
        properties.getRetrieval().setRrfK(60);

        retriever = new HybridRetriever(
                embeddingModel,
                chunkRepository,
                new RrfFusionService(),
                new ObjectMapper(),
                properties);
    }

    /** Creates a mock Object[] row as returned by semanticSearch / keywordSearch. */
    private static Object[] row(String chunkId, String docId, String para, String text,
                                 double score, String metadataJson) {
        return new Object[]{chunkId, docId, para, text, score, metadataJson};
    }

    @Test
    void embeddingLiteralFormat_isCorrectPgvectorSyntax() {
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingModel.embed(anyString())).thenReturn(embedding);
        when(chunkRepository.semanticSearch(anyString(), anyInt())).thenReturn(List.of());
        when(chunkRepository.keywordSearch(anyString(), anyInt())).thenReturn(List.of());

        retriever.retrieve("query", null, 3);

        ArgumentCaptor<String> literalCaptor = ArgumentCaptor.forClass(String.class);
        verify(chunkRepository).semanticSearch(literalCaptor.capture(), anyInt());

        String literal = literalCaptor.getValue();
        assertThat(literal).startsWith("[");
        assertThat(literal).endsWith("]");
        assertThat(literal).contains("0.1");
        assertThat(literal).contains("0.2");
        assertThat(literal).contains("0.3");
        assertThat(literal).doesNotContain(" ");
    }

    @Test
    void candidateKIsTopKTimesTwo() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.5f});
        when(chunkRepository.semanticSearch(anyString(), anyInt())).thenReturn(List.of());
        when(chunkRepository.keywordSearch(anyString(), anyInt())).thenReturn(List.of());

        retriever.retrieve("query", null, 3);

        verify(chunkRepository).semanticSearch(anyString(), eq(6));
        verify(chunkRepository).keywordSearch(anyString(), eq(6));
    }

    @Test
    void metadataJsonParsed_andAvailableOnHit() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.5f});
        when(chunkRepository.semanticSearch(anyString(), anyInt())).thenReturn(
                List.<Object[]>of(row("chk-1", "DOC-1", "Sec 1", "text", 0.85, "{\"doc_type\":\"policy\"}")));
        when(chunkRepository.keywordSearch(anyString(), anyInt())).thenReturn(List.of());

        List<RetrievalHit> hits = retriever.retrieve("query", null, 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).chunkId()).isEqualTo("chk-1");
        assertThat(hits.get(0).documentId()).isEqualTo("DOC-1");
        assertThat(hits.get(0).score()).isEqualTo(0.85);
        assertThat(hits.get(0).metadata()).containsEntry("doc_type", "policy");
    }

    @Test
    void nullMetadata_parsedToEmptyMap() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.5f});
        when(chunkRepository.semanticSearch(anyString(), anyInt())).thenReturn(
                List.<Object[]>of(row("chk-1", "DOC-1", "Sec 1", "text", 0.85, null)));
        when(chunkRepository.keywordSearch(anyString(), anyInt())).thenReturn(List.of());

        List<RetrievalHit> hits = retriever.retrieve("query", null, 5);

        assertThat(hits.get(0).metadata()).isEmpty();
    }

    @Test
    void topKTrimming_limitsResults() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.5f});
        // Provide 4 semantic hits, topK=2 → expect 2 results
        when(chunkRepository.semanticSearch(anyString(), anyInt())).thenReturn(List.of(
                row("c1", "D1", "P1", "t1", 0.9, null),
                row("c2", "D1", "P2", "t2", 0.8, null),
                row("c3", "D1", "P3", "t3", 0.7, null),
                row("c4", "D1", "P4", "t4", 0.6, null)));
        when(chunkRepository.keywordSearch(anyString(), anyInt())).thenReturn(List.of());

        List<RetrievalHit> hits = retriever.retrieve("query", null, 2);

        assertThat(hits).hasSize(2);
    }

    @Test
    void documentIdFilter_excludesNonMatchingHits() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.5f});
        when(chunkRepository.semanticSearch(anyString(), anyInt())).thenReturn(List.of(
                row("c1", "DOC-KEEP", "P1", "t1", 0.9, null),
                row("c2", "DOC-DROP", "P2", "t2", 0.8, null)));
        when(chunkRepository.keywordSearch(anyString(), anyInt())).thenReturn(List.of());

        List<RetrievalHit> hits = retriever.retrieve("query", Map.of("documentId", "DOC-KEEP"), 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).documentId()).isEqualTo("DOC-KEEP");
    }

    @Test
    void metadataFilter_excludesNonMatchingHits() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.5f});
        when(chunkRepository.semanticSearch(anyString(), anyInt())).thenReturn(List.of(
                row("c1", "D1", "P1", "t1", 0.9, "{\"doc_type\":\"policy\"}"),
                row("c2", "D1", "P2", "t2", 0.8, "{\"doc_type\":\"sop\"}")));
        when(chunkRepository.keywordSearch(anyString(), anyInt())).thenReturn(List.of());

        List<RetrievalHit> hits = retriever.retrieve("query", Map.of("doc_type", "policy"), 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).chunkId()).isEqualTo("c1");
    }

    @Test
    void keywordOnlyHit_getsRrfScoreNotTsRank() {
        // chk-semantic is in semantic list; chk-kw is keyword-only
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.5f});
        when(chunkRepository.semanticSearch(anyString(), anyInt())).thenReturn(
                List.<Object[]>of(row("chk-semantic", "D1", "P1", "t1", 0.90, null)));
        when(chunkRepository.keywordSearch(anyString(), anyInt())).thenReturn(
                List.<Object[]>of(row("chk-kw", "D1", "P2", "t2", 0.15, null)));  // ts_rank = 0.15

        List<RetrievalHit> hits = retriever.retrieve("query", null, 5);

        RetrievalHit kwHit = hits.stream().filter(h -> "chk-kw".equals(h.chunkId())).findFirst().orElseThrow();
        // Score should be RRF(1, 60) = 1/61 ≈ 0.01639, not ts_rank 0.15
        double expectedRrf = 1.0 / (60 + 1);
        assertThat(kwHit.score()).isCloseTo(expectedRrf, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void semanticHit_keepsCosineSimilarityScore() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.5f});
        when(chunkRepository.semanticSearch(anyString(), anyInt())).thenReturn(
                List.<Object[]>of(row("chk-1", "D1", "P1", "text", 0.87, null)));
        when(chunkRepository.keywordSearch(anyString(), anyInt())).thenReturn(List.of());

        List<RetrievalHit> hits = retriever.retrieve("query", null, 5);

        assertThat(hits.get(0).score()).isEqualTo(0.87);
    }

    @Test
    void emptyResults_fromBothSearches_returnsEmptyList() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.5f});
        when(chunkRepository.semanticSearch(anyString(), anyInt())).thenReturn(List.of());
        when(chunkRepository.keywordSearch(anyString(), anyInt())).thenReturn(List.of());

        assertThat(retriever.retrieve("query", null, 5)).isEmpty();
    }
}
