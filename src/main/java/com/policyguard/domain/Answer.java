package com.policyguard.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.Type;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "answers")
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "query_id", nullable = false, length = 50)
    private String queryId;

    @Column(name = "response_text", nullable = false, columnDefinition = "TEXT")
    private String responseText;

    /**
     * Each element: {@code {"chunkId": "...", "documentId": "...", "paragraphRef": "...", "textSnippet": "..."}}.
     */
    @Column(columnDefinition = "jsonb")
    @Type(JsonBinaryType.class)
    private List<Map<String, Object>> citations;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "retrieval_hits", columnDefinition = "jsonb")
    @Type(JsonBinaryType.class)
    private List<Map<String, Object>> retrievalHits;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt = OffsetDateTime.now();

    // ── Getters / setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getQueryId() { return queryId; }
    public void setQueryId(String queryId) { this.queryId = queryId; }

    public String getResponseText() { return responseText; }
    public void setResponseText(String responseText) { this.responseText = responseText; }

    public List<Map<String, Object>> getCitations() { return citations; }
    public void setCitations(List<Map<String, Object>> citations) { this.citations = citations; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public List<Map<String, Object>> getRetrievalHits() { return retrievalHits; }
    public void setRetrievalHits(List<Map<String, Object>> retrievalHits) { this.retrievalHits = retrievalHits; }

    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(OffsetDateTime generatedAt) { this.generatedAt = generatedAt; }
}
