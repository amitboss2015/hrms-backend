package com.example.hrms.payroll.service;

import com.example.hrms.payroll.domain.SalaryOvertimeConfig;
import com.example.hrms.payroll.repo.SalaryOvertimeConfigRepository;
import com.example.hrms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for managing Salary & Overtime configuration.
 * Provides default configuration if none exists for tenant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SalaryOvertimeConfigService {

    private final SalaryOvertimeConfigRepository configRepo;

    /**
     * Get configuration for current tenant.
     * Creates default configuration if none exists.
     */
    @Transactional
    public SalaryOvertimeConfig getConfig() {
        String tenantId = TenantContext.getTenantIdOrDefault("SASA001");
        return getConfig(tenantId);
    }

    /**
     * Get configuration for specific tenant.
     * Creates default configuration if none exists.
     */
    @Transactional
    public SalaryOvertimeConfig getConfig(String tenantId) {
        Optional<SalaryOvertimeConfig> existing = configRepo.findByTenantIdAndActiveTrue(tenantId);
        
        if (existing.isPresent()) {
            return existing.get();
        }
        
        // Create and return default configuration
        log.info("Creating default salary/overtime config for tenant: {}", tenantId);
        return createDefaultConfig(tenantId);
    }

    /**
     * Create default configuration for a new tenant.
     */
    @Transactional
    public SalaryOvertimeConfig createDefaultConfig(String tenantId) {
        SalaryOvertimeConfig config = SalaryOvertimeConfig.builder()
                .tenantId(tenantId)
                .fullMonthSalaryThresholdDays(28) // Work 28 days = full month salary
                .salaryCalculationDaysInMonth(30)
                .standardWorkingHoursPerDay(8)
                .enableFullMonthSalaryThreshold(true)
                .overtimeMinThresholdMins(30) // Min 30 mins for OT
                .overtimeEnabled(true)
                .regularOvertimeMultiplier(new BigDecimal("1.0"))
                .weekendOvertimeMultiplier(new BigDecimal("1.5"))
                .holidayOvertimeMultiplier(new BigDecimal("2.0"))
                .maxOvertimeHoursPerDay(4)
                .maxOvertimeHoursPerMonth(50)
                .overtimeCalculationType(SalaryOvertimeConfig.OvertimeCalculationType.HOURLY)
                .lateArrivalsPerAbsent(3)
                .deductForLateArrival(true)
                .lateArrivalGraceMins(0)
                .halfDayMinHours(4)
                .fullDayMinHours(7)
                .active(true)
                .createdAt(LocalDateTime.now())
                .createdBy("system")
                .build();
        
        return configRepo.save(config);
    }

    /**
     * Update configuration for current tenant.
     */
    @Transactional
    public SalaryOvertimeConfig updateConfig(SalaryOvertimeConfig updatedConfig) {
        String tenantId = TenantContext.getTenantIdOrDefault("SASA001");
        return updateConfig(tenantId, updatedConfig);
    }

    /**
     * Update configuration for specific tenant.
     */
    @Transactional
    public SalaryOvertimeConfig updateConfig(String tenantId, SalaryOvertimeConfig updatedConfig) {
        Optional<SalaryOvertimeConfig> existingOpt = configRepo.findByTenantId(tenantId);
        
        SalaryOvertimeConfig config;
        if (existingOpt.isPresent()) {
            config = existingOpt.get();
            // Update fields
            config.setFullMonthSalaryThresholdDays(updatedConfig.getFullMonthSalaryThresholdDays());
            config.setSalaryCalculationDaysInMonth(updatedConfig.getSalaryCalculationDaysInMonth());
            config.setStandardWorkingHoursPerDay(updatedConfig.getStandardWorkingHoursPerDay());
            config.setEnableFullMonthSalaryThreshold(updatedConfig.getEnableFullMonthSalaryThreshold());
            
            config.setOvertimeMinThresholdMins(updatedConfig.getOvertimeMinThresholdMins());
            config.setOvertimeEnabled(updatedConfig.getOvertimeEnabled());
            config.setRegularOvertimeMultiplier(updatedConfig.getRegularOvertimeMultiplier());
            config.setWeekendOvertimeMultiplier(updatedConfig.getWeekendOvertimeMultiplier());
            config.setHolidayOvertimeMultiplier(updatedConfig.getHolidayOvertimeMultiplier());
            config.setMaxOvertimeHoursPerDay(updatedConfig.getMaxOvertimeHoursPerDay());
            config.setMaxOvertimeHoursPerMonth(updatedConfig.getMaxOvertimeHoursPerMonth());
            config.setOvertimeCalculationType(updatedConfig.getOvertimeCalculationType());
            
            config.setLateArrivalsPerAbsent(updatedConfig.getLateArrivalsPerAbsent());
            config.setDeductForLateArrival(updatedConfig.getDeductForLateArrival());
            config.setLateArrivalGraceMins(updatedConfig.getLateArrivalGraceMins());
            
            config.setHalfDayMinHours(updatedConfig.getHalfDayMinHours());
            config.setFullDayMinHours(updatedConfig.getFullDayMinHours());
            
            config.setActive(true);
            config.setUpdatedAt(LocalDateTime.now());
        } else {
            // Create new config
            config = updatedConfig;
            config.setTenantId(tenantId);
            config.setActive(true);
            config.setCreatedAt(LocalDateTime.now());
        }
        
        return configRepo.save(config);
    }

    /**
     * Delete (soft) configuration for current tenant.
     */
    @Transactional
    public void deleteConfig(Long configId) {
        configRepo.findById(configId).ifPresent(config -> {
            config.setActive(false);
            config.setUpdatedAt(LocalDateTime.now());
            configRepo.save(config);
        });
    }

    /**
     * Reset configuration to defaults for current tenant.
     */
    @Transactional
    public SalaryOvertimeConfig resetToDefaults() {
        String tenantId = TenantContext.getTenantIdOrDefault("SASA001");
        
        // Delete existing config
        configRepo.findByTenantId(tenantId).ifPresent(config -> {
            config.setActive(false);
            configRepo.save(config);
        });
        
        // Create new default
        return createDefaultConfig(tenantId);
    }

    // ==================== CALCULATION HELPERS ====================

    /**
     * Calculate overtime amount based on configuration.
     * 
     * @param monthlySalary Employee's monthly salary
     * @param overtimeMinutes Total overtime minutes worked
     * @param isWeekend Whether the OT was on weekend
     * @param isHoliday Whether the OT was on holiday
     * @return Overtime amount
     */
    public BigDecimal calculateOvertimeAmount(BigDecimal monthlySalary, int overtimeMinutes, 
                                               boolean isWeekend, boolean isHoliday) {
        SalaryOvertimeConfig config = getConfig();
        
        if (!config.getOvertimeEnabled() || overtimeMinutes < config.getOvertimeMinThresholdMins()) {
            return BigDecimal.ZERO;
        }

        // Get applicable multiplier
        BigDecimal multiplier = config.getRegularOvertimeMultiplier();
        if (isHoliday) {
            multiplier = config.getHolidayOvertimeMultiplier();
        } else if (isWeekend) {
            multiplier = config.getWeekendOvertimeMultiplier();
        }

        // Calculate per-hour rate
        BigDecimal dailyRate = monthlySalary.divide(
                new BigDecimal(config.getSalaryCalculationDaysInMonth()), 2, BigDecimal.ROUND_HALF_UP);
        BigDecimal hourlyRate = dailyRate.divide(
                new BigDecimal(config.getStandardWorkingHoursPerDay()), 2, BigDecimal.ROUND_HALF_UP);

        // Calculate OT amount
        BigDecimal overtimeHours = new BigDecimal(overtimeMinutes).divide(new BigDecimal(60), 2, BigDecimal.ROUND_HALF_UP);
        
        // Cap overtime hours
        BigDecimal maxOtHours = new BigDecimal(config.getMaxOvertimeHoursPerDay());
        if (overtimeHours.compareTo(maxOtHours) > 0) {
            overtimeHours = maxOtHours;
        }

        return hourlyRate.multiply(overtimeHours).multiply(multiplier)
                .setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Calculate effective working days for salary.
     * If employee worked >= threshold days, return full month days.
     * 
     * @param actualDaysWorked Actual days employee worked
     * @return Effective days for salary calculation
     */
    public int getEffectiveWorkingDays(int actualDaysWorked) {
        SalaryOvertimeConfig config = getConfig();
        
        if (config.getEnableFullMonthSalaryThreshold() && 
            actualDaysWorked >= config.getFullMonthSalaryThresholdDays()) {
            return config.getSalaryCalculationDaysInMonth(); // Full month salary
        }
        
        return actualDaysWorked;
    }

    /**
     * Check if overtime should be counted based on minimum threshold.
     * 
     * @param extraMinutesWorked Minutes worked beyond shift
     * @return true if should count as OT
     */
    public boolean shouldCountAsOvertime(int extraMinutesWorked) {
        SalaryOvertimeConfig config = getConfig();
        return config.getOvertimeEnabled() && 
               extraMinutesWorked >= config.getOvertimeMinThresholdMins();
    }

    /**
     * Get number of late arrivals that equal one absent day.
     */
    public int getLateArrivalsPerAbsent() {
        return getConfig().getLateArrivalsPerAbsent();
    }

    /**
     * Get minimum OT threshold in minutes.
     */
    public int getOvertimeMinThresholdMins() {
        return getConfig().getOvertimeMinThresholdMins();
    }
}
