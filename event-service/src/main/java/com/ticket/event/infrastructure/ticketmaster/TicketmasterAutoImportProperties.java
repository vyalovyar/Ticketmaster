package com.ticket.event.infrastructure.ticketmaster;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ticketmaster.auto-import")
public record TicketmasterAutoImportProperties(
        boolean enabled,
        boolean startupImport,
        String cron,
        String keyword,
        String countryCode,
        String city,
        String classificationName,
        String sort,
        int size,
        int pages
) {

    public TicketmasterAutoImportProperties {
        if (cron == null || cron.isBlank()) {
            cron = "0 0 3 * * *";
        }
        if (sort == null || sort.isBlank()) {
            sort = "date,asc";
        }
        if (size <= 0) {
            size = 50;
        }
        if (pages <= 0) {
            pages = 1;
        }
    }
}
