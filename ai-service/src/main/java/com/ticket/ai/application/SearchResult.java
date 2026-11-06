package com.ticket.ai.application;

import com.ticket.ai.infrastructure.dto.CatalogEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record SearchResult(List<CatalogEvent> events, SearchDiagnostics diagnostics) {

    public SearchResult {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }

    public SearchResult withEmbeddingError(String error) {
        return new SearchResult(events, diagnostics.withEmbeddingError(error));
    }

    public Optional<String> emptyReason() {
        if (!events.isEmpty()) {
            return Optional.empty();
        }
        if (diagnostics.indexed() == 0) {
            return Optional.of("Индекс событий пуст: каталог ещё не синхронизирован.");
        }
        if (diagnostics.semanticSkipped()) {
            return Optional.of("Смысловой поиск недоступен: " + diagnostics.embeddingError());
        }
        if (diagnostics.matchingFilters() == 0) {
            return Optional.of("В каталоге нет событий по фильтрам" + describeFilters() + ".");
        }
        if (diagnostics.semanticRequested() && diagnostics.withEmbeddings() == 0) {
            return Optional.of("У подходящих событий ещё нет векторных представлений.");
        }
        if (diagnostics.hasKeywords() && diagnostics.keywordHits() == 0) {
            return Optional.of("Совпадений по словам «" + diagnostics.intent().keywords()
                    + "» нет, и смысловой поиск ничего не дал.");
        }
        return Optional.of("Подходящих событий не нашёл.");
    }

    private String describeFilters() {
        QueryIntent intent = diagnostics.intent();
        List<String> parts = new ArrayList<>();
        if (hasText(intent.countryCode())) {
            parts.add("страна=" + intent.countryCode());
        }
        if (hasText(intent.city())) {
            parts.add("город=" + intent.city());
        }
        if (intent.dateFrom() != null || intent.dateTo() != null) {
            parts.add("даты=" + intent.dateFrom() + ".." + intent.dateTo());
        }
        if (!intent.genres().isEmpty()) {
            parts.add("жанры=" + String.join(",", intent.genres()));
        }
        return parts.isEmpty() ? "" : ": " + String.join(", ", parts);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
