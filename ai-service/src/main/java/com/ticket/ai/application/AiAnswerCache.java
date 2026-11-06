package com.ticket.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.ai.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;


@Service
public class AiAnswerCache {

    private static final Logger log = LoggerFactory.getLogger(AiAnswerCache.class);
    private static final String CACHE_VERSION = "v3";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;

    public AiAnswerCache(StringRedisTemplate redis, ObjectMapper objectMapper, AiProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Optional<ChatResponse> get(String message) {
        if (properties.cacheTtlMinutes() <= 0) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(key(message));
            if (json == null) {
                return Optional.empty();
            }
            ChatResponse cached = objectMapper.readValue(json, ChatResponse.class);
            return Optional.of(new ChatResponse(cached.answer(), cached.events(), "cache"));
        } catch (Exception e) {
            log.warn("Could not read AI cache: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String message, ChatResponse response) {
        if (properties.cacheTtlMinutes() <= 0 || !"llm".equals(response.mode())) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(response);
            redis.opsForValue().set(
                    key(message),
                    json,
                    Duration.ofMinutes(properties.cacheTtlMinutes()));
        } catch (Exception e) {
            log.warn("Could not write AI cache: {}", e.getMessage());
        }
    }

    private String key(String message) {
        String normalized = message.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return "ai:answer:" + CACHE_VERSION + ":" + sha256(normalized);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
