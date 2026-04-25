package com.policyguard.config.stub;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.policyguard.config.PolicyguardProperties;

/**
 * Deterministic stub embedding model for the {@code stub} profile.
 * <p>
 * For each input text it computes a SHA-256 digest, then expands the digest
 * bytes (mod 256 / 255.0) into a {@code dim}-length float array and
 * L2-normalises the result.  The same input always produces the same vector,
 * making tests reproducible without any external service.
 */
@Component
@Profile("stub")
@Primary
public class StubEmbeddingModel implements EmbeddingModel {

    private final int dim;

    public StubEmbeddingModel(PolicyguardProperties properties) {
        this.dim = properties.getEmbedding().getDim();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = request.getInstructions().stream()
                .map(text -> new Embedding(generateEmbedding(text), 0))
                .toList();
        return new EmbeddingResponse(embeddings, new EmbeddingResponseMetadata());
    }

    @Override
    public float[] embed(Document document) {
        return generateEmbedding(document.getText());
    }

    @Override
    public float[] embed(String text) {
        return generateEmbedding(text);
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private float[] generateEmbedding(String text) {
        byte[] digest = sha256(text);
        float[] raw = new float[dim];
        for (int i = 0; i < dim; i++) {
            raw[i] = (digest[i % digest.length] & 0xFF) / 255.0f;
        }
        return l2Normalize(raw);
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static float[] l2Normalize(float[] v) {
        double norm = 0.0;
        for (float x : v) norm += (double) x * x;
        if (norm == 0.0) return v;
        float scale = (float) (1.0 / Math.sqrt(norm));
        float[] result = new float[v.length];
        for (int i = 0; i < v.length; i++) result[i] = v[i] * scale;
        return result;
    }
}
