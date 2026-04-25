package com.policyguard.service.retrieval;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RrfFusionServiceTest {

    private RrfFusionService service;

    @BeforeEach
    void setUp() {
        service = new RrfFusionService();
    }

    private static RetrievalHit hit(String chunkId, double score) {
        return new RetrievalHit(chunkId, "doc-1", "Para 1", "text", score, Map.of());
    }

    @Test
    void fuseTwoDisjointLists_orderedByRrfScore() {
        // List a: A at rank 1, B at rank 2
        // List b: C at rank 1, D at rank 2
        // RRF(A) = 1/(60+1) = ~0.01639
        // RRF(B) = 1/(60+2) = ~0.01613
        // RRF(C) = 1/(60+1) = ~0.01639
        // RRF(D) = 1/(60+2) = ~0.01613
        // A and C are equal score → tie-break by chunkId: A < C
        List<RetrievalHit> a = List.of(hit("A", 0.9), hit("B", 0.8));
        List<RetrievalHit> b = List.of(hit("C", 0.7), hit("D", 0.6));

        List<RetrievalHit> result = service.fuse(a, b, 60);

        assertThat(result).hasSize(4);
        // A and C have equal RRF scores; A < C alphabetically → A first
        assertThat(result.get(0).chunkId()).isEqualTo("A");
        assertThat(result.get(1).chunkId()).isEqualTo("C");
        assertThat(result.get(2).chunkId()).isEqualTo("B");
        assertThat(result.get(3).chunkId()).isEqualTo("D");
    }

    @Test
    void fuseOverlappingHits_higherScoreForSharedChunk() {
        // X is rank-1 in both lists → RRF = 1/61 + 1/61 = 2/61 ≈ 0.03279
        // Y is rank-2 in list a only → RRF = 1/62 ≈ 0.01613
        // Z is rank-2 in list b only → RRF = 1/62 ≈ 0.01613
        List<RetrievalHit> a = List.of(hit("X", 0.9), hit("Y", 0.8));
        List<RetrievalHit> b = List.of(hit("X", 0.7), hit("Z", 0.5));

        List<RetrievalHit> result = service.fuse(a, b, 60);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).chunkId()).isEqualTo("X");
        // Y and Z have equal RRF scores; tie-break alphabetically
        assertThat(result.get(1).chunkId()).isEqualTo("Y");
        assertThat(result.get(2).chunkId()).isEqualTo("Z");
    }

    @Test
    void fuseOverlappingHits_prefersListAObject() {
        // When X appears in both lists, the RetrievalHit from list a should be in the result
        RetrievalHit xFromA = new RetrievalHit("X", "doc-a", "Para-A", "text from a", 0.9, Map.of("source", "a"));
        RetrievalHit xFromB = new RetrievalHit("X", "doc-b", "Para-B", "text from b", 0.5, Map.of("source", "b"));

        List<RetrievalHit> result = service.fuse(List.of(xFromA), List.of(xFromB), 60);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).documentId()).isEqualTo("doc-a");
        assertThat(result.get(0).text()).isEqualTo("text from a");
    }

    @Test
    void emptyListA_returnsListBOrder() {
        List<RetrievalHit> b = List.of(hit("P", 0.8), hit("Q", 0.6));
        List<RetrievalHit> result = service.fuse(List.of(), b, 60);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).chunkId()).isEqualTo("P");
        assertThat(result.get(1).chunkId()).isEqualTo("Q");
    }

    @Test
    void emptyListB_returnsListAOrder() {
        List<RetrievalHit> a = List.of(hit("M", 0.9), hit("N", 0.7));
        List<RetrievalHit> result = service.fuse(a, List.of(), 60);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).chunkId()).isEqualTo("M");
        assertThat(result.get(1).chunkId()).isEqualTo("N");
    }

    @Test
    void tieBreak_isDeterministicByChunkId() {
        // Three chunks that all appear only in list a at the same relative positions
        // but we force a tie by making them all appear at rank 1 each in their own sub-call
        // Instead: two hits with identical RRF contributions — both rank-1 in separate lists
        List<RetrievalHit> a = List.of(hit("beta", 0.5));
        List<RetrievalHit> b = List.of(hit("alpha", 0.5));

        List<RetrievalHit> result = service.fuse(a, b, 60);

        assertThat(result.get(0).chunkId()).isEqualTo("alpha");
        assertThat(result.get(1).chunkId()).isEqualTo("beta");
    }

    @Test
    void bothListsEmpty_returnsEmptyList() {
        assertThat(service.fuse(List.of(), List.of(), 60)).isEmpty();
    }

    @Test
    void knownScoreComputation() {
        // Verify exact RRF score: rank 1 in list a only, k=60 → 1/61
        List<RetrievalHit> a = List.of(hit("only", 0.9));

        List<RetrievalHit> result = service.fuse(a, List.of(), 60);
        // Just verify ordering; exact score is internal to fuse
        assertThat(result).hasSize(1);
        assertThat(result.get(0).chunkId()).isEqualTo("only");
    }
}
