package com.ticket.event.infrastructure.ticketmaster;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ticketmaster")
public record TicketmasterProperties(String apiKey, String baseUrl) {

    private static final String DEFAULT_BASE_URL = "https://app.ticketmaster.com";

    public TicketmasterProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
