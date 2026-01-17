package com.example.hrms.admin.repo;

import com.example.hrms.admin.domain.RegistrationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RegistrationAttemptRepository extends JpaRepository<RegistrationAttempt, Long> {

    // Count attempts from IP in time window
    long countByIpAddressAndAttemptTimeAfter(String ipAddress, LocalDateTime since);

    // Count attempts with email in time window
    long countByEmailAndAttemptTimeAfter(String email, LocalDateTime since);

    // Find attempts by IP
    List<RegistrationAttempt> findByIpAddressOrderByAttemptTimeDesc(String ipAddress);

    // Find blocked attempts
    List<RegistrationAttempt> findByIsBlockedTrue();

    // Find recent attempts
    @Query("SELECT r FROM RegistrationAttempt r WHERE r.attemptTime >= :since ORDER BY r.attemptTime DESC")
    List<RegistrationAttempt> findRecentAttempts(@Param("since") LocalDateTime since);

    // Statistics
    @Query("SELECT COUNT(r) FROM RegistrationAttempt r WHERE r.attemptTime >= :since")
    long countAttemptsSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(r) FROM RegistrationAttempt r WHERE r.isSuccessful = true AND r.attemptTime >= :since")
    long countSuccessfulAttemptsSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(r) FROM RegistrationAttempt r WHERE r.isBlocked = true AND r.attemptTime >= :since")
    long countBlockedAttemptsSince(@Param("since") LocalDateTime since);
}
