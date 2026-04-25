package com.policyguard.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.policyguard.domain.DocumentChunk;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    Optional<DocumentChunk> findByChunkId(String chunkId);

    List<DocumentChunk> findByDocumentId(String documentId);

    /**
     * Semantic similarity search using pgvector cosine distance operator.
     * Returns up to {@code limit} chunks ordered by ascending cosine distance
     * (i.e. descending cosine similarity) to the query embedding.
     *
     * <p>Note: the {@code :queryEmbedding} parameter is bound as a native SQL
     * {@code ::vector} cast so pgvector can parse the float-array representation.
     * Actual implementation may require a native query; this stub is wired up
     * fully in Phase 7 (retrieval).
     */
    @Query(value = """
            SELECT * FROM document_chunks
            ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<DocumentChunk> findSimilar(@Param("queryEmbedding") String queryEmbedding,
                                    @Param("limit") int limit);

    /**
     * Full-text search using Postgres {@code ts_rank} on the GIN tsvector index.
     */
    @Query(value = """
            SELECT *, ts_rank(to_tsvector('english', text), plainto_tsquery('english', :query)) AS rank
            FROM document_chunks
            WHERE to_tsvector('english', text) @@ plainto_tsquery('english', :query)
            ORDER BY rank DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<DocumentChunk> findByFullText(@Param("query") String query, @Param("limit") int limit);

    /**
     * Semantic similarity search returning raw columns for hybrid-retrieval use.
     * Returns {@code Object[]} rows with columns:
     * {@code [chunk_id, document_id, paragraph_ref, text, sim, metadata_text]}.
     *
     * <p>The embedding is passed as a pgvector-format string literal
     * (e.g. {@code "[0.1,0.2,...]"}) and cast via {@code CAST(:q AS vector)}.
     * {@code sim} = 1 − cosine_distance (higher is more similar).
     * The {@code metadata} jsonb column is returned as {@code TEXT} so the
     * service can parse it with {@code ObjectMapper}.
     */
    @Query(value = """
            SELECT chunk_id, document_id, paragraph_ref, text,
                   1 - (embedding <=> CAST(:q AS vector)) AS sim,
                   metadata::text
            FROM document_chunks
            ORDER BY embedding <=> CAST(:q AS vector) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> semanticSearch(@Param("q") String embeddingLiteral,
                                  @Param("limit") int limit);

    /**
     * Full-text keyword search returning raw columns for hybrid-retrieval use.
     * Returns {@code Object[]} rows with columns:
     * {@code [chunk_id, document_id, paragraph_ref, text, rank, metadata_text]}.
     *
     * <p>{@code rank} is the {@code ts_rank_cd} score from Postgres FTS.
     * The {@code metadata} jsonb column is returned as {@code TEXT}.
     */
    @Query(value = """
            SELECT chunk_id, document_id, paragraph_ref, text,
                   ts_rank_cd(to_tsvector('english', text), plainto_tsquery('english', :q)) AS rank,
                   metadata::text
            FROM document_chunks
            WHERE to_tsvector('english', text) @@ plainto_tsquery('english', :q)
            ORDER BY rank DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> keywordSearch(@Param("q") String text,
                                 @Param("limit") int limit);
}
