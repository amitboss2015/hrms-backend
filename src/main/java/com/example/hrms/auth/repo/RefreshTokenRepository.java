package com.example.hrms.auth.repo;

import com.example.hrms.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Find token by hash
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Find valid token by hash (not revoked, not expired)
     */
    @Query("SELECT t FROM RefreshToken t WHERE t.tokenHash = :hash AND t.revoked = false AND t.expiresAt > :now")
    Optional<RefreshToken> findValidByTokenHash(@Param("hash") String hash, @Param("now") LocalDateTime now);

    /**
     * Find all tokens for a user
     */
    List<RefreshToken> findByUserId(Long userId);

    /**
     * Find active tokens for a user
     */
    @Query("SELECT t FROM RefreshToken t WHERE t.userId = :userId AND t.revoked = false AND t.expiresAt > :now")
    List<RefreshToken> findActiveByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * Revoke all tokens for a user
     */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true, t.revokedAt = :now WHERE t.userId = :userId AND t.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * Delete expired tokens (cleanup job)
     */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Count active sessions for a user
     */
    @Query("SELECT COUNT(t) FROM RefreshToken t WHERE t.userId = :userId AND t.revoked = false AND t.expiresAt > :now")
    long countActiveSessions(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
