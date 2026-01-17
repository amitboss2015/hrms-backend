package com.example.hrms.admin.service;

import com.example.hrms.admin.domain.RegistrationAttempt;
import com.example.hrms.admin.domain.TrialTracking;
import com.example.hrms.admin.domain.TrialTracking.TrialStatus;
import com.example.hrms.admin.repo.RegistrationAttemptRepository;
import com.example.hrms.admin.repo.TrialTrackingRepository;
import com.example.hrms.tenant.repo.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for Admin Dashboard operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardService {

    private final TrialTrackingRepository trialRepo;
    private final RegistrationAttemptRepository attemptRepo;
    private final TenantRepository tenantRepo;

    /**
     * Get dashboard overview statistics
     */
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime last7Days = today.minusDays(7);
        LocalDateTime last30Days = today.minusDays(30);

        // Registration stats
        stats.put("totalRegistrations", trialRepo.count());
        stats.put("registrationsToday", trialRepo.countRegistrationsSince(today));
        stats.put("registrationsLast7Days", trialRepo.countRegistrationsSince(last7Days));
        stats.put("registrationsLast30Days", trialRepo.countRegistrationsSince(last30Days));

        // Trial status breakdown
        stats.put("activeTrials", trialRepo.countByStatus(TrialStatus.ACTIVE));
        stats.put("pendingActivation", trialRepo.countByStatus(TrialStatus.PENDING));
        stats.put("expiredTrials", trialRepo.countByStatus(TrialStatus.EXPIRED));
        stats.put("convertedTrials", trialRepo.countByStatus(TrialStatus.CONVERTED));
        stats.put("suspendedAccounts", trialRepo.countByStatus(TrialStatus.SUSPENDED));

        // Email verification
        stats.put("verifiedEmails", trialRepo.countVerifiedEmails());

        // Trials expiring soon
        List<TrialTracking> expiringSoon = trialRepo.findTrialsExpiringSoon(
            LocalDate.now(), LocalDate.now().plusDays(3));
        stats.put("trialsExpiringSoon", expiringSoon.size());

        // High-risk registrations
        stats.put("highRiskRegistrations", trialRepo.findByFraudScoreGreaterThanEqual(70).size());

        // Average fraud score
        Double avgScore = trialRepo.getAverageFraudScore();
        stats.put("averageFraudScore", avgScore != null ? Math.round(avgScore) : 0);

        // Registration attempts stats
        stats.put("totalAttempts", attemptRepo.count());
        stats.put("blockedAttempts", attemptRepo.countBlockedAttemptsSince(last30Days));
        
        // Conversion rate (rough estimate)
        long total = trialRepo.count();
        long converted = trialRepo.countByStatus(TrialStatus.CONVERTED);
        stats.put("conversionRate", total > 0 ? Math.round((converted * 100.0) / total) : 0);

        return stats;
    }

    /**
     * Get all trials with filters
     */
    public List<TrialTracking> getTrials(String status, String riskLevel, Boolean suspended) {
        List<TrialTracking> trials;

        if (status != null && !status.isBlank()) {
            trials = trialRepo.findByTrialStatus(TrialStatus.valueOf(status.toUpperCase()));
        } else {
            trials = trialRepo.findAll();
        }

        // Filter by risk level
        if (riskLevel != null && !riskLevel.isBlank()) {
            trials = trials.stream()
                .filter(t -> riskLevel.equalsIgnoreCase(t.getRiskLevel()))
                .collect(Collectors.toList());
        }

        // Filter by suspended
        if (suspended != null) {
            trials = trials.stream()
                .filter(t -> suspended.equals(t.getIsSuspended()))
                .collect(Collectors.toList());
        }

        return trials;
    }

    /**
     * Get trials expiring soon
     */
    public List<TrialTracking> getTrialsExpiringSoon(int days) {
        return trialRepo.findTrialsExpiringSoon(LocalDate.now(), LocalDate.now().plusDays(days));
    }

    /**
     * Get high-risk registrations
     */
    public List<TrialTracking> getHighRiskRegistrations(int minScore) {
        return trialRepo.findByFraudScoreGreaterThanEqual(minScore);
    }

    /**
     * Get recent registration attempts
     */
    public List<RegistrationAttempt> getRecentAttempts(int hours) {
        return attemptRepo.findRecentAttempts(LocalDateTime.now().minusHours(hours));
    }

    /**
     * Suspend a company's trial
     */
    @Transactional
    public TrialTracking suspendTrial(String tenantId, String reason, String suspendedBy) {
        TrialTracking trial = trialRepo.findByTenantId(tenantId)
            .orElseThrow(() -> new RuntimeException("Trial not found: " + tenantId));

        trial.setIsSuspended(true);
        trial.setSuspensionReason(reason);
        trial.setSuspendedBy(suspendedBy);
        trial.setSuspendedAt(LocalDateTime.now());
        trial.setTrialStatus(TrialStatus.SUSPENDED);

        // Also deactivate the tenant
        tenantRepo.findById(tenantId).ifPresent(tenant -> {
            tenant.setIsActive(false);
            tenantRepo.save(tenant);
        });

        log.info("Trial suspended: {} by {} - Reason: {}", tenantId, suspendedBy, reason);
        return trialRepo.save(trial);
    }

    /**
     * Reactivate a suspended trial
     */
    @Transactional
    public TrialTracking reactivateTrial(String tenantId, String reactivatedBy) {
        TrialTracking trial = trialRepo.findByTenantId(tenantId)
            .orElseThrow(() -> new RuntimeException("Trial not found: " + tenantId));

        trial.setIsSuspended(false);
        trial.setTrialStatus(TrialStatus.ACTIVE);
        trial.setNotes((trial.getNotes() != null ? trial.getNotes() + "\n" : "") + 
            "Reactivated by " + reactivatedBy + " at " + LocalDateTime.now());

        // Reactivate the tenant
        tenantRepo.findById(tenantId).ifPresent(tenant -> {
            tenant.setIsActive(true);
            tenantRepo.save(tenant);
        });

        log.info("Trial reactivated: {} by {}", tenantId, reactivatedBy);
        return trialRepo.save(trial);
    }

    /**
     * Extend a trial period
     */
    @Transactional
    public TrialTracking extendTrial(String tenantId, int additionalDays, String extendedBy) {
        TrialTracking trial = trialRepo.findByTenantId(tenantId)
            .orElseThrow(() -> new RuntimeException("Trial not found: " + tenantId));

        LocalDate newEndDate = trial.getTrialEndDate().plusDays(additionalDays);
        trial.setTrialEndDate(newEndDate);
        
        // If was expired, reactivate
        if (trial.getTrialStatus() == TrialStatus.EXPIRED) {
            trial.setTrialStatus(TrialStatus.ACTIVE);
        }
        
        trial.setNotes((trial.getNotes() != null ? trial.getNotes() + "\n" : "") + 
            "Extended by " + additionalDays + " days by " + extendedBy + " at " + LocalDateTime.now());

        log.info("Trial extended: {} by {} days (new end: {})", tenantId, additionalDays, newEndDate);
        return trialRepo.save(trial);
    }

    /**
     * Mark trial as converted (paid)
     */
    @Transactional
    public TrialTracking markAsConverted(String tenantId, String planName) {
        TrialTracking trial = trialRepo.findByTenantId(tenantId)
            .orElseThrow(() -> new RuntimeException("Trial not found: " + tenantId));

        trial.setTrialStatus(TrialStatus.CONVERTED);
        trial.setNotes((trial.getNotes() != null ? trial.getNotes() + "\n" : "") + 
            "Converted to " + planName + " at " + LocalDateTime.now());

        // Update tenant plan
        tenantRepo.findById(tenantId).ifPresent(tenant -> {
            tenant.setPlan(planName);
            tenantRepo.save(tenant);
        });

        log.info("Trial converted: {} to plan {}", tenantId, planName);
        return trialRepo.save(trial);
    }

    /**
     * Delete a registration (spam/fake)
     */
    @Transactional
    public void deleteRegistration(String tenantId) {
        TrialTracking trial = trialRepo.findByTenantId(tenantId)
            .orElseThrow(() -> new RuntimeException("Trial not found: " + tenantId));

        // Delete tenant
        tenantRepo.deleteById(tenantId);
        
        // Delete trial tracking
        trialRepo.delete(trial);

        log.info("Registration deleted: {}", tenantId);
    }

    /**
     * Add admin notes to a trial
     */
    @Transactional
    public TrialTracking addNote(String tenantId, String note, String addedBy) {
        TrialTracking trial = trialRepo.findByTenantId(tenantId)
            .orElseThrow(() -> new RuntimeException("Trial not found: " + tenantId));

        String timestamp = LocalDateTime.now().toString();
        String newNote = "[" + timestamp + " by " + addedBy + "]: " + note;
        
        trial.setNotes((trial.getNotes() != null ? trial.getNotes() + "\n" : "") + newNote);
        
        return trialRepo.save(trial);
    }

    /**
     * Get duplicate IP addresses with multiple registrations
     */
    public List<Map<String, Object>> getDuplicateIpRegistrations() {
        List<TrialTracking> allTrials = trialRepo.findAll();
        
        // Group by IP
        Map<String, List<TrialTracking>> byIp = allTrials.stream()
            .filter(t -> t.getIpAddress() != null)
            .collect(Collectors.groupingBy(TrialTracking::getIpAddress));

        // Filter IPs with more than 1 registration
        return byIp.entrySet().stream()
            .filter(e -> e.getValue().size() > 1)
            .map(e -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ipAddress", e.getKey());
                result.put("registrationCount", e.getValue().size());
                result.put("registrations", e.getValue().stream()
                    .map(t -> Map.of(
                        "tenantId", t.getTenantId(),
                        "companyName", t.getCompanyName(),
                        "email", t.getAdminEmail(),
                        "createdAt", t.getCreatedAt()
                    ))
                    .collect(Collectors.toList()));
                return result;
            })
            .collect(Collectors.toList());
    }

    /**
     * Auto-expire outdated trials (scheduled job)
     */
    @Transactional
    public int expireOutdatedTrials() {
        List<TrialTracking> expired = trialRepo.findExpiredActiveTrials(LocalDate.now());
        
        for (TrialTracking trial : expired) {
            trial.setTrialStatus(TrialStatus.EXPIRED);
            trialRepo.save(trial);
            
            // Optionally deactivate tenant
            tenantRepo.findById(trial.getTenantId()).ifPresent(tenant -> {
                // Don't deactivate, just limit features in the app
                // tenant.setIsActive(false);
                // tenantRepo.save(tenant);
            });
        }

        if (!expired.isEmpty()) {
            log.info("Auto-expired {} trials", expired.size());
        }
        
        return expired.size();
    }
}
