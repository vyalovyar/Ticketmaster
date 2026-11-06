package com.ticket.event.application;

import java.util.List;


public record TicketmasterMetadata(
        String artist,
        String location,
        String description,
        String imageUrl,
        String sourceUrl,
        String countryCode,
        String city,
        String venueName,
        List<String> genres
) {
}
