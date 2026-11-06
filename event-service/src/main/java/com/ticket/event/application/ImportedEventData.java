package com.ticket.event.application;

import com.ticket.event.domain.Seat;

import java.time.LocalDateTime;
import java.util.List;

public record ImportedEventData(
        String name,
        String artist,
        String location,
        LocalDateTime eventDate,
        String description,
        String imageUrl,
        String sourceUrl,
        String countryCode,
        String city,
        String venueName,
        List<String> genres,
        String externalId,
        List<Seat> seats
) {
}
