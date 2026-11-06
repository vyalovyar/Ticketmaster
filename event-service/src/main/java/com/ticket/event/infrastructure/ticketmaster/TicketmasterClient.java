package com.ticket.event.infrastructure.ticketmaster;

import com.ticket.event.infrastructure.ticketmaster.dto.TicketmasterEvent;
import com.ticket.event.infrastructure.ticketmaster.dto.TicketmasterSearchResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Set;


@Component
public class TicketmasterClient {

    private static final String EVENTS_PATH = "/discovery/v2/events.json";
    private static final Set<String> ALLOWED_SORTS = Set.of("date,asc", "date,desc", "name,asc", "name,desc", "random");
    private static final int IMPORT_HORIZON_DAYS = 365;
    private static final DateTimeFormatter API_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private final RestClient restClient;
    private final TicketmasterProperties properties;

    public TicketmasterClient(RestClient.Builder builder, TicketmasterProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(15_000);

        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.properties = properties;
    }

    public TicketmasterEvent getEvent(String eventId) {
        if (!properties.isConfigured()) {
            throw new TicketmasterImportException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "TICKETMASTER_NOT_CONFIGURED",
                    "Не задан TICKETMASTER_API_KEY");
        }
        try {
            TicketmasterEvent event = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/discovery/v2/events/{id}.json")
                            .queryParam("apikey", properties.apiKey())
                            .build(eventId))
                    .retrieve()
                    .body(TicketmasterEvent.class);
            if (event == null) {
                throw new TicketmasterImportException(
                        HttpStatus.BAD_GATEWAY,
                        "TICKETMASTER_EMPTY_RESPONSE",
                        "Ticketmaster вернул пустое событие");
            }
            return event;
        } catch (RestClientResponseException e) {
            throw new TicketmasterImportException(
                    e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                            ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY,
                    "TICKETMASTER_API_ERROR",
                    "Ticketmaster API ответил " + e.getStatusCode().value());
        } catch (TicketmasterImportException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new TicketmasterImportException(
                    HttpStatus.BAD_GATEWAY,
                    "TICKETMASTER_UNAVAILABLE",
                    "Не удалось получить событие из Ticketmaster: " + e.getMessage());
        }
    }

    public TicketmasterSearchResponse searchEvents(String keyword, String countryCode, String city,
                                                   String classificationName, String sort, int size, int page) {
        if (!properties.isConfigured()) {
            throw new TicketmasterImportException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "TICKETMASTER_NOT_CONFIGURED",
                    "Не задан TICKETMASTER_API_KEY.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String startDateTime = now.minusMinutes(1).format(API_DATE_FORMAT);
        String endDateTime = now.plusDays(IMPORT_HORIZON_DAYS).format(API_DATE_FORMAT);

        try {
            TicketmasterSearchResponse response = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(EVENTS_PATH)
                                .queryParam("apikey", properties.apiKey())
                                .queryParam("size", size)
                                .queryParam("page", page)
                                .queryParam("sort", resolveSort(sort))
                                .queryParam("startDateTime", startDateTime)
                                .queryParam("endDateTime", endDateTime);

                        if (hasText(keyword)) {
                            uriBuilder.queryParam("keyword", keyword.trim());
                        }
                        if (hasText(countryCode)) {
                            uriBuilder.queryParam("countryCode", countryCode.trim().toUpperCase());
                        }
                        if (hasText(city)) {
                            uriBuilder.queryParam("city", city.trim());
                        }
                        if (hasText(classificationName)) {
                            uriBuilder.queryParam("classificationName", classificationName.trim());
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(TicketmasterSearchResponse.class);

            if (response == null) {
                throw new TicketmasterImportException(
                        HttpStatus.BAD_GATEWAY,
                        "TICKETMASTER_EMPTY_RESPONSE",
                        "Ticketmaster вернул пустой ответ");
            }
            return response;
        } catch (RestClientResponseException e) {
            HttpStatus status = e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()
                    ? HttpStatus.TOO_MANY_REQUESTS
                    : HttpStatus.BAD_GATEWAY;
            String hint = e.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()
                    || e.getStatusCode().value() == HttpStatus.FORBIDDEN.value()
                    ? " Проверьте TICKETMASTER_API_KEY."
                    : "";
            throw new TicketmasterImportException(
                    status,
                    "TICKETMASTER_API_ERROR",
                    "Ticketmaster API ответил " + e.getStatusCode().value() + hint);
        } catch (TicketmasterImportException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new TicketmasterImportException(
                    HttpStatus.BAD_GATEWAY,
                    "TICKETMASTER_UNAVAILABLE",
                    "Не удалось обратиться к Ticketmaster API: " + e.getMessage());
        }
    }

    private String resolveSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "date,asc";
        }
        return ALLOWED_SORTS.contains(sort.trim()) ? sort.trim() : "date,asc";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
