package com.example.hrms.admin.controller;

import com.example.hrms.admin.domain.RegistrationAttempt;
import com.example.hrms.admin.domain.TrialTracking;
import com.example.hrms.admin.service.AdminDashboardService;
import com.example.hrms.admin.service.CompanyManagementService;
import com.example.hrms.admin.service.FraudDetectionService;
import com.example.hrms.attendance.domain.BiometricDevice;
import com.example.hrms.attendance.repo.BiometricDeviceRepository;
import com.example.hrms.tenant.domain.Tenant;
import com.example.hrms.tenant.repo.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

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
    private final CompanyManagementService companyService;
    private final BiometricDeviceRepository deviceRepo;
    private final TenantRepository tenantRepo;
    private final JdbcTemplate jdbcTemplate;

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

    // ==================== COMPANY MANAGEMENT ====================

    /**
     * Get all active companies
     */
    @GetMapping("/companies")
    public ResponseEntity<List<Tenant>> getActiveCompanies() {
        return ResponseEntity.ok(companyService.getActiveCompanies());
    }

    /**
     * Get companies in recycle bin
     */
    @GetMapping("/companies/recycle-bin")
    public ResponseEntity<List<Tenant>> getDeletedCompanies() {
        return ResponseEntity.ok(companyService.getDeletedCompanies());
    }

    /**
     * Get company management statistics
     */
    @GetMapping("/companies/stats")
    public ResponseEntity<Map<String, Object>> getCompanyStats() {
        return ResponseEntity.ok(companyService.getCompanyStats());
    }

    /**
     * Get data counts for a company (before deletion)
     */
    @GetMapping("/companies/{tenantId}/data-counts")
    public ResponseEntity<Map<String, Integer>> getCompanyDataCounts(@PathVariable String tenantId) {
        return ResponseEntity.ok(companyService.getCompanyDataCounts(tenantId));
    }

    /**
     * Soft delete a company (move to recycle bin)
     */
    @PostMapping("/companies/{tenantId}/soft-delete")
    public ResponseEntity<?> softDeleteCompany(
            @PathVariable String tenantId,
            @RequestBody SoftDeleteRequest request,
            Authentication auth) {
        try {
            String deletedBy = auth != null ? auth.getName() : request.deletedBy();
            return ResponseEntity.ok(companyService.softDeleteCompany(tenantId, deletedBy, request.reason()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Cannot delete company",
                "message", e.getMessage(),
                "tenantId", tenantId
            ));
        }
    }

    /**
     * Restore a company from recycle bin
     */
    @PostMapping("/companies/{tenantId}/restore")
    public ResponseEntity<?> restoreCompany(@PathVariable String tenantId) {
        try {
            return ResponseEntity.ok(companyService.restoreCompany(tenantId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Cannot restore company",
                "message", e.getMessage(),
                "tenantId", tenantId
            ));
        }
    }

    /**
     * Permanently delete a company and ALL its data
     * WARNING: This is irreversible!
     */
    @DeleteMapping("/companies/{tenantId}/permanent")
    public ResponseEntity<?> permanentDeleteCompany(
            @PathVariable String tenantId,
            Authentication auth) {
        try {
            String deletedBy = auth != null ? auth.getName() : "SUPER_ADMIN";
            return ResponseEntity.ok(companyService.permanentDeleteCompany(tenantId, deletedBy));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Cannot permanently delete company",
                "message", e.getMessage(),
                "tenantId", tenantId
            ));
        }
    }

    // ==================== BIOMETRIC DEVICE CLEANUP ====================

    /**
     * Get all biometric devices across all tenants (superadmin view)
     */
    @GetMapping("/devices")
    public ResponseEntity<List<Map<String, Object>>> getAllDevices() {
        List<BiometricDevice> devices = deviceRepo.findAll();
        
        List<Map<String, Object>> result = devices.stream().map(d -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", d.getId());
            map.put("tenantId", d.getTenantId());
            map.put("deviceCode", d.getDeviceCode());
            map.put("deviceName", d.getDeviceName());
            map.put("isDefault", d.getIsDefault());
            map.put("isActive", d.getIsActive());
            return map;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }

    /**
     * Clean up orphan and duplicate biometric devices across ALL tenants.
     * - Removes devices where tenant no longer exists
     * - Removes duplicate devices (keeps lowest ID for each tenant+deviceCode)
     */
    @DeleteMapping("/devices/cleanup-all")
    @Transactional
    public ResponseEntity<Map<String, Object>> cleanupAllDevices() {
        Map<String, Object> result = new LinkedHashMap<>();
        
        // Get all valid tenant IDs
        Set<String> validTenantIds = tenantRepo.findAll().stream()
                .map(Tenant::getId)
                .collect(Collectors.toSet());
        
        // Get all devices
        List<BiometricDevice> allDevices = deviceRepo.findAll();
        
        int orphansDeleted = 0;
        int duplicatesDeleted = 0;
        List<String> orphanDetails = new ArrayList<>();
        List<String> duplicateDetails = new ArrayList<>();
        
        // 1. Delete orphan devices (tenant doesn't exist)
        for (BiometricDevice device : allDevices) {
            if (!validTenantIds.contains(device.getTenantId())) {
                orphanDetails.add(String.format("ID=%d, Tenant=%s, Code=%s", 
                        device.getId(), device.getTenantId(), device.getDeviceCode()));
                deviceRepo.delete(device);
                orphansDeleted++;
            }
        }
        
        // 2. Delete duplicates (same tenant + deviceCode, keep lowest ID)
        // Re-fetch after orphan deletion
        allDevices = deviceRepo.findAll();
        
        Map<String, List<BiometricDevice>> grouped = allDevices.stream()
                .collect(Collectors.groupingBy(d -> d.getTenantId() + "|" + d.getDeviceCode()));
        
        for (Map.Entry<String, List<BiometricDevice>> entry : grouped.entrySet()) {
            List<BiometricDevice> devices = entry.getValue();
            if (devices.size() > 1) {
                // Sort by ID, keep first
                devices.sort((a, b) -> a.getId().compareTo(b.getId()));
                for (int i = 1; i < devices.size(); i++) {
                    BiometricDevice dup = devices.get(i);
                    duplicateDetails.add(String.format("ID=%d, Tenant=%s, Code=%s", 
                            dup.getId(), dup.getTenantId(), dup.getDeviceCode()));
                    deviceRepo.delete(dup);
                    duplicatesDeleted++;
                }
            }
        }
        
        log.info("🧹 Cleanup complete: {} orphans, {} duplicates removed", orphansDeleted, duplicatesDeleted);
        
        result.put("orphansDeleted", orphansDeleted);
        result.put("orphanDetails", orphanDetails);
        result.put("duplicatesDeleted", duplicatesDeleted);
        result.put("duplicateDetails", duplicateDetails);
        result.put("totalDeleted", orphansDeleted + duplicatesDeleted);
        result.put("remainingDevices", deviceRepo.count());
        
        return ResponseEntity.ok(result);
    }

    /**
     * Delete a specific device by ID (superadmin)
     */
    @DeleteMapping("/devices/{deviceId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteDevice(@PathVariable Long deviceId) {
        Optional<BiometricDevice> deviceOpt = deviceRepo.findById(deviceId);
        
        if (deviceOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        BiometricDevice device = deviceOpt.get();
        String info = String.format("ID=%d, Tenant=%s, Code=%s, Name=%s", 
                device.getId(), device.getTenantId(), device.getDeviceCode(), device.getDeviceName());
        
        deviceRepo.delete(device);
        log.info("🗑️ Superadmin deleted device: {}", info);
        
        return ResponseEntity.ok(Map.of(
                "deleted", true,
                "device", info
        ));
    }

    // ==================== REQUEST RECORDS ====================

    record SuspendRequest(String reason, String suspendedBy) {}
    record NoteRequest(String note, String addedBy) {}
    record FraudCheckRequest(String email, String companyName, String phone, 
                            String ipAddress, String userAgent) {}
    record SoftDeleteRequest(String reason, String deletedBy) {}
}
