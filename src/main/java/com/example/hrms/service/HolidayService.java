package com.example.hrms.service;

import com.example.hrms.domain.Holiday;
import com.example.hrms.domain.WeeklyOffConfig;
import com.example.hrms.domain.enums.EmploymentType;
import com.example.hrms.repo.HolidayRepository;
import com.example.hrms.repo.WeeklyOffConfigRepository;
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

    // =========== Holiday Management ===========

    public List<Holiday> getHolidays(String orgId, Integer year) {
        if (year != null) {
            return holidayRepo.findByOrgIdAndYearAndActiveTrue(orgId, year);
        }
        return holidayRepo.findByOrgIdAndActiveTrue(orgId);
    }

    public List<Holiday> getHolidaysInRange(String orgId, LocalDate startDate, LocalDate endDate) {
        return holidayRepo.findByOrgIdAndHolidayDateBetweenAndActiveTrue(orgId, startDate, endDate);
    }

    public Holiday createHoliday(Holiday holiday) {
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
        return weeklyOffRepo.findByOrgIdAndActiveTrue(orgId);
    }

    public Optional<WeeklyOffConfig> getWeeklyOffConfig(String orgId, EmploymentType empType) {
        return weeklyOffRepo.findByOrgIdAndEmploymentTypeAndActiveTrue(orgId, empType);
    }

    public WeeklyOffConfig createOrUpdateWeeklyOff(WeeklyOffConfig config) {
        Optional<WeeklyOffConfig> existing = weeklyOffRepo.findByOrgIdAndEmploymentTypeAndActiveTrue(
                config.getOrgId(), config.getEmploymentType());
        
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
        List<Holiday> holidays = holidayRepo.findByOrgIdAndHolidayDateBetweenAndActiveTrue(orgId, date, date);
        return holidays.stream().anyMatch(h -> h.appliesToEmploymentType(empType));
    }

    public boolean isWeeklyOff(String orgId, LocalDate date, EmploymentType empType) {
        Optional<WeeklyOffConfig> config = weeklyOffRepo.findByOrgIdAndEmploymentTypeAndActiveTrue(orgId, empType);
        return config.map(c -> c.isWeeklyOff(date.getDayOfWeek())).orElse(date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY);
    }

    public boolean isNonWorkingDay(String orgId, LocalDate date, EmploymentType empType) {
        return isHoliday(orgId, date, empType) || isWeeklyOff(orgId, date, empType);
    }
}
