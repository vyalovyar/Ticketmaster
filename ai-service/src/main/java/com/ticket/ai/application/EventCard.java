package com.ticket.ai.application;

import com.ticket.ai.infrastructure.dto.CatalogEvent;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventCard(
        UUID id,
        String name,
        String artist,
        String location,
        LocalDateTime eventDate,
        Double priceFrom,
        String imageUrl,
        String sourceUrl
) {

    public static EventCard from(CatalogEvent event) {
        return new EventCard(
                event.id(),
                event.name(),
                event.artist(),
                event.location(),
                event.eventDate(),
                event.priceFrom(),
                event.imageUrl(),
                event.sourceUrl());
    }
}
