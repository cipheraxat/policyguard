package com.policyguard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Asserts at startup that the configured embedding model returns vectors whose
 * length matches {@code policyguard.embedding.dim}.  Fails fast with a clear
 * message if the LMStudio (or other) model output dimension is mismatched —
 * catching issues before the first ingestion or query.
 *
 * <p>Disabled in the {@code stub} profile because {@link com.policyguard.config.stub.StubEmbeddingModel}
 * always honours {@code policyguard.embedding.dim} by construction.
 */
@Component
@Profile("!stub")
public class EmbeddingDimAssertionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingDimAssertionRunner.class);

    private final EmbeddingModel embeddingModel;
    private final PolicyguardProperties properties;

    public EmbeddingDimAssertionRunner(EmbeddingModel embeddingModel,
                                       PolicyguardProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        int expectedDim = properties.getEmbedding().getDim();
        float[] vector = embeddingModel.embed("hello");
        int actualDim = vector.length;

        if (actualDim != expectedDim) {
            throw new IllegalStateException(
                    "Embedding dimension mismatch: policyguard.embedding.dim=%d but model '%s' returned %d-dim vectors. "
                    .formatted(expectedDim, properties.getEmbedding().getModel(), actualDim)
                    + "Update policyguard.embedding.dim in application.yml (and re-run Flyway migration if changing the "
                    + "database schema) or switch to a model that outputs %d-dim embeddings.".formatted(expectedDim));
        }

        log.info("Embedding dimension assertion passed: model='{}' dim={}",
                properties.getEmbedding().getModel(), actualDim);
    }
}
