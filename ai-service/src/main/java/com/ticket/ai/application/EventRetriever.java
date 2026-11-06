package com.ticket.ai.application;

import com.ticket.ai.config.AiProperties;
import com.ticket.ai.infrastructure.AiUnavailableException;
import com.ticket.ai.infrastructure.EmbeddingClient;
import com.ticket.ai.infrastructure.EventIndexRepository;
import com.ticket.ai.infrastructure.dto.CatalogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EventRetriever {

    private static final Logger log = LoggerFactory.getLogger(EventRetriever.class);

    private final IntentParser intentParser;
    private final EventIndexSyncService syncService;
    private final EventIndexRepository indexRepository;
    private final EmbeddingClient embeddingClient;
    private final AiProperties properties;

    public EventRetriever(
            IntentParser intentParser,
            EventIndexSyncService syncService,
            EventIndexRepository indexRepository,
            EmbeddingClient embeddingClient,
            AiProperties properties) {
        this.intentParser = intentParser;
        this.syncService = syncService;
        this.indexRepository = indexRepository;
        this.embeddingClient = embeddingClient;
        this.properties = properties;
    }

    public SearchResult retrieve(String query) {
        QueryIntent intent = intentParser.parse(query);

        try {
            syncService.ensureIndex();
        } catch (RuntimeException e) {
            throw new AiUnavailableException("Индекс событий временно недоступен: " + e.getMessage(), e);
        }

        boolean semanticRequested = embeddingClient.isAvailable();
        float[] queryVector = null;
        String embeddingError = null;

        if (embeddingClient.isEnabled() && !embeddingClient.isAvailable()) {
            embeddingError = embeddingClient.unavailableReason();
        } else if (embeddingClient.isAvailable()) {
            try {
                List<float[]> vectors = embeddingClient.embed(List.of(query));
                queryVector = vectors.isEmpty() ? null : vectors.get(0);
            } catch (RuntimeException e) {
                embeddingError = e.getMessage();
                log.warn("Query embedding failed, falling back to full-text: {}", e.getMessage());
            }
        }

        try {
            return indexRepository.search(intent, queryVector, semanticRequested, embeddingError,
                    properties.topK());
        } catch (RuntimeException e) {
            throw new AiUnavailableException("Поиск по индексу временно недоступен: " + e.getMessage(), e);
        }
    }

    public List<CatalogEvent> retrieveEvents(String query) {
        return retrieve(query).events();
    }
}
