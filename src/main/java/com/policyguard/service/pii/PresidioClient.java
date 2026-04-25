package com.policyguard.service.pii;

import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.policyguard.config.PolicyguardProperties;

/**
 * Thin HTTP client for the Presidio Analyzer service.
 * POSTs to {@code /analyze} and returns the detected entities.
 */
@Component
public class PresidioClient {

    private final RestClient restClient;

    public PresidioClient(PolicyguardProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getPresidio().getBaseUrl())
                .build();
    }

    /**
     * Analyse {@code text} for PII entities in the given {@code language}.
     *
     * @param text     the plain text to analyse
     * @param language ISO-639-1 language code (typically {@code "en"})
     * @return list of detected entities (may be empty)
     */
    public List<PresidioEntity> analyze(String text, String language) {
        Map<String, String> body = Map.of("text", text, "language", language);

        List<PresidioEntity> result = restClient.post()
                .uri("/analyze")
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return result == null ? List.of() : result;
    }
}
