package com.jobtracker.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiter {

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int maxRequests, long windowSeconds) {
        Instant cutoff = Instant.now().minusSeconds(windowSeconds);

        // computeIfAbsent + synchronized on the deque: the map is concurrent, but a single
        // key's deque is not, and two requests from the same client can race.
        Deque<Instant> timestamps = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                return false;
            }
            timestamps.addLast(Instant.now());
            return true;
        }
    }

    public int evictExpired(long windowSeconds) {
        Instant cutoff = Instant.now().minusSeconds(windowSeconds);
        int before = hits.size();
        hits.entrySet().removeIf(entry -> {
            Deque<Instant> timestamps = entry.getValue();
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                    timestamps.pollFirst();
                }
                return timestamps.isEmpty();
            }
        });
        return before - hits.size();
    }
}
