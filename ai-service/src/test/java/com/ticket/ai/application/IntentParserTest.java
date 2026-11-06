package com.ticket.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.ai.config.AiProperties;
import com.ticket.ai.infrastructure.LlmClient;
import com.ticket.ai.infrastructure.LlmResult;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntentParserTest {

    private final LlmClient llmClient = mock(LlmClient.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final AiUsageService usageService = mock(AiUsageService.class);
    private final DateScopeParser dateScopeParser = mock(DateScopeParser.class);
    private final AiProperties properties = new AiProperties(
            "cloudflare", null, "key", "model", null, "acc", null,
            null, null, 6, null, null, null, null);
    private final IntentParser parser = new IntentParser(
            llmClient, new ObjectMapper(), redis, usageService, properties, dateScopeParser);

    @SuppressWarnings("unchecked")
    private void stubCacheMiss() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
    }

    @Test
    void parsesCountryCityAndGenres() {
        stubCacheMiss();
        when(dateScopeParser.parse(anyString())).thenReturn(Optional.empty());
        when(llmClient.complete(anyString(), anyString(), anyString(), anyInt())).thenReturn(new LlmResult("""
                {"countryCode":"FR","city":"Paris","dateFrom":null,"dateTo":null,
                 "genres":["jazz"],"keywords":""}
                """, 0));

        QueryIntent intent = parser.parse("джаз во Франции");

        assertThat(intent.countryCode()).isEqualTo("FR");
        assertThat(intent.city()).isEqualTo("Paris");
        assertThat(intent.genres()).containsExactly("jazz");
    }

    @Test
    void deterministicDateOverridesModelDate() {
        stubCacheMiss();
        when(dateScopeParser.parse("концерт в эту пятницу")).thenReturn(Optional.of(
                new DateScopeParser.DateRange(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 6))));
        when(llmClient.complete(anyString(), anyString(), anyString(), anyInt())).thenReturn(new LlmResult("""
                {"countryCode":null,"city":null,"dateFrom":"2026-10-30","dateTo":"2026-10-30",
                 "genres":[],"keywords":""}
                """, 0));

        QueryIntent intent = parser.parse("концерт в эту пятницу");

        assertThat(intent.dateFrom()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(intent.dateTo()).isEqualTo(LocalDate.of(2026, 11, 6));
    }

    @Test
    void fallsBackToKeywordsWhenLlmFails() {
        stubCacheMiss();
        when(dateScopeParser.parse(anyString())).thenReturn(Optional.empty());
        when(llmClient.complete(anyString(), anyString(), anyString(), anyInt()))
                .thenThrow(new com.ticket.ai.infrastructure.AiUnavailableException("down"));

        QueryIntent intent = parser.parse("рок в Берлине");

        assertThat(intent.keywords()).isEqualTo("рок в Берлине");
        assertThat(intent.countryCode()).isNull();
        assertThat(intent.genres()).isEmpty();
    }
}
