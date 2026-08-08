package com.jobtracker.security;

import com.jobtracker.dto.AuthResponseDto;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OAuthExchangeCodeStore {

    private static final long CODE_TTL_SECONDS = 60;

    private final Map<String, Entry> codes = new ConcurrentHashMap<>();

    public String store(AuthResponseDto authResponseDto) {
        cleanupExpired();
        String code = UUID.randomUUID().toString();
        codes.put(code, new Entry(authResponseDto, Instant.now().plusSeconds(CODE_TTL_SECONDS)));
        return code;
    }

    public Optional<AuthResponseDto> consume(String code) {
        Entry entry = codes.remove(code);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(entry.authResponseDto());
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        codes.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }

    private record Entry(AuthResponseDto authResponseDto, Instant expiresAt) {}
}