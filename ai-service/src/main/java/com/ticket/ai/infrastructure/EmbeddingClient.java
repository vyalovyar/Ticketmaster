package com.ticket.ai.infrastructure;

import com.ticket.ai.config.AiProperties;
import com.ticket.ai.config.EmbeddingProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
public class EmbeddingClient {

    private final RestClient restClient;
    private final AiProperties aiProperties;
    private final EmbeddingProperties embeddingProperties;

    @org.springframework.beans.factory.annotation.Autowired
    public EmbeddingClient(RestClient.Builder builder, AiProperties aiProperties,
                           EmbeddingProperties embeddingProperties) {
        this(createRestClient(builder, aiProperties), aiProperties, embeddingProperties);
    }

    EmbeddingClient(RestClient restClient, AiProperties aiProperties,
                    EmbeddingProperties embeddingProperties) {
        this.restClient = restClient;
        this.aiProperties = aiProperties;
        this.embeddingProperties = embeddingProperties;
    }

    private static RestClient createRestClient(RestClient.Builder builder, AiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(60_000);

        RestClient.Builder configured = builder.requestFactory(requestFactory);
        if (properties.baseUrl() != null && !properties.baseUrl().isBlank()) {
            configured.baseUrl(properties.baseUrl());
        }
        return configured.build();
    }

    public boolean isEnabled() {
        return embeddingProperties.isEnabled();
    }

    public boolean isAvailable() {
        return embeddingProperties.isEnabled()
                && embeddingProperties.hasModel()
                && aiProperties.isConfigured();
    }

    public String unavailableReason() {
        if (!embeddingProperties.isEnabled()) {
            return "смысловой поиск отключён (AI_EMBEDDING_ENABLED=false)";
        }
        if (!embeddingProperties.hasModel()) {
            return "не задана embedding-модель для провайдера «" + aiProperties.provider()
                    + "» (AI_EMBEDDING_MODEL)";
        }
        return "не настроен AI-ключ (AI_API_KEY)";
    }

    public int dimension() {
        return embeddingProperties.dimension();
    }

    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (!isAvailable()) {
            throw new AiUnavailableException("Embedding недоступен: " + unavailableReason());
        }

        try {
            EmbeddingResponse response = restClient.post()
                    .uri("/embeddings")
                    .headers(headers -> {
                        headers.set(HttpHeaders.AUTHORIZATION,
                                aiProperties.authScheme() + " " + aiProperties.apiKey());
                        if ("yandex".equals(aiProperties.provider()) && aiProperties.hasProjectId()) {
                            headers.set("OpenAI-Project", aiProperties.projectId());
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EmbeddingRequest(embeddingProperties.model(), texts))
                    .retrieve()
                    .body(EmbeddingResponse.class);

            if (response == null || response.data() == null || response.data().isEmpty()) {
                throw new AiUnavailableException("Embedding-модель вернула пустой ответ");
            }
            if (response.data().size() != texts.size()) {
                throw new AiUnavailableException("Embedding-модель вернула " + response.data().size()
                        + " векторов на " + texts.size() + " текстов");
            }
            return orderByIndex(response.data(), texts.size());
        } catch (RestClientResponseException e) {
            throw new AiUnavailableException(
                    "Embedding-модель " + embeddingProperties.model() + " ответила ошибкой "
                            + e.getStatusCode().value()
                            + ". Проверь AI_API_KEY, AI_PROJECT_ID и AI_EMBEDDING_MODEL.", e);
        } catch (ResourceAccessException e) {
            throw new AiUnavailableException(
                    "Не удалось подключиться к embedding-модели по адресу " + aiProperties.baseUrl(), e);
        }
    }

    private List<float[]> orderByIndex(List<EmbeddingData> data, int expected) {
        float[][] vectors = new float[expected][];
        boolean indexUsable = true;

        for (EmbeddingData item : data) {
            Integer index = item.index();
            if (index == null || index < 0 || index >= expected || vectors[index] != null) {
                indexUsable = false;
                break;
            }
            vectors[index] = toFloatArray(item.embedding());
        }

        if (!indexUsable) {
            for (int i = 0; i < expected; i++) {
                vectors[i] = toFloatArray(data.get(i).embedding());
            }
        }

        for (int i = 0; i < expected; i++) {
            int actual = vectors[i] == null ? 0 : vectors[i].length;
            if (actual != embeddingProperties.dimension()) {
                throw new AiUnavailableException("Embedding-модель " + embeddingProperties.model()
                        + " вернула вектор размерности " + actual + ", ожидалась "
                        + embeddingProperties.dimension()
                        + ". Задай AI_EMBEDDING_DIMENSION под свою модель.");
            }
        }
        return List.of(vectors);
    }

    private float[] toFloatArray(List<Double> values) {
        if (values == null) {
            return new float[0];
        }
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Double value = values.get(i);
            result[i] = value == null ? 0f : value.floatValue();
        }
        return result;
    }

    public record EmbeddingRequest(String model, List<String> input) {
    }

    public record EmbeddingResponse(List<EmbeddingData> data) {
    }

    public record EmbeddingData(Integer index, List<Double> embedding) {
    }
}
