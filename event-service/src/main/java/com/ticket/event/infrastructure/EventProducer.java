package com.ticket.event.infrastructure;

import com.ticket.dto.CreatedEvent;
import com.ticket.dto.EventDeletedEvent;
import com.ticket.dto.EventUpdatedEvent;
import com.ticket.dto.event.EventEnvelope;
import com.ticket.dto.event.EventEnvelopeMapper;
import com.ticket.dto.event.EventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventProducer {
    private static final Logger log = LoggerFactory.getLogger(EventProducer.class);
    private static final String TOPIC = "ticketmaster.events";

    private final KafkaTemplate<String, EventEnvelope> kafkaTemplate;

    public EventProducer(KafkaTemplate<String, EventEnvelope> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishEventCreated(CreatedEvent event) {
        send(event.eventId().toString(), EventEnvelopeMapper.wrap(EventTypes.EVENT_CREATED, event));
    }

    public void publishEventDeleted(EventDeletedEvent event) {
        send(event.eventId().toString(), EventEnvelopeMapper.wrap(EventTypes.EVENT_DELETED, event));
    }

    public void publishEventUpdated(EventUpdatedEvent event) {
        send(event.eventId().toString(), EventEnvelopeMapper.wrap(EventTypes.EVENT_UPDATED, event));
    }

    private void send(String key, EventEnvelope envelope) {
        kafkaTemplate.send(TOPIC, key, envelope)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Событие {} отправлено в Kafka", envelope.eventType());
                    } else {
                        log.error("Ошибка отправки в Kafka", ex);
                    }
                });
    }
}