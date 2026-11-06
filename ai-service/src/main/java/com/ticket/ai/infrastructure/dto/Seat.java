package com.ticket.ai.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Seat(String row, Integer seatNumber, Double price) {
}
