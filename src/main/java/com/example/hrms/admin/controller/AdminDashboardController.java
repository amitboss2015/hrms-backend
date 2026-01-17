package com.example.hrms.admin.controller;

import com.example.hrms.admin.domain.RegistrationAttempt;
import com.example.hrms.admin.domain.TrialTracking;
import com.example.hrms.admin.service.AdminDashboardService;
import com.example.hrms.admin.service.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin Dashboard API for Super Admin
 * Provides monitoring and management of trials, registrations, and fraud detection
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;
    private final FraudDetectionService fraudService;

    // ==================== DASHBOARD ====================

    /**
     * Get dashboard overview statistics
     */
    @GetMapping("/dashboard/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        return ResponseEntity.ok(dashboardService.getDashboardStats());
    }

    // ==================== TRIALS ====================

    /**
     * Get all trials with optional filters
     */
    @GetMapping("/trials")
    public ResponseEntity<List<TrialTracking>> getTrials(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) Boolean suspended) {
        return ResponseEntity.ok(dashboardService.getTrials(status, riskLevel, suspended));
    }

    /**
     * Get trials expiring soon
     */
    @GetMapping("/trials/expiring")
    public ResponseEntity<List<TrialTracking>> getTrialsExpiringSoon(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(dashboardService.getTrialsExpiringSoon(days));
    }

    /**
     * Get high-risk registrations
     */
    @GetMapping("/trials/high-risk")
    public ResponseEntity<List<TrialTracking>> getHighRiskRegistrations(
            @RequestParam(defaultValue = "50") int minScore) {
        return ResponseEntity.ok(dashboardService.getHighRiskRegistrations(minScore));
    }

    /**
     * Get duplicate IP registrations
     */
    @GetMapping("/trials/duplicate-ips")
    public ResponseEntity<List<Map<String, Object>>> getDuplicateIpRegistrations() {
        return ResponseEntity.ok(dashboardService.getDuplicateIpRegistrations());
    }

    // ==================== TRIAL ACTIONS ====================

    /**
     * Suspend a trial
     */
    @PostMapping("/trials/{tenantId}/suspend")
    public ResponseEntity<TrialTracking> suspendTrial(
            @PathVariable String tenantId,
            @RequestBody SuspendRequest request) {
        return ResponseEntity.ok(dashboardService.suspendTrial(
            tenantId, request.reason(), request.suspendedBy()));
    }

    /**
     * Reactivate a suspended trial
     */
    @PostMapping("/trials/{tenantId}/reactivate")
    public ResponseEntity<TrialTracking> reactivateTrial(
            @PathVariable String tenantId,
            @RequestParam String reactivatedBy) {
        return ResponseEntity.ok(dashboardService.reactivateTrial(tenantId, reactivatedBy));
    }

    /**
     * Extend a trial period
     */
    @PostMapping("/trials/{tenantId}/extend")
    public ResponseEntity<TrialTracking> extendTrial(
            @PathVariable String tenantId,
            @RequestParam int days,
            @RequestParam String extendedBy) {
        return ResponseEntity.ok(dashboardService.extendTrial(tenantId, days, extendedBy));
    }

    /**
     * Mark trial as converted (paid customer)
     */
    @PostMapping("/trials/{tenantId}/convert")
    public ResponseEntity<TrialTracking> markAsConverted(
            @PathVariable String tenantId,
            @RequestParam String planName) {
        return ResponseEntity.ok(dashboardService.markAsConverted(tenantId, planName));
    }

    /**
     * Delete a registration (spam/fake)
     */
    @DeleteMapping("/trials/{tenantId}")
    public ResponseEntity<Map<String, String>> deleteRegistration(
            @PathVariable String tenantId) {
        dashboardService.deleteRegistration(tenantId);
        return ResponseEntity.ok(Map.of(
            "message", "Registration deleted successfully",
            "tenantId", tenantId
        ));
    }

    /**
     * Add admin notes to a trial
     */
    @PostMapping("/trials/{tenantId}/notes")
    public ResponseEntity<TrialTracking> addNote(
            @PathVariable String tenantId,
            @RequestBody NoteRequest request) {
        return ResponseEntity.ok(dashboardService.addNote(
            tenantId, request.note(), request.addedBy()));
    }

    // ==================== REGISTRATION ATTEMPTS ====================

    /**
     * Get recent registration attempts
     */
    @GetMapping("/attempts")
    public ResponseEntity<List<RegistrationAttempt>> getRecentAttempts(
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(dashboardService.getRecentAttempts(hours));
    }

    // ==================== FRAUD DETECTION ====================

    /**
     * Check fraud score for an email/company (for testing)
     */
    @PostMapping("/fraud-check")
    public ResponseEntity<FraudDetectionService.FraudResult> checkFraud(
            @RequestBody FraudCheckRequest request) {
        return ResponseEntity.ok(fraudService.calculateFraudScore(
            request.email(),
            request.companyName(),
            request.phone(),
            request.ipAddress(),
            request.userAgent()
        ));
    }

    /**
     * Check if email is disposable
     */
    @GetMapping("/fraud-check/email")
    public ResponseEntity<Map<String, Object>> checkEmail(@RequestParam String email) {
        return ResponseEntity.ok(Map.of(
            "email", email,
            "isDisposable", fraudService.isDisposableEmail(email),
            "isFreeProvider", fraudService.isFreeEmailProvider(email)
        ));
    }

    // ==================== MAINTENANCE ====================

    /**
     * Manually trigger trial expiry check
     */
    @PostMapping("/maintenance/expire-trials")
    public ResponseEntity<Map<String, Object>> expireTrials() {
        int count = dashboardService.expireOutdatedTrials();
        return ResponseEntity.ok(Map.of(
            "message", "Trial expiry check completed",
            "expiredCount", count
        ));
    }

    // ==================== REQUEST RECORDS ====================

    record SuspendRequest(String reason, String suspendedBy) {}
    record NoteRequest(String note, String addedBy) {}
    record FraudCheckRequest(String email, String companyName, String phone, 
                            String ipAddress, String userAgent) {}
}
