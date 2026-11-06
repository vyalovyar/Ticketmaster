package com.ticket.ai.infrastructure;

import com.ticket.ai.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LlmClientTest {

    @Test
    void sendsYandexAuthHeadersAndModelUri() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://ai.api.cloud.yandex.net/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        AiProperties properties = new AiProperties(
                "yandex",
                "https://ai.api.cloud.yandex.net/v1",
                "secret-key",
                "aliceai-llm-flash",
                null,
                "folder-123",
                "Api-Key",
                0.2,
                800,
                8,
                true,
                null,
                null,
                null);

        LlmClient client = new LlmClient(builder.build(), properties);

        server.expect(requestTo("https://ai.api.cloud.yandex.net/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Api-Key secret-key"))
                .andExpect(header("OpenAI-Project", "folder-123"))
                .andExpect(jsonPath("$.model").value("gpt://folder-123/aliceai-llm-flash"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"answer\\\":\\\"ok\\\",\\\"eventIds\\\":[]}\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"neurons\":1.5}}",
                        MediaType.APPLICATION_JSON));

        LlmResult result = client.complete("system", "user");

        assertThat(result.content()).contains("\"answer\":\"ok\"");
        assertThat(result.neurons()).isEqualTo(2);
        server.verify();
    }
}
