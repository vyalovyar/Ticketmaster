package com.ticket.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public record EventUpdatedEvent(
        UUID eventId,
        LocalDateTime updatedAt
) implements Serializable {
}
