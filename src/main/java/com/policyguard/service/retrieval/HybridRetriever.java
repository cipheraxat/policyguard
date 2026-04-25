package com.policyguard.service.retrieval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyguard.config.PolicyguardProperties;
import com.policyguard.repository.DocumentChunkRepository;

/**
 * Performs hybrid retrieval by combining pgvector cosine similarity search with
 * Postgres full-text search (FTS), then fusing the results with Reciprocal Rank
 * Fusion (RRF).
 *
 * <h3>Score semantics</h3>
 * <p>The {@link RetrievalHit#score()} on each returned hit is set to the
 * <em>semantic cosine similarity</em> (1 − cosine_distance ∈ [0, 1]) whenever
 * the chunk appeared in the vector-search leg — this is the value used for
 * downstream confidence calculation.  For chunks that surfaced <em>only</em>
 * via FTS (keyword-only hits), the score is set to the RRF contribution of
 * that chunk from the keyword list: {@code 1 / (rrfK + rank_in_keyword_list)}.
 * This ensures the field is always a meaningful, bounded quality signal.
 */
@Service
public class HybridRetriever {

    private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final EmbeddingModel embeddingModel;
    private final DocumentChunkRepository chunkRepository;
    private final RrfFusionService rrfFusionService;
    private final ObjectMapper objectMapper;
    private final PolicyguardProperties properties;

    public HybridRetriever(EmbeddingModel embeddingModel,
                           DocumentChunkRepository chunkRepository,
                           RrfFusionService rrfFusionService,
                           ObjectMapper objectMapper,
                           PolicyguardProperties properties) {
        this.embeddingModel = embeddingModel;
        this.chunkRepository = chunkRepository;
        this.rrfFusionService = rrfFusionService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Retrieves the top-K most relevant chunks for the given query text.
     *
     * @param queryText the (PII-redacted) user question
     * @param filters   optional key-value filter map; {@code "documentId"} is matched
     *                  against {@link RetrievalHit#documentId()}, all other keys are
     *                  matched against metadata entries (string-equal, case-sensitive)
     * @param topK      maximum number of hits to return
     * @return fused, filtered, top-K list of hits sorted by descending relevance
     */
    public List<RetrievalHit> retrieve(String queryText, Map<String, Object> filters, int topK) {
        // 1. Embed query
        float[] embedding = embeddingModel.embed(queryText);

        // 2. Format as pgvector literal "[x1,x2,...]"
        String embeddingLiteral = buildEmbeddingLiteral(embedding);

        int candidateK = topK * 2;

        // 3. Run both searches
        List<Object[]> semanticRows = chunkRepository.semanticSearch(embeddingLiteral, candidateK);
        List<Object[]> keywordRows  = chunkRepository.keywordSearch(queryText, candidateK);

        // 4. Build RetrievalHit lists (parse metadata from jsonb text column)
        List<RetrievalHit> semanticHits = toHits(semanticRows);
        List<RetrievalHit> keywordHits  = toHits(keywordRows);

        // 5. RRF fusion (semantic = list a, keyword = list b)
        int rrfK = properties.getRetrieval().getRrfK();
        List<RetrievalHit> fused = rrfFusionService.fuse(semanticHits, keywordHits, rrfK);

        // 6. Fix scores: semantic hits keep their cosine similarity; keyword-only hits
        //    get the RRF score (1/(rrfK+rank)) as a proxy quality signal.
        Set<String> semanticChunkIds = semanticHits.stream()
                .map(RetrievalHit::chunkId)
                .collect(Collectors.toSet());

        // Build rank map for keyword-only fallback score
        Map<String, Integer> keywordRankMap = new HashMap<>();
        for (int i = 0; i < keywordHits.size(); i++) {
            keywordRankMap.put(keywordHits.get(i).chunkId(), i + 1);
        }

        List<RetrievalHit> scored = new ArrayList<>(fused.size());
        for (RetrievalHit hit : fused) {
            if (!semanticChunkIds.contains(hit.chunkId())) {
                // keyword-only hit: replace ts_rank score with RRF score
                int rank = keywordRankMap.getOrDefault(hit.chunkId(), fused.size());
                double rrfScore = rrfFusionService.rrfScore(rank, rrfK);
                hit = new RetrievalHit(hit.chunkId(), hit.documentId(), hit.paragraphRef(),
                        hit.text(), rrfScore, hit.metadata());
            }
            scored.add(hit);
        }

        // 7. Apply metadata filters
        List<RetrievalHit> filtered = applyFilters(scored, filters);

        // 8. Return top-K
        return filtered.stream().limit(topK).toList();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Formats a float array as a pgvector-compatible string literal, e.g. {@code "[0.1,0.2,...]"}.
     */
    private static String buildEmbeddingLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Converts raw {@code Object[]} rows from a native query into {@link RetrievalHit} objects.
     * Expected column order: chunk_id, document_id, paragraph_ref, text, score, metadata(::text).
     */
    private List<RetrievalHit> toHits(List<Object[]> rows) {
        List<RetrievalHit> hits = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            String chunkId      = (String) row[0];
            String documentId   = (String) row[1];
            String paragraphRef = (String) row[2];
            String text         = (String) row[3];
            double score        = row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;
            Map<String, Object> metadata = parseMetadata((String) row[5]);
            hits.add(new RetrievalHit(chunkId, documentId, paragraphRef, text, score, metadata));
        }
        return hits;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            log.warn("Failed to parse chunk metadata JSON: {}", e.getMessage());
            return Map.of();
        }
    }

    private List<RetrievalHit> applyFilters(List<RetrievalHit> hits, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) return hits;
        return hits.stream().filter(hit -> matchesFilters(hit, filters)).toList();
    }

    private boolean matchesFilters(RetrievalHit hit, Map<String, Object> filters) {
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String key   = entry.getKey();
            Object value = entry.getValue();
            if ("documentId".equals(key)) {
                if (!String.valueOf(value).equals(hit.documentId())) return false;
            } else {
                Object metaValue = hit.metadata().get(key);
                if (!String.valueOf(value).equals(String.valueOf(metaValue))) return false;
            }
        }
        return true;
    }
}
