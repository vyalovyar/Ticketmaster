package com.ticket.ai.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class DateScopeParserTest {

    private final DateScopeParser parser = new DateScopeParser(
            Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneId.of("Europe/Moscow")));

    @Test
    void parsesNextFriday() {
        DateScopeParser.DateRange range = parser.parse("хочу сходить на концерт в эту пятницу").orElseThrow();

        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @Test
    void parsesWeekend() {
        DateScopeParser.DateRange range = parser.parse("что есть на выходные").orElseThrow();

        assertThat(range.from().getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(range.to().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
    }

    @Test
    void returnsEmptyForQueryWithoutDate() {
        assertThat(parser.parse("джаз в Берлине")).isEmpty();
    }
}
