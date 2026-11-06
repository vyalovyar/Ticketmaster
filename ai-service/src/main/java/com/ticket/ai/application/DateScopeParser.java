package com.ticket.ai.application;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Clock;
import java.time.LocalTime;
import java.time.Month;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;


@Component
public class DateScopeParser {

    private static final Map<String, DayOfWeek> WEEKDAYS = Map.ofEntries(
            Map.entry("понедельник", DayOfWeek.MONDAY),
            Map.entry("вторник", DayOfWeek.TUESDAY),
            Map.entry("сред", DayOfWeek.WEDNESDAY),
            Map.entry("четверг", DayOfWeek.THURSDAY),
            Map.entry("пятниц", DayOfWeek.FRIDAY),
            Map.entry("суббот", DayOfWeek.SATURDAY),
            Map.entry("воскрес", DayOfWeek.SUNDAY));

    private static final Map<String, Month> MONTHS = Map.ofEntries(
            Map.entry("январ", Month.JANUARY),
            Map.entry("феврал", Month.FEBRUARY),
            Map.entry("март", Month.MARCH),
            Map.entry("апрел", Month.APRIL),
            Map.entry("май", Month.MAY),
            Map.entry("мая", Month.MAY),
            Map.entry("мае", Month.MAY),
            Map.entry("июн", Month.JUNE),
            Map.entry("июл", Month.JULY),
            Map.entry("август", Month.AUGUST),
            Map.entry("сентябр", Month.SEPTEMBER),
            Map.entry("октябр", Month.OCTOBER),
            Map.entry("ноябр", Month.NOVEMBER),
            Map.entry("декабр", Month.DECEMBER));

    private final Clock clock;

    public DateScopeParser() {
        this(Clock.systemDefaultZone());
    }

    DateScopeParser(Clock clock) {
        this.clock = clock;
    }

    public Optional<DateRange> parse(String query) {
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        LocalDate today = LocalDate.now(clock);
        List<String> words = List.of(normalized.split("\\s+"));

        if (words.contains("послезавтра")) {
            return range(today.plusDays(2), today.plusDays(2));
        }
        if (words.contains("завтра")) {
            return range(today.plusDays(1), today.plusDays(1));
        }
        if (words.contains("сегодня")) {
            return range(today, today);
        }
        if (containsAny(words, "выходные", "выходных", "выходным")) {
            if (today.getDayOfWeek() == DayOfWeek.SATURDAY) {
                return range(today, today.plusDays(1));
            }
            if (today.getDayOfWeek() == DayOfWeek.SUNDAY) {
                return range(today, today);
            }
            LocalDate saturday = today.plusDays(6 - today.getDayOfWeek().getValue());
            return range(saturday, saturday.plusDays(1));
        }
        if (containsAny(words, "неделе", "неделю")) {
            LocalDate sunday = today.getDayOfWeek() == DayOfWeek.SUNDAY
                    ? today
                    : today.plusDays(7 - today.getDayOfWeek().getValue());
            return range(today, sunday);
        }
        if (containsAny(words, "месяце", "месяц")) {
            YearMonth month = containsAny(words, "следующем", "следующий")
                    ? YearMonth.from(today).plusMonths(1)
                    : YearMonth.from(today);
            return range(month.atDay(1), month.atEndOfMonth());
        }
        if (containsAny(words, "году", "год")) {
            int year = containsAny(words, "следующем", "следующий")
                    ? today.getYear() + 1
                    : today.getYear();
            return range(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        }

        for (Map.Entry<String, DayOfWeek> entry : WEEKDAYS.entrySet()) {
            if (words.stream().anyMatch(word -> word.startsWith(entry.getKey()))) {
                int delta = (entry.getValue().getValue() - today.getDayOfWeek().getValue() + 7) % 7;
                if (delta == 0 && LocalTime.now(clock).isAfter(LocalTime.of(20, 0))) {
                    delta = 7;
                }
                if (containsAny(words, "следующ", "следующую")) {
                    delta += 7;
                }
                LocalDate date = today.plusDays(delta);
                return range(date, date);
            }
        }

        for (Map.Entry<String, Month> entry : MONTHS.entrySet()) {
            if (words.stream().anyMatch(word -> word.startsWith(entry.getKey()))) {
                YearMonth month = YearMonth.of(today.getYear(), entry.getValue());
                if (month.isBefore(YearMonth.from(today))) {
                    month = month.plusYears(1);
                }
                return range(month.atDay(1), month.atEndOfMonth());
            }
        }

        return Optional.empty();
    }

    private Optional<DateRange> range(LocalDate from, LocalDate to) {
        return Optional.of(new DateRange(from, to));
    }

    private boolean containsAny(List<String> words, String... prefixes) {
        for (String prefix : prefixes) {
            if (words.stream().anyMatch(word -> word.startsWith(prefix))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    public record DateRange(LocalDate from, LocalDate to) {
    }
}
