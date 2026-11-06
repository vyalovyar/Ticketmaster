package com.ticket.ai.application;

import java.util.List;


public record ChatResponse(String answer, List<EventCard> events, String mode) {
}
