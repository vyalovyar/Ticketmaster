package com.ticket.event.infrastructure.ticketmaster.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;


@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketmasterSearchResponse(
        @JsonProperty("_embedded") Embedded embedded,
        Page page
) {

    public List<TicketmasterEvent> events() {
        if (embedded == null || embedded.events() == null) {
            return List.of();
        }
        return embedded.events();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Embedded(List<TicketmasterEvent> events) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(int size, int totalElements, int totalPages, int number) {
    }
}
