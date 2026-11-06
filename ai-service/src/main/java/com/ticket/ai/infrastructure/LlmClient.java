package com.ticket.ai.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticket.ai.config.AiProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;


@Component
public class LlmClient {

    private final RestClient restClient;
    private final AiProperties properties;

    @org.springframework.beans.factory.annotation.Autowired
    public LlmClient(RestClient.Builder builder, AiProperties properties) {
        this(createRestClient(builder, properties), properties);
    }

    LlmClient(RestClient restClient, AiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    private static RestClient createRestClient(RestClient.Builder builder, AiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(120_000);

        RestClient.Builder configured = builder.requestFactory(requestFactory);
        if (properties.baseUrl() != null && !properties.baseUrl().isBlank()) {
            configured.baseUrl(properties.baseUrl());
        }
        return configured.build();
    }

    public LlmResult complete(String systemPrompt, String userPrompt) {
        return complete(properties.resolvedModel(), systemPrompt, userPrompt, properties.maxTokens());
    }

    public LlmResult complete(String model, String systemPrompt, String userPrompt, int maxTokens) {
        if (!properties.isConfigured()) {
            throw new AiUnavailableException(
                    "AI не настроен. Укажи AI_API_KEY и (для Cloudflare/Yandex) AI_PROJECT_ID.");
        }

        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                List.of(
                        new ChatMessage("system", systemPrompt),
                        new ChatMessage("user", userPrompt)),
                properties.temperature(),
                maxTokens,
                properties.jsonMode() ? new ResponseFormat("json_object") : null);

        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .headers(headers -> {
                        headers.set(HttpHeaders.AUTHORIZATION,
                                properties.authScheme() + " " + properties.apiKey());
                        if ("yandex".equals(properties.provider()) && properties.hasProjectId()) {
                            headers.set("OpenAI-Project", properties.projectId());
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()
                    || response.choices().get(0).message() == null) {
                throw new AiUnavailableException("LLM вернула пустой ответ");
            }
            String content = response.choices().get(0).message().content();
            if (content == null || content.isBlank()) {
                throw new AiUnavailableException("LLM вернула пустой текст ответа");
            }

            long neurons = 0;
            if (response.usage() != null && response.usage().neurons() != null) {
                neurons = Math.round(response.usage().neurons());
            }
            return new LlmResult(content, neurons);
        } catch (RestClientResponseException e) {
            throw new AiUnavailableException(
                    "LLM (" + properties.provider() + ") ответила ошибкой " + e.getStatusCode().value()
                            + ". Проверь AI_API_KEY, AI_PROJECT_ID и AI_CHAT_MODEL.", e);
        } catch (ResourceAccessException e) {
            throw new AiUnavailableException(
                    "Не удалось подключиться к LLM (" + properties.provider() + ") по адресу "
                            + properties.baseUrl() + ". Проверь AI_BASE_URL и доступ к интернету.", e);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatCompletionRequest(
            String model,
            List<ChatMessage> messages,
            double temperature,
            @JsonProperty("max_tokens") int maxTokens,
            @JsonProperty("response_format") ResponseFormat responseFormat
    ) {
    }

    public record ResponseFormat(String type) {
    }

    public record ChatMessage(String role, String content) {
    }

    public record ChatCompletionResponse(List<Choice> choices, Usage usage) {
    }

    public record Choice(ChatMessage message) {
    }

    public record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            Double neurons
    ) {
    }
}
