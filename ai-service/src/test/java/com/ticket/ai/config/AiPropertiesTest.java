package com.ticket.ai.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiPropertiesTest {

    @Test
    void buildsYandexModelUriFromFolderAndShortName() {
        AiProperties properties = props("yandex", null, "key", "aliceai-llm-flash", null, "folder-1");

        assertThat(properties.resolvedModel()).isEqualTo("gpt://folder-1/aliceai-llm-flash");
        assertThat(properties.baseUrl()).isEqualTo("https://ai.api.cloud.yandex.net/v1");
        assertThat(properties.authScheme()).isEqualTo("Api-Key");
    }

    @Test
    void keepsFullModelUriAsIs() {
        AiProperties properties = props("yandex", null, "key", "gpt://folder-1/yandexgpt-5.1", null, "folder-1");

        assertThat(properties.resolvedModel()).isEqualTo("gpt://folder-1/yandexgpt-5.1");
    }

    @Test
    void usesShortModelNameWhenFolderIsMissing() {
        AiProperties properties = props("yandex", null, "key", "aliceai-llm-flash", null, null);

        assertThat(properties.resolvedModel()).isEqualTo("aliceai-llm-flash");
    }

    @Test
    void appliesGroqPreset() {
        AiProperties properties = props("groq", null, "key", null, null, null);

        assertThat(properties.baseUrl()).isEqualTo("https://api.groq.com/openai/v1");
        assertThat(properties.chatModel()).isEqualTo("llama-3.3-70b-versatile");
        assertThat(properties.resolvedIntentModel()).isEqualTo("llama-3.1-8b-instant");
        assertThat(properties.authScheme()).isEqualTo("Bearer");
    }

    @Test
    void buildsCloudflareBaseUrlFromAccountId() {
        AiProperties properties = props("cloudflare", null, "token", null, null, "account-123");

        assertThat(properties.baseUrl())
                .isEqualTo("https://api.cloudflare.com/client/v4/accounts/account-123/ai/v1");
        assertThat(properties.chatModel()).isEqualTo("@cf/meta/llama-3.3-70b-instruct-fp8-fast");
        assertThat(properties.authScheme()).isEqualTo("Bearer");
        assertThat(properties.dailyGlobalLimit()).isEqualTo(50);
        assertThat(properties.dailyUserLimit()).isEqualTo(10);
    }

    @Test
    void yandexIntentModelFallsBackToChatModel() {
        AiProperties properties = props("yandex", null, "key", null, null, "folder-1");

        assertThat(properties.chatModel()).isEqualTo("aliceai-llm-flash");
        assertThat(properties.resolvedIntentModel()).isEqualTo("gpt://folder-1/aliceai-llm-flash");
    }

    @Test
    void explicitValuesOverridePreset() {
        AiProperties properties = new AiProperties(
                "groq", "https://example.com/v1", "key", "custom-model", "custom-intent",
                null, "Bearer", null, null, null, null, null, null, null);

        assertThat(properties.baseUrl()).isEqualTo("https://example.com/v1");
        assertThat(properties.chatModel()).isEqualTo("custom-model");
        assertThat(properties.resolvedIntentModel()).isEqualTo("custom-intent");
    }

    private AiProperties props(String provider, String baseUrl, String apiKey,
                               String chatModel, String intentModel, String projectId) {
        return new AiProperties(provider, baseUrl, apiKey, chatModel, intentModel, projectId,
                null, null, null, null, null, null, null, null);
    }
}
