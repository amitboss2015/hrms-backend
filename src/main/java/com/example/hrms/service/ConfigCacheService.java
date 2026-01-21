package com.example.hrms.service;

import com.example.hrms.config.CacheConfig;
import com.example.hrms.domain.Holiday;
import com.example.hrms.domain.Shift;
import com.example.hrms.domain.WeeklyOffConfig;
import com.example.hrms.domain.enums.EmploymentType;
import com.example.hrms.payroll.domain.SalaryOvertimeConfig;
import com.example.hrms.payroll.repo.SalaryOvertimeConfigRepository;
import com.example.hrms.repo.HolidayRepository;
import com.example.hrms.repo.ShiftRepository;
import com.example.hrms.repo.WeeklyOffConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Cached service for frequently accessed configuration data.
 * 
 * This service provides caching for:
 * - Weekly off configurations
 * - Salary/overtime configurations
 * - Holidays
 * - Shifts
 * 
 * Cache is invalidated when configurations are updated.
 * Default TTL: 10 minutes (configured in CacheConfig)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigCacheService {

    private final WeeklyOffConfigRepository weeklyOffRepo;
    private final SalaryOvertimeConfigRepository salaryConfigRepo;
    private final HolidayRepository holidayRepo;
    private final ShiftRepository shiftRepo;

    // ============ WEEKLY OFF CONFIG ============

    /**
     * Get weekly off config for a tenant and employment type.
     * Cached to avoid repeated queries during batch operations.
     */
    @Cacheable(value = CacheConfig.CACHE_WEEKLY_OFF, 
               key = "T(com.example.hrms.config.CacheConfig).tenantKey(#tenantId, #employmentType?.name())")
    public Optional<WeeklyOffConfig> getWeeklyOffConfig(String tenantId, EmploymentType employmentType) {
        log.debug("[CACHE MISS] Loading WeeklyOffConfig for tenant={}, type={}", tenantId, employmentType);
        return weeklyOffRepo.findByTenantIdAndEmploymentTypeAndActiveTrue(tenantId, employmentType);
    }

    /**
     * Get all active weekly off configs for a tenant (preload for batch).
     */
    @Cacheable(value = CacheConfig.CACHE_WEEKLY_OFF, key = "'all:' + #tenantId")
    public List<WeeklyOffConfig> getAllWeeklyOffConfigs(String tenantId) {
        log.debug("[CACHE MISS] Loading all WeeklyOffConfigs for tenant={}", tenantId);
        return weeklyOffRepo.findByTenantIdAndActiveTrue(tenantId);
    }

    @CacheEvict(value = CacheConfig.CACHE_WEEKLY_OFF, allEntries = true)
    public void evictWeeklyOffCache() {
        log.info("[CACHE] Evicted weekly off config cache");
    }

    // ============ SALARY/OVERTIME CONFIG ============

    /**
     * Get salary/overtime config for a tenant.
     * Cached as this rarely changes but is queried for every employee.
     */
    @Cacheable(value = CacheConfig.CACHE_SALARY_CONFIG, key = "#tenantId")
    public Optional<SalaryOvertimeConfig> getSalaryConfig(String tenantId) {
        log.debug("[CACHE MISS] Loading SalaryOvertimeConfig for tenant={}", tenantId);
        return salaryConfigRepo.findByTenantId(tenantId);
    }

    @CacheEvict(value = CacheConfig.CACHE_SALARY_CONFIG, key = "#tenantId")
    public void evictSalaryConfigCache(String tenantId) {
        log.info("[CACHE] Evicted salary config cache for tenant={}", tenantId);
    }

    // ============ HOLIDAYS ============

    /**
     * Get active holidays for a tenant and year.
     * Cached as holidays are fixed for a year.
     */
    @Cacheable(value = CacheConfig.CACHE_HOLIDAYS, 
               key = "T(com.example.hrms.config.CacheConfig).tenantKey(#tenantId, #year.toString())")
    public List<Holiday> getHolidays(String tenantId, int year) {
        log.debug("[CACHE MISS] Loading Holidays for tenant={}, year={}", tenantId, year);
        return holidayRepo.findByTenantIdAndYearAndActiveTrue(tenantId, year);
    }

    /**
     * Get active holidays for a date range.
     * Used by payroll calculation.
     */
    @Cacheable(value = CacheConfig.CACHE_HOLIDAYS, 
               key = "'active:' + T(com.example.hrms.config.CacheConfig).tenantKey(#tenantId, #startDate.toString(), #endDate.toString())")
    public List<Holiday> getActiveHolidaysInRange(String tenantId, LocalDate startDate, LocalDate endDate) {
        log.debug("[CACHE MISS] Loading active Holidays for tenant={}, from={} to={}", tenantId, startDate, endDate);
        return holidayRepo.findByTenantIdAndHolidayDateBetweenAndActiveTrue(tenantId, startDate, endDate);
    }

    @CacheEvict(value = CacheConfig.CACHE_HOLIDAYS, allEntries = true)
    public void evictHolidaysCache() {
        log.info("[CACHE] Evicted holidays cache");
    }

    // ============ SHIFTS ============

    /**
     * Get all shifts for a tenant.
     */
    @Cacheable(value = CacheConfig.CACHE_SHIFTS, key = "#tenantId")
    public List<Shift> getShifts(String tenantId) {
        log.debug("[CACHE MISS] Loading Shifts for tenant={}", tenantId);
        return shiftRepo.findByTenantId(tenantId);
    }

    /**
     * Get shift by code.
     */
    @Cacheable(value = CacheConfig.CACHE_SHIFTS, 
               key = "T(com.example.hrms.config.CacheConfig).tenantKey(#tenantId, #shiftCode)")
    public Optional<Shift> getShiftByCode(String tenantId, String shiftCode) {
        log.debug("[CACHE MISS] Loading Shift for tenant={}, code={}", tenantId, shiftCode);
        return shiftRepo.findByTenantIdAndCode(tenantId, shiftCode);
    }

    @CacheEvict(value = CacheConfig.CACHE_SHIFTS, allEntries = true)
    public void evictShiftsCache() {
        log.info("[CACHE] Evicted shifts cache");
    }

    // ============ BULK CACHE OPERATIONS ============

    /**
     * Evict all caches for a tenant (use after bulk config updates).
     */
    @CacheEvict(value = {
            CacheConfig.CACHE_WEEKLY_OFF,
            CacheConfig.CACHE_SALARY_CONFIG,
            CacheConfig.CACHE_HOLIDAYS,
            CacheConfig.CACHE_SHIFTS
    }, allEntries = true)
    public void evictAllConfigCaches() {
        log.info("[CACHE] Evicted ALL config caches");
    }
}
