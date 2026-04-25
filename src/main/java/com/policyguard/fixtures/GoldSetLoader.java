package com.policyguard.fixtures;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Loads the evaluation gold-set from {@code classpath:fixtures/gold-set.json}.
 *
 * <p>Intentionally has no Spring dependency so it can be used in plain JUnit
 * tests and in the eval harness {@code ApplicationRunner} without a full
 * application context.
 */
public class GoldSetLoader {

    private static final String RESOURCE_PATH = "/fixtures/gold-set.json";

    private final ObjectMapper objectMapper;

    public GoldSetLoader() {
        this.objectMapper = new ObjectMapper();
    }

    public GoldSetLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Loads and deserialises all gold queries.
     *
     * @return immutable list of {@link GoldQuery} records
     * @throws IllegalStateException if the resource cannot be found
     * @throws RuntimeException      on JSON parse failure
     */
    public List<GoldQuery> load() {
        try (InputStream is = GoldSetLoader.class.getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                throw new IllegalStateException(
                        "Gold-set resource not found on classpath: " + RESOURCE_PATH);
            }
            return objectMapper.readValue(is, new TypeReference<List<GoldQuery>>() {});
        } catch (IOException e) {
            throw new RuntimeException("Failed to load gold set from " + RESOURCE_PATH, e);
        }
    }
}
