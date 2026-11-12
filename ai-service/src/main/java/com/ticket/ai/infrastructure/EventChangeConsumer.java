package com.ticket.ai.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.ai.application.EventIndexSyncService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;


@Component
public class EventChangeConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventChangeConsumer.class);

    private final ObjectMapper objectMapper;
    private final EventIndexSyncService syncService;

    public EventChangeConsumer(ObjectMapper objectMapper, EventIndexSyncService syncService) {
        this.objectMapper = objectMapper;
        this.syncService = syncService;
    }

    @KafkaListener(topics = "ticketmaster.events", groupId = "ai-service-group")
    public void onEvent(ConsumerRecord<String, String> record) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventType = root.path("eventType").asText();
            String eventId = root.path("payload").path("eventId").asText();

            if (eventId.isBlank()) {
                log.warn("Kafka event without eventId: {}", record.value());
                return;
            }

            switch (eventType) {
                case "EVENT_CREATED", "EVENT_UPDATED" -> syncService.updateEvent(UUID.fromString(eventId));
                case "EVENT_DELETED" -> syncService.deleteEvent(UUID.fromString(eventId));
                default -> log.debug("Ignoring event type {}", eventType);
            }
        } catch (JsonProcessingException e) {
            log.warn("Skipping unreadable event from Kafka: {}", record.value(), e);
        } catch (IllegalArgumentException e) {
            log.warn("Skipping malformed event from Kafka: {}", record.value(), e);
        } catch (RuntimeException e) {
            log.error("Could not process event from Kafka, retrying: {}", e.getMessage(), e);
            throw e;
        }
    }
}
