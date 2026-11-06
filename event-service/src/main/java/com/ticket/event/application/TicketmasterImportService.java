package com.ticket.event.application;

import com.ticket.event.domain.Seat;
import com.ticket.event.infrastructure.EventRepository;
import com.ticket.event.infrastructure.ticketmaster.TicketmasterClient;
import com.ticket.event.infrastructure.ticketmaster.TicketmasterImportResult;
import com.ticket.event.infrastructure.ticketmaster.dto.TicketmasterEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class TicketmasterImportService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_PAGES = 5;

    private static final int ROWS = 8;
    private static final int SEATS_PER_ROW = 10;
    private static final double DEFAULT_ROW_PRICE_STEP = 7.5;

    private final TicketmasterClient ticketmasterClient;
    private final EventService eventService;
    private final EventRepository eventRepository;

    public TicketmasterImportService(
            TicketmasterClient ticketmasterClient,
            EventService eventService,
            EventRepository eventRepository) {
        this.ticketmasterClient = ticketmasterClient;
        this.eventService = eventService;
        this.eventRepository = eventRepository;
    }

    public TicketmasterImportResult importEvents(String keyword, String countryCode, String city,
                                                 String classificationName, String sort, int size, int pages) {
        int pageSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int pageCount = pages <= 0 ? 1 : Math.min(pages, MAX_PAGES);

        int fetched = 0;
        int imported = 0;
        int skippedExisting = 0;
        int skippedInvalid = 0;
        List<UUID> createdEventIds = new ArrayList<>();
        Set<String> seenExternalIds = new HashSet<>();

        for (int page = 0; page < pageCount; page++) {
            List<TicketmasterEvent> events = ticketmasterClient
                    .searchEvents(keyword, countryCode, city, classificationName, sort, pageSize, page)
                    .events();

            if (events.isEmpty()) {
                break;
            }

            for (TicketmasterEvent ticketmasterEvent : events) {
                fetched++;

                String externalId = trimToNull(ticketmasterEvent.id());
                if (externalId == null) {
                    skippedInvalid++;
                    continue;
                }

                if (!seenExternalIds.add(externalId)) {
                    skippedExisting++;
                    continue;
                }

                Optional<LocalDateTime> eventDate = resolveEventDate(ticketmasterEvent);
                if (eventDate.isEmpty() || !isFuture(ticketmasterEvent, eventDate.get())) {
                    skippedInvalid++;
                    continue;
                }

                String name = truncate(trimToNull(ticketmasterEvent.name()), 120);
                if (name == null) {
                    skippedInvalid++;
                    continue;
                }

                String artist = truncate(resolveArtist(ticketmasterEvent, name), 120);
                String location = truncate(resolveLocation(ticketmasterEvent), 200);
                String description = truncate(resolveDescription(ticketmasterEvent), 4000);
                String imageUrl = truncate(ticketmasterEvent.bestImageUrl().orElse(null), 500);
                String sourceUrl = truncate(trimToNull(ticketmasterEvent.url()), 500);
                String eventCountryCode = truncate(resolveCountryCode(ticketmasterEvent), 2);
                String eventCity = truncate(resolveCity(ticketmasterEvent), 120);
                String venueName = truncate(resolveVenueName(ticketmasterEvent), 200);
                List<String> genres = resolveGenres(ticketmasterEvent);
                List<Seat> seats = buildSeats(ticketmasterEvent);

                ImportedEventData data = new ImportedEventData(
                        name,
                        artist,
                        location,
                        eventDate.get(),
                        description,
                        imageUrl,
                        sourceUrl,
                        eventCountryCode,
                        eventCity,
                        venueName,
                        genres,
                        externalId,
                        seats);

                if (eventRepository.findByExternalId(externalId).isPresent()) {
                    eventService.updateImportedMetadata(data);
                    skippedExisting++;
                    continue;
                }

                UUID eventId = eventService.createImportedEvent(data);
                createdEventIds.add(eventId);
                imported++;
            }

            if (events.size() < pageSize) {
                break;
            }
        }

        return new TicketmasterImportResult(fetched, imported, skippedExisting, skippedInvalid, createdEventIds);
    }

    private Optional<LocalDateTime> resolveEventDate(TicketmasterEvent event) {
        if (event.dates() == null || event.dates().start() == null) {
            return Optional.empty();
        }

        TicketmasterEvent.Start start = event.dates().start();
        if (Boolean.TRUE.equals(start.dateTBA())
                || Boolean.TRUE.equals(start.dateTBD())
                || Boolean.TRUE.equals(start.timeTBA())
                || Boolean.TRUE.equals(start.noSpecificTime())) {
            return Optional.empty();
        }

        String localDate = trimToNull(start.localDate());
        String localTime = trimToNull(start.localTime());
        if (localDate == null || localTime == null) {
            return Optional.empty();
        }

        String dateTime = trimToNull(start.dateTime());
        if (dateTime != null) {
            try {
                return Optional.of(OffsetDateTime.parse(dateTime)
                        .atZoneSameInstant(ZoneId.systemDefault())
                        .toLocalDateTime());
            } catch (DateTimeParseException ignored) {
                // ниже используем localDate/localTime площадки
            }
        }

        try {
            LocalDateTime venueLocal = LocalDateTime.of(LocalDate.parse(localDate), LocalTime.parse(localTime));
            return Optional.of(toSystemZone(venueLocal, event.dates().timezone()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }


    private LocalDateTime toSystemZone(LocalDateTime venueLocal, String venueTimezone) {
        if (venueTimezone == null || venueTimezone.isBlank()) {
            return venueLocal;
        }
        try {
            return ZonedDateTime.of(venueLocal, ZoneId.of(venueTimezone))
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (RuntimeException e) {
            return venueLocal;
        }
    }

    private boolean isFuture(TicketmasterEvent event, LocalDateTime eventDate) {
        String dateTime = trimToNull(event.dates().start().dateTime());
        if (dateTime != null) {
            try {
                return OffsetDateTime.parse(dateTime).toInstant().isAfter(Instant.now());
            } catch (DateTimeParseException ignored) {
                // fallback — сравнение уже приведённого к зоне сервера времени
            }
        }
        return eventDate.isAfter(LocalDateTime.now());
    }

    private String resolveArtist(TicketmasterEvent event, String fallbackName) {
        return event.firstAttraction()
                .map(TicketmasterEvent.Attraction::name)
                .map(this::trimToNull)
                .orElse(fallbackName);
    }

    private String resolveDescription(TicketmasterEvent event) {
        String description = firstNonBlank(event.description(), event.info(), event.pleaseNote());
        return description == null ? null : stripHtml(description);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String stripHtml(String html) {
        String text = html
                .replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("(?i)</p>", " ")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
        return trimToNull(text);
    }

    public TicketmasterMetadata extractMetadata(TicketmasterEvent event) {
        return new TicketmasterMetadata(
                resolveArtist(event, event.name()),
                resolveLocation(event),
                resolveDescription(event),
                event.bestImageUrl().orElse(null),
                trimToNull(event.url()),
                resolveCountryCode(event),
                resolveCity(event),
                resolveVenueName(event),
                resolveGenres(event));
    }

    private String resolveCountryCode(TicketmasterEvent event) {
        return event.firstVenue()
                .map(TicketmasterEvent.Venue::country)
                .map(TicketmasterEvent.Country::countryCode)
                .map(this::trimToNull)
                .orElse(null);
    }

    private String resolveCity(TicketmasterEvent event) {
        return event.firstVenue()
                .map(TicketmasterEvent.Venue::city)
                .map(TicketmasterEvent.City::name)
                .map(this::trimToNull)
                .orElse(null);
    }

    private String resolveVenueName(TicketmasterEvent event) {
        return event.firstVenue()
                .map(TicketmasterEvent.Venue::name)
                .map(this::trimToNull)
                .orElse(null);
    }

    private List<String> resolveGenres(TicketmasterEvent event) {
        if (event.classifications() == null || event.classifications().isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> genres = new java.util.LinkedHashSet<>();
        for (TicketmasterEvent.Classification classification : event.classifications()) {
            addGenre(genres, classification.segment() == null ? null : classification.segment().name());
            addGenre(genres, classification.genre() == null ? null : classification.genre().name());
            addGenre(genres, classification.subGenre() == null ? null : classification.subGenre().name());
        }
        return genres.stream().limit(10).toList();
    }

    private void addGenre(java.util.Set<String> genres, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.equals("undefined") && !normalized.equals("other")) {
            genres.add(normalized);
        }
    }

    private String resolveLocation(TicketmasterEvent event) {
        Optional<TicketmasterEvent.Venue> venue = event.firstVenue();
        if (venue.isEmpty()) {
            return "TBA";
        }

        String venueName = trimToNull(venue.get().name());
        String cityName = venue.get().city() == null ? null : trimToNull(venue.get().city().name());

        if (venueName != null && cityName != null) {
            return venueName + ", " + cityName;
        }
        if (venueName != null) {
            return venueName;
        }
        if (cityName != null) {
            return cityName;
        }
        return "TBA";
    }

    private List<Seat> buildSeats(TicketmasterEvent event) {
        double basePrice = event.firstPriceRange()
                .map(TicketmasterEvent.PriceRange::min)
                .filter(price -> price != null && price > 0)
                .orElseGet(() -> defaultBasePrice(event));

        double rowStep = event.firstPriceRange()
                .map(range -> rowStep(range, basePrice))
                .orElse(DEFAULT_ROW_PRICE_STEP);

        List<Seat> seats = new ArrayList<>(ROWS * SEATS_PER_ROW);
        for (int row = 0; row < ROWS; row++) {
            String rowLabel = String.valueOf((char) ('A' + row));
            double rowPrice = roundPrice(basePrice + row * rowStep);
            for (int seatNumber = 1; seatNumber <= SEATS_PER_ROW; seatNumber++) {
                seats.add(new Seat(rowLabel, seatNumber, rowPrice));
            }
        }
        return seats;
    }

    /**
     * Если Ticketmaster не отдал цены, берём стабильную «случайную» цену по id события,
     * чтобы в каталоге не было одинаковых $49 у всех подряд.
     */
    private double defaultBasePrice(TicketmasterEvent event) {
        int hash = Math.floorMod(java.util.Objects.hashCode(event.id()), 61);
        return 29 + hash;
    }

    private double rowStep(TicketmasterEvent.PriceRange range, double basePrice) {
        if (range.max() == null || range.max() <= basePrice) {
            return DEFAULT_ROW_PRICE_STEP;
        }
        return (range.max() - basePrice) / (ROWS - 1);
    }

    private double roundPrice(double value) {
        return Math.max(0.01, Math.round(value * 100.0) / 100.0);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }
}
