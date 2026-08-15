package com.jobtracker.service;

import com.jobtracker.repository.PasswordResetTokenRepository;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.security.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupService.class);

    /** Matches the forgot-password limiter's window; anything older can't affect a decision. */
    private static final long RATE_LIMIT_WINDOW_SECONDS = 900;

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RateLimiter rateLimiter;

    @Value("${app.cleanup.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${app.cleanup.cron:0 30 3 * * *}")
    @Transactional
    public void purgeExpiredTokens() {
        if (!enabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        long refreshDeleted = refreshTokenRepository.deleteByExpiresAtBefore(now);
        long resetDeleted = passwordResetTokenRepository.deleteByExpiresAtBefore(now);
        // Not a token, but the same "unbounded in-memory growth" problem, and the same schedule.
        int limiterKeysEvicted = rateLimiter.evictExpired(RATE_LIMIT_WINDOW_SECONDS);

        if (refreshDeleted > 0 || resetDeleted > 0 || limiterKeysEvicted > 0) {
            log.info("Token cleanup: {} refresh, {} reset, {} rate-limit keys",
                    refreshDeleted, resetDeleted, limiterKeysEvicted);
        }
    }
}
