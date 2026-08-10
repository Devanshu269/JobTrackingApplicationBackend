package com.jobtracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Table(name = "refresh_tokens")
@Entity
@Getter
@Setter
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id", nullable = false)
    private Integer refreshTokenId;

    @Column(name = "token", nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "device_info", nullable = false)
    private String deviceInfo;

    @Column(name = "revoked", nullable = false, columnDefinition = "BOOL")
    private Boolean revoked = false;

    /**
     * Groups every token in one rotation chain — i.e. one login session. Carried forward on each
     * rotation so that detecting a replayed token can kill the whole session, not just that row.
     */
    @Column(name = "family_id", nullable = false, length = 64)
    private String familyId;

    /**
     * When the *original* login happened, copied unchanged through every rotation. Backs the
     * absolute session cap: sliding expiry alone would let a continuously-used session live
     * forever.
     */
    @Column(name = "family_created_at", nullable = false)
    private LocalDateTime familyCreatedAt;

    /**
     * When this token was consumed by a rotation. Null while current.
     *
     * <p>Also drives the race grace period: a token replayed within seconds of being rotated is
     * far more likely to be two browser tabs refreshing at once than an attacker, so it fails
     * that one call without nuking the session.
     */
    @Column(name = "rotated_at")
    private LocalDateTime rotatedAt;
}