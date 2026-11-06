package com.ticket.ai.application;

import com.ticket.ai.config.AiProperties;
import com.ticket.ai.infrastructure.AiUnavailableException;
import com.ticket.ai.infrastructure.EmbeddingClient;
import com.ticket.ai.infrastructure.EventIndexRepository;
import com.ticket.ai.infrastructure.dto.CatalogEvent;
import com.ticket.ai.infrastructure.dto.Seat;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventRetrieverTest {

    private final IntentParser intentParser = mock(IntentParser.class);
    private final EventIndexSyncService syncService = mock(EventIndexSyncService.class);
    private final EventIndexRepository repository = mock(EventIndexRepository.class);
    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
    private final EventRetriever retriever = new EventRetriever(
            intentParser, syncService, repository, embeddingClient,
            new AiProperties("cloudflare", null, "key", "model", null, "acc", null,
                    null, null, 6, null, null, null, null));

    @Test
    void passesIntentAndQueryVectorToRepository() {
        QueryIntent intent = new QueryIntent("DE", "Berlin", null, null, List.of("rock"), "");
        CatalogEvent event = event();
        float[] vector = {0.1f, 0.2f};

        when(embeddingClient.isAvailable()).thenReturn(true);
        when(intentParser.parse("рок в Берлине")).thenReturn(intent);
        when(embeddingClient.embed(List.of("рок в Берлине"))).thenReturn(List.of(vector));
        when(repository.search(intent, vector, true, null, 6)).thenReturn(result(event));

        assertThat(retriever.retrieve("рок в Берлине").events()).containsExactly(event);
        verify(syncService).ensureIndex();
    }

    @Test
    void fallsBackToFullTextWhenQueryEmbeddingFails() {
        QueryIntent intent = new QueryIntent(null, null, null, null, List.of(), "рок");
        CatalogEvent event = event();

        when(embeddingClient.isAvailable()).thenReturn(true);
        when(intentParser.parse("рок")).thenReturn(intent);
        when(embeddingClient.embed(List.of("рок"))).thenThrow(new AiUnavailableException("embedding down"));
        when(repository.search(eq(intent), eq(null), eq(true), anyString(), eq(6)))
                .thenReturn(result(event));

        assertThat(retriever.retrieve("рок").events()).containsExactly(event);
    }

    @Test
    void usesFullTextOnlyWhenEmbeddingsDisabled() {
        QueryIntent intent = new QueryIntent(null, null, null, null, List.of(), "рок");
        CatalogEvent event = event();

        when(embeddingClient.isAvailable()).thenReturn(false);
        when(embeddingClient.isEnabled()).thenReturn(false);
        when(intentParser.parse("рок")).thenReturn(intent);
        when(repository.search(intent, null, false, null, 6)).thenReturn(result(event));

        assertThat(retriever.retrieve("рок").events()).containsExactly(event);
    }

    @Test
    void reportsUnavailableWhenIndexFails() {
        when(intentParser.parse(anyString()))
                .thenReturn(new QueryIntent(null, null, null, null, List.of(), ""));
        doThrow(new IllegalStateException("db down")).when(syncService).ensureIndex();

        assertThatThrownBy(() -> retriever.retrieve("концерт"))
                .isInstanceOf(AiUnavailableException.class);
    }

    private SearchResult result(CatalogEvent event) {
        QueryIntent intent = new QueryIntent(null, null, null, null, List.of(), "");
        return new SearchResult(List.of(event),
                new SearchDiagnostics(intent, false, null, 10, 5, 0, 3));
    }

    private CatalogEvent event() {
        return new CatalogEvent(
                UUID.randomUUID(),
                "Rock Night",
                "Band",
                "Arena, Berlin",
                LocalDateTime.now().plusDays(3),
                50.0,
                "DE",
                "Berlin",
                "Arena",
                List.of("music", "rock"),
                "loud rock",
                "https://example.com/image.jpg",
                "https://example.com/event",
                List.of(new Seat("A", 1, 50.0)));
    }
}
