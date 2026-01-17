package com.example.hrms.admin.repo;

import com.example.hrms.admin.domain.TrialTracking;
import com.example.hrms.admin.domain.TrialTracking.TrialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrialTrackingRepository extends JpaRepository<TrialTracking, Long> {

    Optional<TrialTracking> findByTenantId(String tenantId);

    Optional<TrialTracking> findByAdminEmail(String email);

    List<TrialTracking> findByTrialStatus(TrialStatus status);

    List<TrialTracking> findByTrialStatusIn(List<TrialStatus> statuses);

    // Find trials expiring soon
    @Query("SELECT t FROM TrialTracking t WHERE t.trialEndDate BETWEEN :startDate AND :endDate AND t.trialStatus = 'ACTIVE'")
    List<TrialTracking> findTrialsExpiringSoon(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Find expired but not yet marked as expired
    @Query("SELECT t FROM TrialTracking t WHERE t.trialEndDate < :today AND t.trialStatus = 'ACTIVE'")
    List<TrialTracking> findExpiredActiveTrials(@Param("today") LocalDate today);

    // Find high-risk registrations
    List<TrialTracking> findByFraudScoreGreaterThanEqual(Integer score);

    // Find by IP address
    List<TrialTracking> findByIpAddress(String ipAddress);

    // Count registrations from same IP
    long countByIpAddress(String ipAddress);

    // Find suspended accounts
    List<TrialTracking> findByIsSuspendedTrue();

    // Find recent registrations
    @Query("SELECT t FROM TrialTracking t WHERE t.createdAt >= :since ORDER BY t.createdAt DESC")
    List<TrialTracking> findRecentRegistrations(@Param("since") LocalDateTime since);

    // Statistics queries
    @Query("SELECT COUNT(t) FROM TrialTracking t WHERE t.trialStatus = :status")
    long countByStatus(@Param("status") TrialStatus status);

    @Query("SELECT COUNT(t) FROM TrialTracking t WHERE t.createdAt >= :since")
    long countRegistrationsSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(t) FROM TrialTracking t WHERE t.isEmailVerified = true")
    long countVerifiedEmails();

    @Query("SELECT AVG(t.fraudScore) FROM TrialTracking t")
    Double getAverageFraudScore();

    // Find by phone for duplicate detection
    List<TrialTracking> findByAdminPhone(String phone);
}
