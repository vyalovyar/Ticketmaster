package com.ticket.ai.application;

import com.ticket.ai.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;


@Service
public class AiUsageService {

    private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);
    private static final Duration COUNTER_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redis;
    private final AiProperties properties;

    public AiUsageService(StringRedisTemplate redis, AiProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public QuotaDecision consumeLlmQuota(String userId) {
        String date = today();
        int userLimit = properties.dailyUserLimit();
        int globalLimit = properties.dailyGlobalLimit();

        try {
            long global = increment("ai:usage:global:" + date);
            long user = increment("ai:usage:user:" + userId + ":" + date);

            if (global > globalLimit) {
                log.warn("AI global quota exceeded: user={} globalCount={}/{}", userId, global, globalLimit);
                return QuotaDecision.globalLimit(user, userLimit, global, globalLimit);
            }
            if (user > userLimit) {
                log.warn("AI user quota exceeded: user={} userCount={}/{}", userId, user, userLimit);
                return QuotaDecision.userLimit(user, userLimit, global, globalLimit);
            }
            return QuotaDecision.allowed(user, userLimit, global, globalLimit);
        } catch (RuntimeException e) {
            log.error("Redis unavailable, blocking LLM call to avoid unexpected charges", e);
            return QuotaDecision.storageUnavailable(userLimit, globalLimit);
        }
    }

    public record QuotaDecision(
            boolean allowed,
            Reason reason,
            long userUsed,
            int userLimit,
            long globalUsed,
            int globalLimit
    ) {
        public enum Reason { ALLOWED, USER_LIMIT, GLOBAL_LIMIT, STORAGE_UNAVAILABLE }

        public static QuotaDecision allowed(long userUsed, int userLimit, long globalUsed, int globalLimit) {
            return new QuotaDecision(true, Reason.ALLOWED, userUsed, userLimit, globalUsed, globalLimit);
        }

        public static QuotaDecision userLimit(long userUsed, int userLimit, long globalUsed, int globalLimit) {
            return new QuotaDecision(false, Reason.USER_LIMIT, userUsed, userLimit, globalUsed, globalLimit);
        }

        public static QuotaDecision globalLimit(long userUsed, int userLimit, long globalUsed, int globalLimit) {
            return new QuotaDecision(false, Reason.GLOBAL_LIMIT, userUsed, userLimit, globalUsed, globalLimit);
        }

        public static QuotaDecision storageUnavailable(int userLimit, int globalLimit) {
            return new QuotaDecision(false, Reason.STORAGE_UNAVAILABLE, 0, userLimit, 0, globalLimit);
        }
    }

    public void recordLlmCall(long estimatedNeurons) {
        incrementQuietly("ai:llm:" + today(), 1);
        if (estimatedNeurons > 0) {
            incrementQuietly("ai:neurons:" + today(), estimatedNeurons);
        }
    }

    public void recordCacheHit() {
        incrementQuietly("ai:cache-hits:" + today(), 1);
    }

    public void recordFallback() {
        incrementQuietly("ai:fallbacks:" + today(), 1);
    }

    public void recordError() {
        incrementQuietly("ai:errors:" + today(), 1);
    }

    public void recordEmbeddingCall(long estimatedNeurons) {
        incrementQuietly("ai:embeddings:" + today(), 1);
        if (estimatedNeurons > 0) {
            incrementQuietly("ai:embedding-neurons:" + today(), estimatedNeurons);
        }
    }

    public void recordIntentCall(long estimatedNeurons) {
        incrementQuietly("ai:intents:" + today(), 1);
        if (estimatedNeurons > 0) {
            incrementQuietly("ai:intent-neurons:" + today(), estimatedNeurons);
        }
    }

    public long estimateEmbeddingNeurons(int chars) {
        double tokens = chars / 4.0;
        return Math.round(tokens / 1_000_000.0 * 1_075);
    }

    public long estimateCloudflareNeurons(String prompt, String answer) {
        if (!"cloudflare".equals(properties.provider())) {
            return 0;
        }
        double inputTokens = prompt.length() / 4.0;
        double outputTokens = answer.length() / 4.0;
        double neurons = inputTokens / 1_000_000.0 * 26_668 + outputTokens / 1_000_000.0 * 204_805;
        return Math.round(neurons);
    }

    public Map<String, Object> stats(String userId) {
        String date = today();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dateUtc", date);
        result.put("provider", properties.provider());
        result.put("model", properties.resolvedModel());
        result.put("globalLimit", properties.dailyGlobalLimit());
        result.put("userLimit", properties.dailyUserLimit());
        result.put("globalUsed", read("ai:usage:global:" + date));
        result.put("userUsed", read("ai:usage:user:" + userId + ":" + date));
        result.put("llmCalls", read("ai:llm:" + date));
        result.put("cacheHits", read("ai:cache-hits:" + date));
        result.put("fallbacks", read("ai:fallbacks:" + date));
        result.put("errors", read("ai:errors:" + date));
        result.put("estimatedNeurons", read("ai:neurons:" + date));
        result.put("embeddingCalls", read("ai:embeddings:" + date));
        result.put("embeddingNeurons", read("ai:embedding-neurons:" + date));
        result.put("intentCalls", read("ai:intents:" + date));
        result.put("intentNeurons", read("ai:intent-neurons:" + date));
        return result;
    }

    private long increment(String key) {
        Long value = redis.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redis.expire(key, COUNTER_TTL);
        }
        return value == null ? 0 : value;
    }

    private void incrementQuietly(String key, long delta) {
        try {
            Long value = redis.opsForValue().increment(key, delta);
            if (value != null && value == delta) {
                redis.expire(key, COUNTER_TTL);
            }
        } catch (RuntimeException e) {
            log.warn("Could not update AI counter {}: {}", key, e.getMessage());
        }
    }

    private long read(String key) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? 0 : Long.parseLong(value);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }
}
