package com.policyguard.service.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;

import org.springframework.stereotype.Service;

/**
 * Reciprocal Rank Fusion (RRF) merger for two ranked retrieval lists.
 *
 * <p>Given two ranked lists {@code a} (semantic) and {@code b} (keyword), the
 * RRF score for each unique chunk is:
 * <pre>
 *   score = Σ  1 / (k + rank_i)
 * </pre>
 * where {@code rank_i} is the 1-based position of the chunk in the list where
 * it appears.  The result is sorted by RRF score descending; ties are broken
 * by {@code chunkId} alphabetically for determinism.
 *
 * <p>When a chunk appears in both lists, the {@link RetrievalHit} object from
 * list {@code a} is preserved (so the caller can rely on list-a metadata, e.g.
 * semantic similarity scores).
 */
@Service
public class RrfFusionService {

    /**
     * Fuses two ranked hit lists using Reciprocal Rank Fusion.
     *
     * @param a first ranked list (e.g. semantic hits); preferred source for hit objects
     * @param b second ranked list (e.g. keyword hits)
     * @param k RRF smoothing constant (typically 60)
     * @return merged list sorted by descending RRF score, ties broken by chunkId
     */
    public List<RetrievalHit> fuse(List<RetrievalHit> a, List<RetrievalHit> b, int k) {
        // Build rank maps (1-based)
        Map<String, Integer> rankA = buildRankMap(a);
        Map<String, Integer> rankB = buildRankMap(b);

        // Collect all unique chunkIds, preserving insertion order for determinism
        SequencedSet<String> allIds = new LinkedHashSet<>();
        a.forEach(h -> allIds.add(h.chunkId()));
        b.forEach(h -> allIds.add(h.chunkId()));

        // Prefer hit object from list a when both lists contain the same chunk
        Map<String, RetrievalHit> hitSource = new HashMap<>();
        // Process b first so a overwrites (a takes priority)
        b.forEach(h -> hitSource.put(h.chunkId(), h));
        a.forEach(h -> hitSource.put(h.chunkId(), h));

        // Compute RRF scores
        Map<String, Double> rrfScore = new HashMap<>();
        for (String id : allIds) {
            double score = 0.0;
            if (rankA.containsKey(id)) score += 1.0 / (k + rankA.get(id));
            if (rankB.containsKey(id)) score += 1.0 / (k + rankB.get(id));
            rrfScore.put(id, score);
        }

        // Sort: descending score, then ascending chunkId for determinism
        List<String> sorted = new ArrayList<>(allIds);
        sorted.sort(
                Comparator.comparingDouble((String id) -> -rrfScore.get(id))
                          .thenComparing(Comparator.naturalOrder()));

        return sorted.stream().map(hitSource::get).toList();
    }

    /**
     * Returns the RRF score for a single chunk given its 1-based rank in one list.
     * Useful for computing the fallback score for keyword-only hits.
     */
    public double rrfScore(int rank, int k) {
        return 1.0 / (k + rank);
    }

    private static Map<String, Integer> buildRankMap(List<RetrievalHit> list) {
        Map<String, Integer> map = new HashMap<>(list.size() * 2);
        for (int i = 0; i < list.size(); i++) {
            map.put(list.get(i).chunkId(), i + 1);
        }
        return map;
    }
}
