package com.ticket.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;


@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String provider,
        String baseUrl,
        String apiKey,
        String chatModel,
        String intentModel,
        String projectId,
        String authScheme,
        Double temperature,
        Integer maxTokens,
        Integer topK,
        Boolean jsonMode,
        Integer dailyGlobalLimit,
        Integer dailyUserLimit,
        Integer cacheTtlMinutes
) {

    private static final Map<String, String> BASE_URLS = Map.of(
            "yandex", "https://ai.api.cloud.yandex.net/v1",
            "groq", "https://api.groq.com/openai/v1",
            "gemini", "https://generativelanguage.googleapis.com/v1beta/openai",
            "openrouter", "https://openrouter.ai/api/v1",
            "openai", "https://api.openai.com/v1");

    private static final Map<String, String> CHAT_MODELS = Map.of(
            "yandex", "aliceai-llm-flash",
            "cloudflare", "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
            "groq", "llama-3.3-70b-versatile",
            "gemini", "gemini-2.5-flash",
            "openrouter", "meta-llama/llama-3.3-70b-instruct:free",
            "openai", "gpt-4o-mini");

    private static final Map<String, String> INTENT_MODELS = Map.of(
            "cloudflare", "@cf/meta/llama-3.1-8b-instruct-fp8-fast",
            "groq", "llama-3.1-8b-instant",
            "openai", "gpt-4o-mini",
            "gemini", "gemini-2.5-flash-lite");

    public AiProperties {
        if (provider == null || provider.isBlank()) {
            provider = "yandex";
        }
        provider = provider.trim().toLowerCase();

        if (baseUrl == null || baseUrl.isBlank()) {
            if ("cloudflare".equals(provider) && projectId != null && !projectId.isBlank()) {
                baseUrl = "https://api.cloudflare.com/client/v4/accounts/" + projectId.trim() + "/ai/v1";
            } else {
                baseUrl = BASE_URLS.getOrDefault(provider, "");
            }
        }
        if (chatModel == null || chatModel.isBlank()) {
            chatModel = CHAT_MODELS.getOrDefault(provider, "aliceai-llm-flash");
        }
        if (intentModel == null || intentModel.isBlank()) {
            intentModel = INTENT_MODELS.getOrDefault(provider, chatModel);
        }
        if (authScheme == null || authScheme.isBlank()) {
            authScheme = "yandex".equals(provider) ? "Api-Key" : "Bearer";
        }
        if (temperature == null) {
            temperature = 0.2;
        }
        if (maxTokens == null) {
            maxTokens = 500;
        }
        if (topK == null || topK < 1) {
            topK = 6;
        }
        if (jsonMode == null) {
            jsonMode = true;
        }
        if (dailyGlobalLimit == null || dailyGlobalLimit < 1) {
            dailyGlobalLimit = 50;
        }
        if (dailyUserLimit == null || dailyUserLimit < 1) {
            dailyUserLimit = 10;
        }
        if (cacheTtlMinutes == null || cacheTtlMinutes < 0) {
            cacheTtlMinutes = 10;
        }
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean hasProjectId() {
        return projectId != null && !projectId.isBlank();
    }

    public boolean isConfigured() {
        return !baseUrl.isBlank() && hasApiKey();
    }

    public String resolvedModel() {
        return qualify(chatModel);
    }

    public String resolvedIntentModel() {
        return qualify(intentModel);
    }

    private String qualify(String model) {
        if (!"yandex".equals(provider) || model.startsWith("gpt://") || !hasProjectId()) {
            return model;
        }
        return "gpt://" + projectId.trim() + "/" + model.trim();
    }
}
