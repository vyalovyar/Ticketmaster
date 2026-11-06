package com.ticket.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.ai.infrastructure.AiUnavailableException;
import com.ticket.ai.infrastructure.LlmClient;
import com.ticket.ai.infrastructure.LlmResult;
import com.ticket.ai.infrastructure.dto.CatalogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);
    private static final int MAX_CARDS = 5;
    private static final int FALLBACK_CARDS = 3;

    private static final String LIMIT_MESSAGE =
            "Дневной лимит AI-запросов исчерпан. Вот события, которые подошли под запрос:";
    private static final String UNAVAILABLE_MESSAGE =
            "AI-ассистент временно недоступен, но вот подходящие события из каталога:";
    private static final String FOUND_MESSAGE = "Вот события, которые подошли под запрос.";

    private final EventRetriever retriever;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final AiUsageService usageService;
    private final AiAnswerCache answerCache;

    public AiChatService(
            EventRetriever retriever,
            PromptBuilder promptBuilder,
            LlmClient llmClient,
            ObjectMapper objectMapper,
            AiUsageService usageService,
            AiAnswerCache answerCache) {
        this.retriever = retriever;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.usageService = usageService;
        this.answerCache = answerCache;
    }

    public ChatResponse chat(String message, String userId) {
        long started = System.currentTimeMillis();

        Optional<ChatResponse> cached = answerCache.get(message);
        if (cached.isPresent()) {
            usageService.recordCacheHit();
            log.info("AI chat: user={} mode=cache durationMs={}", userId, elapsed(started));
            return cached.get();
        }

        SearchResult search = retriever.retrieve(message);

        if (search.isEmpty()) {
            String reason = search.emptyReason()
                    .orElse("Подходящих событий не нашёл. Попробуй уточнить город, дату или жанр.");
            log.info("AI chat: user={} mode=no_results durationMs={} indexed={} matched={} semanticSkipped={}",
                    userId, elapsed(started), search.diagnostics().indexed(),
                    search.diagnostics().matchingFilters(), search.diagnostics().semanticRequested());
            return new ChatResponse(reason, List.of(), "no_results");
        }

        List<CatalogEvent> retrieved = search.events();

        AiUsageService.QuotaDecision quota = usageService.consumeLlmQuota(userId);
        if (!quota.allowed()) {
            usageService.recordFallback();
            log.info("AI chat: user={} mode=fallback reason={} durationMs={}",
                    userId, quota.reason(), elapsed(started));
            return fallback(retrieved, quotaMessage(quota));
        }

        String systemPrompt = promptBuilder.systemPrompt();
        String userPrompt = promptBuilder.userPrompt(message, retrieved);

        try {
            LlmResult llmResult = llmClient.complete(systemPrompt, userPrompt);
            String rawAnswer = llmResult.content();
            long neurons = llmResult.neurons() > 0
                    ? llmResult.neurons()
                    : usageService.estimateCloudflareNeurons(systemPrompt + userPrompt, rawAnswer);
            usageService.recordLlmCall(neurons);

            ChatResponse response = buildResponse(rawAnswer, retrieved);
            answerCache.put(message, response);

            log.info("AI chat: user={} mode=llm durationMs={} estimatedNeurons={}",
                    userId, elapsed(started), neurons);
            return response;
        } catch (AiUnavailableException e) {
            usageService.recordError();
            usageService.recordFallback();
            log.warn("AI chat: user={} mode=fallback reason=unavailable durationMs={} error={}",
                    userId, elapsed(started), e.getMessage());
            return fallback(retrieved, UNAVAILABLE_MESSAGE);
        }
    }

    private ChatResponse buildResponse(String rawAnswer, List<CatalogEvent> retrieved) {
        LlmAnswer parsed = parse(rawAnswer);

        Map<String, CatalogEvent> byId = new LinkedHashMap<>();
        for (CatalogEvent event : retrieved) {
            byId.put(event.id().toString(), event);
        }

        List<EventCard> cards = new ArrayList<>();
        if (parsed.eventIds() != null) {
            for (String id : parsed.eventIds()) {
                CatalogEvent event = byId.get(id);
                if (event == null || cards.stream().anyMatch(card -> card.id().equals(event.id()))) {
                    continue;
                }
                cards.add(EventCard.from(event));
                if (cards.size() >= MAX_CARDS) {
                    break;
                }
            }
        }

        boolean modelSaysNoResults = looksLikeNoResults(parsed.answer());

        if (modelSaysNoResults || cards.isEmpty()) {
            cards = retrieved.stream()
                    .limit(Math.min(FALLBACK_CARDS, retrieved.size()))
                    .map(EventCard::from)
                    .toList();
        }

        String answer;
        if (modelSaysNoResults) {
            answer = FOUND_MESSAGE;
        } else if (parsed.answer() == null || parsed.answer().isBlank()) {
            answer = FOUND_MESSAGE;
        } else {
            answer = parsed.answer().trim();
        }

        return new ChatResponse(answer, cards, "llm");
    }

    private boolean looksLikeNoResults(String answer) {
        if (answer == null) {
            return false;
        }
        String lower = answer.toLowerCase(Locale.ROOT);
        return lower.contains("не наш") || lower.contains("нет подходящ")
                || lower.contains("не удалось найти") || lower.contains("no suitable")
                || lower.contains("not found") || lower.contains("couldn't find");
    }

    private String quotaMessage(AiUsageService.QuotaDecision quota) {
        return switch (quota.reason()) {
            case USER_LIMIT -> "Ваш дневной лимит AI-запросов исчерпан ("
                    + Math.min(quota.userUsed(), quota.userLimit()) + "/" + quota.userLimit()
                    + "). Общий лимит сервиса: " + quota.globalUsed() + "/" + quota.globalLimit()
                    + ". Лимит обновится в 00:00 UTC. Вот события, которые подошли под запрос:";
            case GLOBAL_LIMIT -> "Общий дневной лимит AI-ассистента исчерпан ("
                    + quota.globalLimit() + " запросов). Лимит обновится в 00:00 UTC. "
                    + "Вот события, которые подошли под запрос:";
            case STORAGE_UNAVAILABLE -> "AI-ассистент временно недоступен из-за ограничения запросов. "
                    + "Вот события, которые подошли под запрос:";
            default -> LIMIT_MESSAGE;
        };
    }

    private ChatResponse fallback(List<CatalogEvent> retrieved, String message) {
        List<EventCard> cards = retrieved.stream()
                .limit(Math.min(FALLBACK_CARDS, retrieved.size()))
                .map(EventCard::from)
                .toList();
        return new ChatResponse(message, cards, "fallback");
    }

    private LlmAnswer parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new LlmAnswer("", List.of());
        }

        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline >= 0) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
        }

        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }

        try {
            LlmAnswer answer = objectMapper.readValue(cleaned, LlmAnswer.class);
            return answer == null ? new LlmAnswer("", List.of()) : answer;
        } catch (JsonProcessingException e) {
            return new LlmAnswer(cleaned, List.of());
        }
    }

    private long elapsed(long started) {
        return System.currentTimeMillis() - started;
    }

    public record LlmAnswer(String answer, List<String> eventIds) {
    }
}
