package com.example.hrms.registration.service;

import com.example.hrms.attendance.domain.BiometricDevice;
import com.example.hrms.attendance.repo.BiometricDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for setting up biometric devices for new companies.
 * This is a separate service to allow proper transaction management
 * with REQUIRES_NEW propagation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BiometricDeviceSetupService {

    private final BiometricDeviceRepository biometricDeviceRepo;

    /**
     * Create a default biometric device for the new company.
     * This runs in a SEPARATE transaction so failures don't affect the calling transaction.
     * 
     * @param tenantId The tenant ID for the new company
     * @param companyName The company name (for logging)
     * @return true if device was created successfully, false otherwise
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean createDefaultBiometricDevice(String tenantId, String companyName) {
        try {
            // Check if default device already exists for this tenant
            boolean exists = biometricDeviceRepo.existsByTenantIdAndDeviceCode(tenantId, "DEFAULT");
            if (exists) {
                log.info("⚠️ Default biometric device already exists for tenant: {}", tenantId);
                return true; // Already exists, that's fine
            }
            
            BiometricDevice defaultDevice = BiometricDevice.builder()
                    .tenantId(tenantId)
                    .deviceCode("DEFAULT")
                    .deviceName("Main Attendance Device")
                    .location("Main Office")
                    .isActive(true)
                    .isDefault(true)
                    .build();
            
            biometricDeviceRepo.save(defaultDevice);
            log.info("✅ Default biometric device created for tenant: {}", tenantId);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to create default biometric device for tenant: {} - Error: {}", 
                    tenantId, e.getMessage(), e);
            // Return false but don't throw - let the calling code handle it
            return false;
        }
    }
}
