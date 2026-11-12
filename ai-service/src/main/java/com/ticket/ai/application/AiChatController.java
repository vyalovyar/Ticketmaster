package com.ticket.ai.application;

import com.ticket.ai.config.AiProperties;
import com.ticket.ai.config.EmbeddingProperties;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    private final AiChatService aiChatService;
    private final EventRetriever eventRetriever;
    private final AiUsageService usageService;
    private final AiProperties properties;
    private final EmbeddingProperties embeddingProperties;

    public AiChatController(
            AiChatService aiChatService,
            EventRetriever eventRetriever,
            AiUsageService usageService,
            AiProperties properties,
            EmbeddingProperties embeddingProperties) {
        this.aiChatService = aiChatService;
        this.eventRetriever = eventRetriever;
        this.usageService = usageService;
        this.properties = properties;
        this.embeddingProperties = embeddingProperties;
    }

    @PostMapping("/chat")
    public ChatResponse chat(
            @RequestHeader(value = HEADER_USER_ID, required = false) String userId,
            @Valid @RequestBody ChatRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Требуется авторизация");
        }
        return aiChatService.chat(request.message(), userId);
    }

    @GetMapping("/search")
    public List<EventCard> search(@RequestParam("q") String query) {
        return eventRetriever.retrieveEvents(query).stream().map(EventCard::from).toList();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(
            @RequestHeader(value = HEADER_USER_ID, required = false) String userId,
            @RequestHeader(value = HEADER_USER_ROLE, required = false) String role) {
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Только администратор");
        }
        return usageService.stats(userId == null ? "anonymous" : userId);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "ok");
        status.put("configured", properties.isConfigured());
        status.put("embeddingEnabled", embeddingProperties.isEnabled());
        return status;
    }

    @GetMapping("/diagnostics")
    public Map<String, Object> diagnostics(@RequestHeader(value = HEADER_USER_ROLE, required = false) String role) {
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Только администратор");
        }
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("provider", properties.provider());
        status.put("model", properties.resolvedModel());
        status.put("intentModel", properties.resolvedIntentModel());
        status.put("llmBaseUrl", properties.baseUrl());
        status.put("configured", properties.isConfigured());
        status.put("projectConfigured", properties.hasProjectId());
        status.put("apiKeyConfigured", properties.hasApiKey());
        status.put("dailyGlobalLimit", properties.dailyGlobalLimit());
        status.put("dailyUserLimit", properties.dailyUserLimit());
        status.put("cacheTtlMinutes", properties.cacheTtlMinutes());
        status.put("topK", properties.topK());
        status.put("embeddingEnabled", embeddingProperties.isEnabled());
        status.put("embeddingModel", embeddingProperties.model());
        status.put("embeddingDimension", embeddingProperties.dimension());
        status.put("embeddingWeight", embeddingProperties.vectorWeight());
        return status;
    }
}
