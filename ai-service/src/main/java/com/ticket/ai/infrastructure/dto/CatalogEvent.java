package com.ticket.ai.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogEvent(
        UUID id,
        String name,
        String artist,
        String location,
        LocalDateTime eventDate,
        Double startingPrice,
        String countryCode,
        String city,
        String venueName,
        List<String> genres,
        String description,
        String imageUrl,
        String sourceUrl,
        List<Seat> seats
) {

    public CatalogEvent {
        if (genres == null) {
            genres = List.of();
        }
        if (seats == null) {
            seats = List.of();
        }
    }

    public Double priceFrom() {
        if (startingPrice != null && startingPrice > 0) {
            return startingPrice;
        }
        return seats.stream()
                .map(Seat::price)
                .filter(Objects::nonNull)
                .filter(price -> price > 0)
                .min(Double::compareTo)
                .orElse(null);
    }
}
