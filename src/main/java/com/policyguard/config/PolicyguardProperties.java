package com.policyguard.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "policyguard")
public class PolicyguardProperties {

    private Chat chat = new Chat();
    private Embedding embedding = new Embedding();
    private Presidio presidio = new Presidio();
    private Chunking chunking = new Chunking();
    private Retrieval retrieval = new Retrieval();
    private Confidence confidence = new Confidence();
    private Risk risk = new Risk();

    // ── Getters / setters ────────────────────────────────────────────────────

    public Chat getChat() { return chat; }
    public void setChat(Chat chat) { this.chat = chat; }

    public Embedding getEmbedding() { return embedding; }
    public void setEmbedding(Embedding embedding) { this.embedding = embedding; }

    public Presidio getPresidio() { return presidio; }
    public void setPresidio(Presidio presidio) { this.presidio = presidio; }

    public Chunking getChunking() { return chunking; }
    public void setChunking(Chunking chunking) { this.chunking = chunking; }

    public Retrieval getRetrieval() { return retrieval; }
    public void setRetrieval(Retrieval retrieval) { this.retrieval = retrieval; }

    public Confidence getConfidence() { return confidence; }
    public void setConfidence(Confidence confidence) { this.confidence = confidence; }

    public Risk getRisk() { return risk; }
    public void setRisk(Risk risk) { this.risk = risk; }

    // ── Nested config classes ─────────────────────────────────────────────────

    public static class Chat {
        private String provider = "lmstudio";
        private String baseUrl = "http://host.docker.internal:1234/v1";
        private String apiKey = "not-needed";
        private String model = "local-model";

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    public static class Embedding {
        private String provider = "lmstudio";
        private String baseUrl = "http://host.docker.internal:1234/v1";
        private String apiKey = "not-needed";
        private String model = "text-embedding-nomic-embed-text-v1.5";
        private int dim = 1536;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getDim() { return dim; }
        public void setDim(int dim) { this.dim = dim; }
    }

    public static class Presidio {
        private String baseUrl = "http://localhost:5002";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class Chunking {
        private int maxChars = 1200;
        private int overlap = 150;

        public int getMaxChars() { return maxChars; }
        public void setMaxChars(int maxChars) { this.maxChars = maxChars; }
        public int getOverlap() { return overlap; }
        public void setOverlap(int overlap) { this.overlap = overlap; }
    }

    public static class Retrieval {
        private int topK = 5;
        private int rrfK = 60;

        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
        public int getRrfK() { return rrfK; }
        public void setRrfK(int rrfK) { this.rrfK = rrfK; }
    }

    public static class Confidence {
        private double threshold = 0.65;

        public double getThreshold() { return threshold; }
        public void setThreshold(double threshold) { this.threshold = threshold; }
    }

    public static class Risk {
        private List<RiskPatternConfig> patterns = new ArrayList<>();

        public List<RiskPatternConfig> getPatterns() { return patterns; }
        public void setPatterns(List<RiskPatternConfig> patterns) { this.patterns = patterns; }

        public static class RiskPatternConfig {
            private String category;
            private String regex;

            public String getCategory() { return category; }
            public void setCategory(String category) { this.category = category; }
            public String getRegex() { return regex; }
            public void setRegex(String regex) { this.regex = regex; }
        }
    }
}
