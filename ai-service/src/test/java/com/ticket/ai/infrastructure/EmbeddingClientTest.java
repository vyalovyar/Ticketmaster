package com.ticket.ai.infrastructure;

import com.ticket.ai.config.AiProperties;
import com.ticket.ai.config.EmbeddingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EmbeddingClientTest {

    private static final String BASE_URL = "https://api.cloudflare.com/client/v4/accounts/acc/ai/v1";

    private final AiProperties ai = new AiProperties(
            "cloudflare", "https://api.cloudflare.com/client/v4/accounts/acc/ai/v1",
            "token", "chat-model", null, "acc", "Bearer", 0.2, 500, 6, true, 50, 10, 10);

    @Test
    void sendsBatchAndParsesVectors() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        EmbeddingClient client = new EmbeddingClient(builder.build(), ai, embedding(2));

        server.expect(requestTo(BASE_URL + "/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token"))
                .andExpect(jsonPath("$.model").value("@cf/baai/bge-m3"))
                .andExpect(jsonPath("$.input[0]").value("rock"))
                .andExpect(jsonPath("$.input[1]").value("jazz"))
                .andRespond(withSuccess("""
                        {"object":"list","model":"@cf/baai/bge-m3","data":[
                          {"object":"embedding","index":1,"embedding":[0.3,0.4]},
                          {"object":"embedding","index":0,"embedding":[0.1,0.2]}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<float[]> vectors = client.embed(List.of("rock", "jazz"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(0.1f, 0.2f);
        assertThat(vectors.get(1)).containsExactly(0.3f, 0.4f);
        server.verify();
    }

    @Test
    void rejectsVectorWithUnexpectedDimension() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        EmbeddingClient client = new EmbeddingClient(builder.build(), ai, embedding(1024));

        server.expect(requestTo(BASE_URL + "/embeddings"))
                .andRespond(withSuccess("""
                        {"data":[{"index":0,"embedding":[0.1,0.2]}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed(List.of("rock")))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("размерности 2");
    }

    @Test
    void reportsUnavailableWhenModelIsNotConfigured() {
        EmbeddingProperties yandexEmbedding = new EmbeddingProperties(
                "yandex", true, null, null, 10, 20, 0.7);
        AiProperties yandex = new AiProperties(
                "yandex", null, "key", null, null, "folder-1", null,
                null, null, 6, null, null, null, null);
        EmbeddingClient client = new EmbeddingClient(RestClient.builder().baseUrl(BASE_URL),
                yandex, yandexEmbedding);

        assertThat(client.isAvailable()).isFalse();
        assertThat(client.unavailableReason()).contains("AI_EMBEDDING_MODEL");
        assertThatThrownBy(() -> client.embed(List.of("rock")))
                .isInstanceOf(AiUnavailableException.class);
    }

    private EmbeddingProperties embedding(int dimension) {
        return new EmbeddingProperties("cloudflare", true, "@cf/baai/bge-m3", dimension, 10, 20, 0.7);
    }
}
