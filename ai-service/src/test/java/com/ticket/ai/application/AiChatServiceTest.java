package com.ticket.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.ai.infrastructure.AiUnavailableException;
import com.ticket.ai.infrastructure.LlmClient;
import com.ticket.ai.infrastructure.LlmResult;
import com.ticket.ai.infrastructure.dto.CatalogEvent;
import com.ticket.ai.infrastructure.dto.Seat;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatServiceTest {

    private final EventRetriever retriever = mock(EventRetriever.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final AiUsageService usageService = mock(AiUsageService.class);
    private final AiAnswerCache answerCache = mock(AiAnswerCache.class);
    private final AiChatService service = new AiChatService(
            retriever, new PromptBuilder(), llmClient, new ObjectMapper(), usageService, answerCache);

    @Test
    void returnsCardsOnlyForIdsThatExistInRetrievedEvents() {
        CatalogEvent first = event("First");
        CatalogEvent second = event("Second");
        stubSearch("концерт", first, second);
        when(usageService.consumeLlmQuota("user-1"))
                .thenReturn(AiUsageService.QuotaDecision.allowed(1, 10, 1, 50));
        when(llmClient.complete(anyString(), anyString())).thenReturn(new LlmResult("""
                {"answer": "Есть два концерта", "eventIds": ["%s", "fake-id"]}
                """.formatted(first.id()), 0));

        ChatResponse response = service.chat("концерт", "user-1");

        assertThat(response.mode()).isEqualTo("llm");
        assertThat(response.answer()).isEqualTo("Есть два концерта");
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().get(0).id()).isEqualTo(first.id());
    }

    @Test
    void doesNotCallLlmWhenNothingFound() {
        when(answerCache.get("концерт в Антарктиде")).thenReturn(Optional.empty());
        when(retriever.retrieve("концерт в Антарктиде")).thenReturn(emptyResult("концерт в Антарктиде"));

        ChatResponse response = service.chat("концерт в Антарктиде", "user-1");

        assertThat(response.mode()).isEqualTo("no_results");
        assertThat(response.events()).isEmpty();
        assertThat(response.answer()).contains("нет событий");
        verify(llmClient, never()).complete(anyString(), anyString());
    }

    @Test
    void fallsBackToPlainTextWhenModelReturnsBrokenJson() {
        CatalogEvent first = event("First");
        stubSearch("концерт", first);
        when(usageService.consumeLlmQuota("user-1"))
                .thenReturn(AiUsageService.QuotaDecision.allowed(1, 10, 1, 50));
        when(llmClient.complete(anyString(), anyString()))
                .thenReturn(new LlmResult("Просто текст без JSON", 0));

        ChatResponse response = service.chat("концерт", "user-1");

        assertThat(response.mode()).isEqualTo("llm");
        assertThat(response.answer()).isEqualTo("Просто текст без JSON");
        assertThat(response.events()).hasSize(1);
    }

    @Test
    void doesNotShowNoResultsWhenRetrievalFoundEvents() {
        CatalogEvent first = event("First");
        stubSearch("симфония", first);
        when(usageService.consumeLlmQuota("user-1"))
                .thenReturn(AiUsageService.QuotaDecision.allowed(1, 10, 1, 50));
        when(llmClient.complete(anyString(), anyString())).thenReturn(new LlmResult(
                "{\"answer\":\"Подходящих событий не нашёл\",\"eventIds\":[]}", 0));

        ChatResponse response = service.chat("симфония", "user-1");

        assertThat(response.answer()).doesNotContain("не нашёл");
        assertThat(response.events()).hasSize(1);
    }

    @Test
    void returnsFallbackWithoutLlmWhenQuotaExceeded() {
        CatalogEvent first = event("First");
        stubSearch("концерт", first);
        when(usageService.consumeLlmQuota("user-1"))
                .thenReturn(AiUsageService.QuotaDecision.userLimit(11, 10, 20, 50));

        ChatResponse response = service.chat("концерт", "user-1");

        assertThat(response.mode()).isEqualTo("fallback");
        assertThat(response.events()).hasSize(1);
        assertThat(response.answer()).contains("лимит");
        verify(llmClient, never()).complete(anyString(), anyString());
    }

    @Test
    void returnsFallbackWhenLlmIsUnavailable() {
        CatalogEvent first = event("First");
        stubSearch("концерт", first);
        when(usageService.consumeLlmQuota("user-1"))
                .thenReturn(AiUsageService.QuotaDecision.allowed(1, 10, 1, 50));
        when(llmClient.complete(anyString(), anyString()))
                .thenThrow(new AiUnavailableException("down"));

        ChatResponse response = service.chat("концерт", "user-1");

        assertThat(response.mode()).isEqualTo("fallback");
        assertThat(response.events()).hasSize(1);
        assertThat(response.answer()).contains("недоступен");
    }

    @Test
    void returnsCachedAnswerWithoutLlm() {
        CatalogEvent first = event("First");
        ChatResponse cached = new ChatResponse("Из кэша", List.of(EventCard.from(first)), "cache");
        when(answerCache.get("концерт")).thenReturn(Optional.of(cached));

        ChatResponse response = service.chat("концерт", "user-1");

        assertThat(response.mode()).isEqualTo("cache");
        assertThat(response.answer()).isEqualTo("Из кэша");
        verify(llmClient, never()).complete(anyString(), anyString());
    }

    private void stubSearch(String query, CatalogEvent... events) {
        when(answerCache.get(query)).thenReturn(Optional.empty());
        when(retriever.retrieve(query)).thenReturn(new SearchResult(List.of(events),
                new SearchDiagnostics(intent(query), false, null, events.length, events.length, 0, 0)));
    }

    private SearchResult emptyResult(String query) {
        return new SearchResult(List.of(),
                new SearchDiagnostics(intent(query), false, null, 10, 0, 0, 0));
    }

    private QueryIntent intent(String query) {
        return new QueryIntent(null, null, null, null, List.of(), query);
    }

    private CatalogEvent event(String name) {
        return new CatalogEvent(
                UUID.randomUUID(),
                name,
                "Artist",
                "Arena, Berlin",
                LocalDateTime.now().plusDays(3),
                50.0,
                "DE",
                "Berlin",
                "Arena",
                List.of("rock"),
                "Description",
                "https://example.com/image.jpg",
                "https://example.com/event",
                List.of(new Seat("A", 1, 50.0)));
    }
}
