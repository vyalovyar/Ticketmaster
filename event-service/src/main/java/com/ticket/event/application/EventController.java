package com.ticket.event.application;

import com.ticket.event.domain.Event;
import com.ticket.event.domain.Seat;
import com.ticket.event.infrastructure.EventRepository;
import com.ticket.exception.BusinessException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final EventRepository eventRepository;
    private final TicketmasterMetadataBackfillService backfillService;

    public EventController(
            EventService eventService,
            EventRepository eventRepository,
            TicketmasterMetadataBackfillService backfillService) {
        this.eventService = eventService;
        this.eventRepository = eventRepository;
        this.backfillService = backfillService;
    }

    public record EventResponse(UUID id, String name, String artist, String location, LocalDateTime eventDate,
                                String description, String imageUrl, String sourceUrl,
                                String countryCode, String city, String venueName, List<String> genres,
                                List<Seat> seats) {}

    @GetMapping
    public ResponseEntity<List<EventResponse>> getAllEvents(
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String date
    ) {
        LocalDateTime dateFrom = null;
        LocalDateTime dateTo = null;
        if (date != null && !date.isBlank()) {
            try {
                LocalDate parsed = LocalDate.parse(date.trim());
                dateFrom = parsed.atStartOfDay();
                dateTo = parsed.plusDays(1).atStartOfDay();
            } catch (DateTimeParseException ex) {
                throw new BusinessException("Некорректная дата", HttpStatus.BAD_REQUEST, "INVALID_DATE");
            }
        }

        List<EventResponse> events = eventRepository.findUpcomingFiltered(
                        LocalDateTime.now(),
                        blankToNull(artist),
                        blankToNull(location),
                        dateFrom,
                        dateTo).stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(events);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(eventService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<UUID> createEvent(@Valid @RequestBody CreateEventRequest request) {
        UUID eventId = eventService.createEvent(
                request.name(),
                request.artist(),
                request.location(),
                request.eventDate(),
                request.seats()
        );
        return ResponseEntity.ok(eventId);
    }

    @PostMapping("/backfill-metadata")
    public ResponseEntity<TicketmasterMetadataBackfillService.BackfillResult> backfillMetadata(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(backfillService.backfill(limit));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UUID> updateEvent(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEventRequest request) {
        return ResponseEntity.ok(eventService.updateEvent(
                id,
                request.name(),
                request.artist(),
                request.location(),
                request.eventDate()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<UUID> deleteEvent(@PathVariable UUID id) {
        return ResponseEntity.ok(eventService.deleteEvent(id));
    }

    private EventResponse toResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getArtist(),
                event.getLocation(),
                event.getEventDate(),
                event.getDescription(),
                event.getImageUrl(),
                event.getSourceUrl(),
                event.getCountryCode(),
                event.getCity(),
                event.getVenueName(),
                event.getGenres(),
                event.getSeats());
    }
}
