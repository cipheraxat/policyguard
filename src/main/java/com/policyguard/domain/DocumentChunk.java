package com.policyguard.domain;

import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.Type;

import com.policyguard.type.FloatArrayVectorType;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chunk_id", nullable = false, unique = true, length = 50)
    private String chunkId;

    @Column(name = "document_id", nullable = false, length = 50)
    private String documentId;

    @Column(name = "paragraph_ref", nullable = false)
    private String paragraphRef;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    /** pgvector embedding — dimension matches {@code policyguard.embedding.dim} (default 1536). */
    @Column(columnDefinition = "vector(1536)")
    @Type(FloatArrayVectorType.class)
    private float[] embedding;

    @Column(columnDefinition = "jsonb")
    @Type(JsonBinaryType.class)
    private Map<String, Object> metadata;

    // ── Getters / setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getParagraphRef() { return paragraphRef; }
    public void setParagraphRef(String paragraphRef) { this.paragraphRef = paragraphRef; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
