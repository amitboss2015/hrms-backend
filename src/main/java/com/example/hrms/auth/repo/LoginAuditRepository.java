package com.example.hrms.auth.repo;

import com.example.hrms.auth.domain.LoginAudit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoginAuditRepository extends JpaRepository<LoginAudit, Long> {

    /**
     * Find recent audits for a user
     */
    List<LoginAudit> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Find audits for a tenant
     */
    List<LoginAudit> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    /**
     * Count failed logins for email in time window (for rate limiting)
     */
    @Query("SELECT COUNT(a) FROM LoginAudit a WHERE a.email = :email AND a.action = 'LOGIN_FAILED' AND a.createdAt > :since")
    long countFailedLoginsSince(@Param("email") String email, @Param("since") LocalDateTime since);

    /**
     * Count failed logins from IP in time window
     */
    @Query("SELECT COUNT(a) FROM LoginAudit a WHERE a.ipAddress = :ip AND a.action = 'LOGIN_FAILED' AND a.createdAt > :since")
    long countFailedLoginsByIpSince(@Param("ip") String ip, @Param("since") LocalDateTime since);

    /**
     * Find suspicious activity - multiple failed logins
     */
    @Query("SELECT a FROM LoginAudit a WHERE a.action = 'LOGIN_FAILED' AND a.createdAt > :since ORDER BY a.createdAt DESC")
    List<LoginAudit> findRecentFailures(@Param("since") LocalDateTime since, Pageable pageable);
}
