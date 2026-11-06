package com.ticket.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;


@ConfigurationProperties(prefix = "app.ai.embedding")
public record EmbeddingProperties(
        String provider,
        Boolean enabled,
        String model,
        Integer dimension,
        Integer refreshMinutes,
        Integer batchSize,
        Double weight
) {

    private static final int DEFAULT_DIMENSION = 1024;

    private static final Map<String, Integer> MODEL_DIMENSIONS = Map.ofEntries(
            Map.entry("@cf/baai/bge-m3", 1024),
            Map.entry("@cf/baai/bge-large-en-v1.5", 1024),
            Map.entry("@cf/baai/bge-small-en-v1.5", 384),
            Map.entry("@cf/baai/bge-base-en-v1.5", 768),
            Map.entry("text-embedding-3-small", 1536),
            Map.entry("text-embedding-3-large", 3072),
            Map.entry("text-embedding-ada-002", 1536),
            Map.entry("text-embedding-004", 768),
            Map.entry("gemini-embedding-001", 3072),
            Map.entry("nomic-embed-text", 768),
            Map.entry("multilingual-e5-large", 1024),
            Map.entry("bge-m3", 1024));

    private static final Map<String, String> PROVIDER_MODELS = Map.of(
            "cloudflare", "@cf/baai/bge-m3",
            "openai", "text-embedding-3-small",
            "gemini", "text-embedding-004",
            "openrouter", "text-embedding-3-small");

    public EmbeddingProperties {
        if (enabled == null) {
            enabled = true;
        }
        if (refreshMinutes == null || refreshMinutes < 1) {
            refreshMinutes = 10;
        }
        if (batchSize == null || batchSize < 1) {
            batchSize = 20;
        }
        if (weight == null) {
            weight = 0.7;
        }
        weight = Math.max(0.0, Math.min(1.0, weight));

        if (model == null || model.isBlank()) {
            model = provider == null ? null : PROVIDER_MODELS.get(provider.trim().toLowerCase());
        }
        if (dimension == null || dimension < 1) {
            dimension = model == null ? DEFAULT_DIMENSION : MODEL_DIMENSIONS.getOrDefault(model.trim(), DEFAULT_DIMENSION);
        }
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public boolean hasModel() {
        return model != null && !model.isBlank();
    }

    public double vectorWeight() {
        return weight;
    }

    public double textWeight() {
        return 1.0 - weight;
    }
}
