package com.ticket.service;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Service
public class RateLimitService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private static final long LIMIT = 10;
    private static final Duration WINDOW = Duration.ofSeconds(1);

    public RateLimitService(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> isAllowed(String clientId) {
        long window = Instant.now().getEpochSecond();
        String key = "rate:" + clientId + ":" + window;

        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count != null && count == 1L) {
                        return redisTemplate.expire(key, WINDOW).thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .map(count -> count != null && count <= LIMIT);
    }
}