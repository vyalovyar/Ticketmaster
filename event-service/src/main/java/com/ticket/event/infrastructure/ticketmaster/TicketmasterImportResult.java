package com.ticket.event.infrastructure.ticketmaster;

import java.util.List;
import java.util.UUID;

public record TicketmasterImportResult(
        int fetched,
        int imported,
        int skippedExisting,
        int skippedInvalid,
        List<UUID> createdEventIds
) {
    public TicketmasterImportResult {
        createdEventIds = createdEventIds == null ? List.of() : List.copyOf(createdEventIds);
    }
}
