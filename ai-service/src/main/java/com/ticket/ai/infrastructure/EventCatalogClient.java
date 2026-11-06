package com.ticket.ai.infrastructure;

import com.ticket.ai.infrastructure.dto.CatalogEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Component
public class EventCatalogClient {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final String eventServiceBaseUrl;
    private volatile List<CatalogEvent> cachedEvents = List.of();
    private volatile Instant cachedAt = Instant.EPOCH;

    public EventCatalogClient(
            RestClient.Builder builder,
            @Value("${app.event-service.base-url:http://localhost:8081}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3_000);
        requestFactory.setReadTimeout(10_000);

        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.eventServiceBaseUrl = baseUrl;
    }

    public CatalogEvent fetchEvent(UUID eventId) {
        try {
            CatalogEvent event = restClient.get()
                    .uri("/api/events/{id}", eventId)
                    .retrieve()
                    .body(CatalogEvent.class);
            if (event == null) {
                throw new AiUnavailableException("Событие " + eventId + " не найдено в event-service");
            }
            return event;
        } catch (RestClientException e) {
            throw new AiUnavailableException(
                    "Не удалось получить событие " + eventId + " из event-service", e);
        }
    }

    public List<CatalogEvent> fetchActiveEvents() {
        if (Instant.now().isBefore(cachedAt.plus(CACHE_TTL))) {
            return cachedEvents;
        }

        synchronized (this) {
            if (Instant.now().isBefore(cachedAt.plus(CACHE_TTL))) {
                return cachedEvents;
            }
            try {
                CatalogEvent[] events = restClient.get()
                        .uri("/api/events")
                        .retrieve()
                        .body(CatalogEvent[].class);

                cachedEvents = events == null ? List.of() : List.of(events);
                cachedAt = Instant.now();
                return cachedEvents;
            } catch (RestClientException e) {
                throw new AiUnavailableException(
                        "Не удалось получить каталог событий из event-service. Проверь, что сервис запущен на "
                                + eventServiceBaseUrl, e);
            }
        }
    }
}
