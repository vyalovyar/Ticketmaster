package com.ticket.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.ai.config.AiProperties;
import com.ticket.ai.infrastructure.LlmClient;
import com.ticket.ai.infrastructure.LlmResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class IntentParser {

    private static final Logger log = LoggerFactory.getLogger(IntentParser.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String CACHE_PREFIX = "ai:intent:v3:";
    private static final int MAX_KEYWORDS = 200;
    private static final int MAX_GENRES = 10;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final AiUsageService usageService;
    private final AiProperties properties;
    private final DateScopeParser dateScopeParser;

    public IntentParser(
            LlmClient llmClient,
            ObjectMapper objectMapper,
            StringRedisTemplate redis,
            AiUsageService usageService,
            AiProperties properties,
            DateScopeParser dateScopeParser) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.usageService = usageService;
        this.properties = properties;
        this.dateScopeParser = dateScopeParser;
    }

    public QueryIntent parse(String query) {
        Optional<QueryIntent> cached = readCache(query);
        if (cached.isPresent()) {
            return cached.get();
        }
        QueryIntent parsed = parseWithLlm(query);
        writeCache(query, parsed);
        return parsed;
    }

    private QueryIntent parseWithLlm(String query) {
        if (!properties.isConfigured()) {
            return fallback(query);
        }

        String system = """
                Ты — парсер поисковых запросов сервиса продажи билетов.
                Сегодня %s.
                Верни строго JSON без markdown:
                {"countryCode":null,"city":null,"dateFrom":null,"dateTo":null,"genres":[],"keywords":""}

                Правила:
                - countryCode — ISO-3166 alpha-2 (DE, FR, US) или null.
                - city — английское название города (Berlin, Paris, New York) или null.
                - dateFrom/dateTo — ISO yyyy-MM-dd или null. Относительные даты ("в эту пятницу", "завтра") преобразуй в конкретные.
                - genres — английские жанры (rock, jazz, classical, pop) или [].
                - keywords — оставшиеся значимые слова без города, даты и жанра.
                - Не придумывай данные, которых нет в запросе.
                """.formatted(LocalDate.now());

        try {
            LlmResult result = llmClient.complete(
                    properties.resolvedIntentModel(), system, "Запрос: " + query, 250);
            usageService.recordIntentCall(result.neurons());

            IntentJson json = objectMapper.readValue(stripJson(result.content()), IntentJson.class);
            return toIntent(json, query);
        } catch (Exception e) {
            log.warn("Intent parsing failed, using keyword fallback: {}", e.getMessage());
            return fallback(query);
        }
    }

    private QueryIntent toIntent(IntentJson json, String originalQuery) {
        String countryCode = null;
        if (json.countryCode() != null && json.countryCode().trim().matches("[A-Za-z]{2}")) {
            countryCode = json.countryCode().trim().toUpperCase(Locale.ROOT);
        }

        String city = normalizeText(json.city(), 120);

        LocalDate dateFrom = parseDate(json.dateFrom());
        LocalDate dateTo = parseDate(json.dateTo());

        Optional<DateScopeParser.DateRange> relativeDate = dateScopeParser.parse(originalQuery);
        if (relativeDate.isPresent()) {
            dateFrom = relativeDate.get().from();
            dateTo = relativeDate.get().to();
        }
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            LocalDate swap = dateFrom;
            dateFrom = dateTo;
            dateTo = swap;
        }

        List<String> genres = json.genres() == null ? List.of() : json.genres().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.equals("undefined") && !value.equals("other"))
                .distinct()
                .limit(MAX_GENRES)
                .toList();

        String keywords = normalizeText(json.keywords(), MAX_KEYWORDS);
        return new QueryIntent(countryCode, city, dateFrom, dateTo, genres,
                keywords == null ? "" : keywords);
    }

    private QueryIntent fallback(String query) {
        String keywords = query == null ? "" : query.trim();
        if (keywords.length() > MAX_KEYWORDS) {
            keywords = keywords.substring(0, MAX_KEYWORDS);
        }
        return new QueryIntent(null, null, null, null, List.of(), keywords);
    }

    private String stripJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        String cleaned = raw.trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        return start >= 0 && end > start ? cleaned.substring(start, end + 1) : "{}";
    }

    private String normalizeText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength).trim();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Optional<QueryIntent> readCache(String query) {
        try {
            String json = redis.opsForValue().get(cacheKey(query));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, QueryIntent.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void writeCache(String query, QueryIntent intent) {
        try {
            redis.opsForValue().set(cacheKey(query), objectMapper.writeValueAsString(intent), CACHE_TTL);
        } catch (Exception e) {
            log.warn("Could not cache query intent: {}", e.getMessage());
        }
    }

    private String cacheKey(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        String hash;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            hash = HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            hash = Integer.toHexString(normalized.hashCode());
        }
        return CACHE_PREFIX + LocalDate.now() + ":" + hash;
    }

    public record IntentJson(
            String countryCode,
            String city,
            String dateFrom,
            String dateTo,
            List<String> genres,
            String keywords
    ) {
    }
}
