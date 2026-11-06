package com.ticket.ai.application;

import com.ticket.ai.infrastructure.dto.CatalogEvent;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class EventDocumentBuilder {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public String content(CatalogEvent event) {
        StringBuilder text = new StringBuilder();
        append(text, event.name());
        append(text, event.artist());
        append(text, event.venueName());
        append(text, event.city());
        append(text, event.countryCode());
        if (!event.genres().isEmpty()) {
            append(text, String.join(" ", event.genres()));
        }
        append(text, event.eventDate() == null ? null : DATE_FORMAT.format(event.eventDate()));
        append(text, event.priceFrom() == null ? null : String.format(Locale.ROOT, "%.2f", event.priceFrom()));
        append(text, event.description());
        append(text, event.location());
        return text.toString().trim();
    }

    public String hash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private void append(StringBuilder text, String value) {
        if (value != null && !value.isBlank()) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(value.trim());
        }
    }
}
