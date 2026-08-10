package com.jobtracker.repository;

import com.jobtracker.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Integer> {

    Optional<PasswordResetToken> findByToken(String token);

    /** Backs the resend cooldown: is there already a live, unused reset token for this user? */
    boolean existsByUser_UserIdAndUsedFalseAndCreatedAtAfter(Integer userId, LocalDateTime after);

    /** Cleanup job. Derived deleteBy — the CALLER must be @Transactional. */
    long deleteByExpiresAtBefore(LocalDateTime time);
}
