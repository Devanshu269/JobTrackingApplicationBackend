package com.jobtracker.repository;

import com.jobtracker.model.RefreshToken;
import com.jobtracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Integer> {
    Optional<RefreshToken> findByToken(String token);
    List<RefreshToken> findByUser(User user);
    void deleteByToken(String token);
    void deleteByUser(User user);
    long deleteByExpiresAtBefore(LocalDateTime time);

    /**
     * Kills an entire login session in one statement — used when a replayed token indicates the
     * chain may be compromised, and when the absolute session cap is reached.
     *
     * <p>A bulk update rather than load-and-save: the caller doesn't need the entities, and this
     * stays one statement regardless of how many rotations the session has been through.
     * {@code @Modifying} requires the CALLER to be {@code @Transactional}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.familyId = :familyId AND r.revoked = false")
    int revokeFamily(@Param("familyId") String familyId);
}