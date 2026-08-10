package com.jobtracker.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limiter.
 *
 * <p>Deliberately simple and process-local — it protects a single instance against a single
 * abusive client, which is the actual risk here (someone hammering /forgot-password to spam an
 * inbox or burn the Gmail send quota). It is <b>not</b> a distributed limiter: running two
 * instances doubles the effective allowance. Swap for Redis/Bucket4j if this ever scales out.
 */
@Component
public class RateLimiter {

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /**
     * @return true if the call is allowed, false if the key has exhausted its window
     */
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

    /**
     * Drops keys whose entire window has expired. Without this the map grows once per distinct
     * client forever; called on a schedule rather than per-request to keep the hot path cheap.
     */
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
