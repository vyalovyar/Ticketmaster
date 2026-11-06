package com.ticket.event.application;

import com.ticket.event.domain.Event;
import com.ticket.event.infrastructure.EventRepository;
import com.ticket.event.infrastructure.ticketmaster.TicketmasterClient;
import com.ticket.event.infrastructure.ticketmaster.dto.TicketmasterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class TicketmasterMetadataBackfillService {

    private static final Logger log = LoggerFactory.getLogger(TicketmasterMetadataBackfillService.class);
    private static final int MAX_LIMIT = 200;
    private static final long RATE_LIMIT_PAUSE_MS = 250;

    private final EventRepository eventRepository;
    private final TicketmasterClient ticketmasterClient;
    private final TicketmasterImportService importService;
    private final EventService eventService;

    public TicketmasterMetadataBackfillService(
            EventRepository eventRepository,
            TicketmasterClient ticketmasterClient,
            TicketmasterImportService importService,
            EventService eventService) {
        this.eventRepository = eventRepository;
        this.ticketmasterClient = ticketmasterClient;
        this.importService = importService;
        this.eventService = eventService;
    }

    public BackfillResult backfill(int limit) {
        int effectiveLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<Event> events = eventRepository.findMissingMetadata(PageRequest.of(0, effectiveLimit));

        int updated = 0;
        int failed = 0;

        for (Event event : events) {
            try {
                TicketmasterEvent ticketmasterEvent = ticketmasterClient.getEvent(event.getExternalId());
                TicketmasterMetadata metadata = importService.extractMetadata(ticketmasterEvent);
                eventService.backfillMetadata(event.getId(), metadata);
                updated++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("Could not backfill event {}: {}", event.getExternalId(), e.getMessage());
            }

            try {
                Thread.sleep(RATE_LIMIT_PAUSE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        long remaining = eventRepository
                .countByDeletedAtIsNullAndExternalIdIsNotNullAndCountryCodeIsNull();
        log.info("Metadata backfill finished: updated={} failed={} remaining={}", updated, failed, remaining);
        return new BackfillResult(updated, failed, remaining);
    }

    public record BackfillResult(int updated, int failed, long remaining) {
    }
}
