package com.clip2house.ledger.auth;

import com.clip2house.ledger.config.GameProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

    private final GameProperties props;

    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(2))
            .maximumSize(200_000)
            .build();

    private final Cache<Long, Instant> frictionUntil = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(2))
            .maximumSize(50_000)
            .build();

    public RateLimiter(GameProperties props) {
        this.props = props;
    }

    public boolean allowGuestCreation(String ip) {
        return tryAcquire("guest:" + ip, Duration.ofHours(1), props.rateLimit().guestPerIpPerHour());
    }

    public boolean allowRequest(String ip) {
        return tryAcquire("req:" + ip, Duration.ofMinutes(1), props.rateLimit().requestsPerIpPerMinute());
    }

    public boolean allowMessage(Long playerId) {
        Instant friction = frictionUntil.getIfPresent(playerId);
        int limit = (friction != null && friction.isAfter(Instant.now()))
                ? props.rateLimit().frictionMessagesPerMinute()
                : props.rateLimit().messagesPerPlayerPerMinute();
        return tryAcquire("msg:" + playerId, Duration.ofMinutes(1), limit);
    }

    public void recordClassifierHit(Long playerId) {
        Duration window = props.rateLimit().frictionWindow();
        String key = "hit:" + playerId + ":" + (Instant.now().toEpochMilli() / window.toMillis());
        AtomicInteger count = counters.get(key, k -> new AtomicInteger());
        if (count.incrementAndGet() >= props.rateLimit().frictionThreshold()) {
            frictionUntil.put(playerId, Instant.now().plus(window));
        }
    }

    private boolean tryAcquire(String scope, Duration window, int limit) {
        String key = scope + ":" + (Instant.now().toEpochMilli() / window.toMillis());
        AtomicInteger count = counters.get(key, k -> new AtomicInteger());
        return count.incrementAndGet() <= limit;
    }
}
