package org.vinod.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${ai.rate-limit.requests-per-minute:60}")
    private int requestsPerMinute;

    private final ConcurrentHashMap<String, Deque<Long>> requestWindows = new ConcurrentHashMap<>();

    public RateLimitService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(String userId, String operation) {
        String key = "rate_limit:" + userId + ":" + operation;
        long now = Instant.now().getEpochSecond();
        long windowStart = now - 60;

        Deque<Long> timestamps = requestWindows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            boolean allowed = timestamps.size() < requestsPerMinute;
            if (allowed) {
                timestamps.addLast(now);
            }
            if (!allowed) {
                log.warn("Rate limit exceeded for user: {}, operation: {}", userId, operation);
            }
            return allowed;
        }
    }

    public long getRemainingTokens(String userId, String operation) {
        String key = "rate_limit:" + userId + ":" + operation;
        long now = Instant.now().getEpochSecond();
        long windowStart = now - 60;
        Deque<Long> timestamps = requestWindows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            return Math.max(0, requestsPerMinute - timestamps.size());
        }
    }
}

