package com.ticket.ai.application;

import java.time.LocalDate;
import java.util.List;

public record QueryIntent(
        String countryCode,
        String city,
        LocalDate dateFrom,
        LocalDate dateTo,
        List<String> genres,
        String keywords
) {

    public QueryIntent {
        genres = genres == null ? List.of() : List.copyOf(genres);
    }
}
