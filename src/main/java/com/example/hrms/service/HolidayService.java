package com.example.hrms.service;

import com.example.hrms.domain.Holiday;
import com.example.hrms.domain.WeeklyOffConfig;
import com.example.hrms.domain.enums.EmploymentType;
import com.example.hrms.repo.HolidayRepository;
import com.example.hrms.repo.WeeklyOffConfigRepository;
import com.example.hrms.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class HolidayService {

    private final HolidayRepository holidayRepo;
    private final WeeklyOffConfigRepository weeklyOffRepo;

    public HolidayService(HolidayRepository holidayRepo, WeeklyOffConfigRepository weeklyOffRepo) {
        this.holidayRepo = holidayRepo;
        this.weeklyOffRepo = weeklyOffRepo;
    }
    
    /**
     * Get current tenant ID with fallback
     */
    private String getTenantId() {
        return TenantContext.getTenantIdOrDefault("ORG001");
    }
    
    /**
     * Resolve tenant ID from parameter or context
     */
    private String resolveTenantId(String orgId) {
        if (orgId != null && !orgId.isEmpty()) {
            return orgId;
        }
        return getTenantId();
    }

    // =========== Holiday Management ===========

    public List<Holiday> getHolidays(String orgId, Integer year) {
        String tenantId = resolveTenantId(orgId);
        if (year != null) {
            return holidayRepo.findByTenantIdAndYearAndActiveTrue(tenantId, year);
        }
        return holidayRepo.findByTenantIdAndActiveTrue(tenantId);
    }
    
    /**
     * Get holidays for current tenant
     */
    public List<Holiday> getHolidays(Integer year) {
        return getHolidays(getTenantId(), year);
    }

    public List<Holiday> getHolidaysInRange(String orgId, LocalDate startDate, LocalDate endDate) {
        String tenantId = resolveTenantId(orgId);
        return holidayRepo.findByTenantIdAndHolidayDateBetweenAndActiveTrue(tenantId, startDate, endDate);
    }
    
    /**
     * Get holidays in range for current tenant
     */
    public List<Holiday> getHolidaysInRange(LocalDate startDate, LocalDate endDate) {
        return getHolidaysInRange(getTenantId(), startDate, endDate);
    }

    public Holiday createHoliday(Holiday holiday) {
        // Set tenant ID if not already set
        if (holiday.getTenantId() == null || holiday.getTenantId().isEmpty()) {
            holiday.setTenantId(getTenantId());
        }
        if (holiday.getYear() == null) {
            holiday.setYear(holiday.getHolidayDate().getYear());
        }
        return holidayRepo.save(holiday);
    }

    public Holiday updateHoliday(Long id, Holiday updates) {
        Holiday holiday = holidayRepo.findById(id).orElseThrow();
        if (updates.getName() != null) holiday.setName(updates.getName());
        if (updates.getDescription() != null) holiday.setDescription(updates.getDescription());
        if (updates.getHolidayDate() != null) {
            holiday.setHolidayDate(updates.getHolidayDate());
            holiday.setYear(updates.getHolidayDate().getYear());
        }
        if (updates.getApplicableEmploymentTypes() != null) {
            holiday.setApplicableEmploymentTypes(updates.getApplicableEmploymentTypes());
        }
        if (updates.getIsPaid() != null) holiday.setIsPaid(updates.getIsPaid());
        if (updates.getIsOptional() != null) holiday.setIsOptional(updates.getIsOptional());
        return holidayRepo.save(holiday);
    }

    public void deleteHoliday(Long id) {
        Holiday holiday = holidayRepo.findById(id).orElseThrow();
        holiday.setActive(false);
        holidayRepo.save(holiday);
    }

    // =========== Weekly Off Configuration ===========

    public List<WeeklyOffConfig> getWeeklyOffConfigs(String orgId) {
        String tenantId = resolveTenantId(orgId);
        return weeklyOffRepo.findByTenantIdAndActiveTrue(tenantId);
    }
    
    /**
     * Get weekly off configs for current tenant
     */
    public List<WeeklyOffConfig> getWeeklyOffConfigs() {
        return getWeeklyOffConfigs(getTenantId());
    }

    public Optional<WeeklyOffConfig> getWeeklyOffConfig(String orgId, EmploymentType empType) {
        String tenantId = resolveTenantId(orgId);
        return weeklyOffRepo.findByTenantIdAndEmploymentTypeAndActiveTrue(tenantId, empType);
    }
    
    /**
     * Get weekly off config for current tenant
     */
    public Optional<WeeklyOffConfig> getWeeklyOffConfig(EmploymentType empType) {
        return getWeeklyOffConfig(getTenantId(), empType);
    }

    public WeeklyOffConfig createOrUpdateWeeklyOff(WeeklyOffConfig config) {
        // Set tenant ID if not already set
        if (config.getTenantId() == null || config.getTenantId().isEmpty()) {
            config.setTenantId(getTenantId());
        }
        
        Optional<WeeklyOffConfig> existing = weeklyOffRepo.findByTenantIdAndEmploymentTypeAndActiveTrue(
                config.getTenantId(), config.getEmploymentType());
        
        if (existing.isPresent()) {
            WeeklyOffConfig ex = existing.get();
            ex.setWeeklyOffDays(config.getWeeklyOffDays());
            ex.setAlternateSaturdayRule(config.getAlternateSaturdayRule());
            return weeklyOffRepo.save(ex);
        }
        return weeklyOffRepo.save(config);
    }

    public void deleteWeeklyOffConfig(Long id) {
        WeeklyOffConfig config = weeklyOffRepo.findById(id).orElseThrow();
        config.setActive(false);
        weeklyOffRepo.save(config);
    }

    // =========== Utility Methods ===========

    public boolean isHoliday(String orgId, LocalDate date, EmploymentType empType) {
        String tenantId = resolveTenantId(orgId);
        List<Holiday> holidays = holidayRepo.findByTenantIdAndHolidayDateBetweenAndActiveTrue(tenantId, date, date);
        return holidays.stream().anyMatch(h -> h.appliesToEmploymentType(empType));
    }
    
    /**
     * Check if date is holiday for current tenant
     */
    public boolean isHoliday(LocalDate date, EmploymentType empType) {
        return isHoliday(getTenantId(), date, empType);
    }

    public boolean isWeeklyOff(String orgId, LocalDate date, EmploymentType empType) {
        String tenantId = resolveTenantId(orgId);
        Optional<WeeklyOffConfig> config = weeklyOffRepo.findByTenantIdAndEmploymentTypeAndActiveTrue(tenantId, empType);
        return config.map(c -> c.isWeeklyOff(date.getDayOfWeek())).orElse(date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY);
    }
    
    /**
     * Check if date is weekly off for current tenant
     */
    public boolean isWeeklyOff(LocalDate date, EmploymentType empType) {
        return isWeeklyOff(getTenantId(), date, empType);
    }

    public boolean isNonWorkingDay(String orgId, LocalDate date, EmploymentType empType) {
        return isHoliday(orgId, date, empType) || isWeeklyOff(orgId, date, empType);
    }
    
    /**
     * Check if date is non-working day for current tenant
     */
    public boolean isNonWorkingDay(LocalDate date, EmploymentType empType) {
        return isNonWorkingDay(getTenantId(), date, empType);
    }
}
