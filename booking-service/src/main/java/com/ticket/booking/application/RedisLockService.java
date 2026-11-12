package com.ticket.booking.application;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class RedisLockService {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryLock(String lockKey, String lockValue, Duration timeout) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, timeout);
        return Boolean.TRUE.equals(acquired);
    }

    public boolean releaseLock(String lockKey, String lockValue) {
        Long deleted = redisTemplate.execute(RELEASE_SCRIPT, List.of(lockKey), lockValue);
        return deleted != null && deleted > 0;
    }
}
