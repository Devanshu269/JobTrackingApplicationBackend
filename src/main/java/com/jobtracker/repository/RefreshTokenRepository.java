package com.jobtracker.repository;

import com.jobtracker.model.RefreshToken;
import com.jobtracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
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
}