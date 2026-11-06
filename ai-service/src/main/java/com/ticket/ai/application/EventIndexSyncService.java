package com.ticket.ai.application;

import com.ticket.ai.config.EmbeddingProperties;
import com.ticket.ai.infrastructure.EmbeddingClient;
import com.ticket.ai.infrastructure.EventCatalogClient;
import com.ticket.ai.infrastructure.EventIndexRepository;
import com.ticket.ai.infrastructure.dto.CatalogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EventIndexSyncService {

    private static final Logger log = LoggerFactory.getLogger(EventIndexSyncService.class);
    private static final Duration SYNC_TTL = Duration.ofMinutes(5);
    private static final Duration SYNC_DEBOUNCE = Duration.ofSeconds(10);

    private final EventCatalogClient catalogClient;
    private final EmbeddingClient embeddingClient;
    private final EventIndexRepository indexRepository;
    private final EventDocumentBuilder documentBuilder;
    private final AiUsageService usageService;
    private final EmbeddingProperties embeddingProperties;

    private volatile Instant lastSync = Instant.EPOCH;
    private final Object syncLock = new Object();

    public EventIndexSyncService(
            EventCatalogClient catalogClient,
            EmbeddingClient embeddingClient,
            EventIndexRepository indexRepository,
            EventDocumentBuilder documentBuilder,
            AiUsageService usageService,
            EmbeddingProperties embeddingProperties) {
        this.catalogClient = catalogClient;
        this.embeddingClient = embeddingClient;
        this.indexRepository = indexRepository;
        this.documentBuilder = documentBuilder;
        this.usageService = usageService;
        this.embeddingProperties = embeddingProperties;
    }

    public void ensureIndex() {
        if (indexRepository.count() > 0 && Instant.now().isBefore(lastSync.plus(SYNC_TTL))) {
            return;
        }
        sync();
    }

    public void updateEvent(UUID eventId) {
        CatalogEvent event = catalogClient.fetchEvent(eventId);
        String content = documentBuilder.content(event);
        String hash = documentBuilder.hash(content);

        float[] embedding = embedOne(content, eventId);
        indexRepository.upsertAll(List.of(event), List.of(embedding),
                List.of(content), List.of(hash));
        usageService.recordEmbeddingCall(usageService.estimateEmbeddingNeurons(content.length()));
        log.info("AI index updated from Kafka for event {}", eventId);
    }

    public void deleteEvent(UUID eventId) {
        indexRepository.delete(eventId);
        log.info("AI index deleted event {}", eventId);
    }

    @Scheduled(initialDelayString = "${app.index.initial-delay-ms:15000}",
            fixedDelayString = "${app.index.sync-ms:300000}")
    public void scheduledSync() {
        try {
            sync();
        } catch (RuntimeException e) {
            log.warn("Scheduled index sync failed: {}", e.getMessage());
        }
    }

    public void sync() {
        synchronized (syncLock) {
            if (Instant.now().isBefore(lastSync.plus(SYNC_DEBOUNCE))) {
                return;
            }
            lastSync = Instant.now();

            List<CatalogEvent> events = catalogClient.fetchActiveEvents();
            if (events.isEmpty()) {
                log.warn("Event catalog is empty; index sync skipped");
                return;
            }

            Set<UUID> activeIds = events.stream().map(CatalogEvent::id).collect(Collectors.toSet());
            indexRepository.deleteMissing(activeIds);

            Map<UUID, String> existingHashes = indexRepository.findHashes();
            List<CatalogEvent> changed = new ArrayList<>();
            List<String> contents = new ArrayList<>();
            List<String> hashes = new ArrayList<>();

            for (CatalogEvent event : events) {
                String content = documentBuilder.content(event);
                String hash = documentBuilder.hash(content);
                if (!hash.equals(existingHashes.get(event.id()))) {
                    changed.add(event);
                    contents.add(content);
                    hashes.add(hash);
                }
            }

            if (changed.isEmpty()) {
                log.info("AI event index synced: total={} changed=0", events.size());
                return;
            }

            List<float[]> embeddings = embedBatch(contents);
            indexRepository.upsertAll(changed, embeddings, contents, hashes);

            int chars = contents.stream().mapToInt(String::length).sum();
            usageService.recordEmbeddingCall(usageService.estimateEmbeddingNeurons(chars));
            log.info("AI event index synced: total={} changed={}", events.size(), changed.size());
        }
    }

    private List<float[]> embedBatch(List<String> contents) {
        if (!embeddingClient.isAvailable()) {
            log.warn("Embeddings unavailable ({}); storing events without vectors",
                    embeddingClient.unavailableReason());
            return emptyEmbeddings(contents.size());
        }
        int batchSize = embeddingProperties.batchSize();
        List<float[]> embeddings = new ArrayList<>(contents.size());
        for (int from = 0; from < contents.size(); from += batchSize) {
            int to = Math.min(from + batchSize, contents.size());
            try {
                embeddings.addAll(embeddingClient.embed(contents.subList(from, to)));
            } catch (RuntimeException e) {
                log.warn("Embedding batch [{}, {}) failed: {}", from, to, e.getMessage());
                embeddings.addAll(emptyEmbeddings(to - from));
            }
        }
        if (embeddings.size() != contents.size()) {
            throw new IllegalStateException("Embedding count mismatch: events="
                    + contents.size() + " vectors=" + embeddings.size());
        }
        return embeddings;
    }

    private float[] embedOne(String content, UUID eventId) {
        if (!embeddingClient.isAvailable()) {
            log.warn("Embeddings unavailable ({}); event {} stored without vector",
                    embeddingClient.unavailableReason(), eventId);
            return new float[0];
        }
        List<float[]> vectors = embeddingClient.embed(List.of(content));
        if (vectors.isEmpty()) {
            throw new IllegalStateException("Embedding model returned no vector for event " + eventId);
        }
        return vectors.get(0);
    }

    private List<float[]> emptyEmbeddings(int count) {
        List<float[]> vectors = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            vectors.add(new float[0]);
        }
        return vectors;
    }
}
