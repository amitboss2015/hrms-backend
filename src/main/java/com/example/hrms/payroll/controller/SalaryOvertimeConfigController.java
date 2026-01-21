package com.example.hrms.payroll.controller;

import com.example.hrms.payroll.domain.SalaryOvertimeConfig;
import com.example.hrms.payroll.service.SalaryOvertimeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for Salary & Overtime Configuration.
 */
@RestController
@RequestMapping("/api/config/salary-overtime")
@RequiredArgsConstructor
public class SalaryOvertimeConfigController {

    private final SalaryOvertimeConfigService configService;

    /**
     * Get current configuration for the tenant.
     * Creates default if not exists.
     */
    @GetMapping
    public ResponseEntity<SalaryOvertimeConfig> getConfig() {
        return ResponseEntity.ok(configService.getConfig());
    }

    /**
     * Update configuration for the tenant.
     */
    @PostMapping
    public ResponseEntity<SalaryOvertimeConfig> updateConfig(@RequestBody SalaryOvertimeConfig config) {
        return ResponseEntity.ok(configService.updateConfig(config));
    }

    /**
     * Reset configuration to defaults.
     */
    @PostMapping("/reset")
    public ResponseEntity<SalaryOvertimeConfig> resetToDefaults() {
        return ResponseEntity.ok(configService.resetToDefaults());
    }

    /**
     * Get configuration summary (simplified view for display).
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getConfigSummary() {
        SalaryOvertimeConfig config = configService.getConfig();
        
        Map<String, Object> summary = new HashMap<>();
        
        // Salary Rules
        Map<String, Object> salaryRules = new HashMap<>();
        salaryRules.put("fullMonthThresholdDays", config.getFullMonthSalaryThresholdDays());
        salaryRules.put("daysInMonth", config.getSalaryCalculationDaysInMonth());
        salaryRules.put("standardHoursPerDay", config.getStandardWorkingHoursPerDay());
        salaryRules.put("thresholdEnabled", config.getEnableFullMonthSalaryThreshold());
        summary.put("salaryRules", salaryRules);
        
        // Overtime Rules
        Map<String, Object> otRules = new HashMap<>();
        otRules.put("enabled", config.getOvertimeEnabled());
        otRules.put("minThresholdMins", config.getOvertimeMinThresholdMins());
        otRules.put("regularMultiplier", config.getRegularOvertimeMultiplier());
        otRules.put("weekendMultiplier", config.getWeekendOvertimeMultiplier());
        otRules.put("holidayMultiplier", config.getHolidayOvertimeMultiplier());
        otRules.put("maxHoursPerDay", config.getMaxOvertimeHoursPerDay());
        otRules.put("maxHoursPerMonth", config.getMaxOvertimeHoursPerMonth());
        otRules.put("calculationType", config.getOvertimeCalculationType());
        summary.put("overtimeRules", otRules);
        
        // Late/Attendance Rules
        Map<String, Object> attendanceRules = new HashMap<>();
        attendanceRules.put("lateArrivalsPerAbsent", config.getLateArrivalsPerAbsent());
        attendanceRules.put("deductForLate", config.getDeductForLateArrival());
        attendanceRules.put("lateGraceMins", config.getLateArrivalGraceMins());
        attendanceRules.put("halfDayMinHours", config.getHalfDayMinHours());
        attendanceRules.put("fullDayMinHours", config.getFullDayMinHours());
        summary.put("attendanceRules", attendanceRules);
        
        return ResponseEntity.ok(summary);
    }

    /**
     * Calculate overtime for given parameters (preview/test endpoint).
     */
    @PostMapping("/calculate-ot")
    public ResponseEntity<Map<String, Object>> calculateOvertime(
            @RequestParam double monthlySalary,
            @RequestParam int overtimeMinutes,
            @RequestParam(defaultValue = "false") boolean isWeekend,
            @RequestParam(defaultValue = "false") boolean isHoliday) {
        
        SalaryOvertimeConfig config = configService.getConfig();
        
        var otAmount = configService.calculateOvertimeAmount(
                new java.math.BigDecimal(monthlySalary), 
                overtimeMinutes, isWeekend, isHoliday);
        
        Map<String, Object> result = new HashMap<>();
        result.put("monthlySalary", monthlySalary);
        result.put("overtimeMinutes", overtimeMinutes);
        result.put("overtimeHours", overtimeMinutes / 60.0);
        result.put("isWeekend", isWeekend);
        result.put("isHoliday", isHoliday);
        result.put("minThresholdMins", config.getOvertimeMinThresholdMins());
        result.put("qualifiesForOT", overtimeMinutes >= config.getOvertimeMinThresholdMins());
        result.put("overtimeAmount", otAmount);
        
        return ResponseEntity.ok(result);
    }

    /**
     * Calculate effective working days (preview/test endpoint).
     */
    @GetMapping("/calculate-days")
    public ResponseEntity<Map<String, Object>> calculateEffectiveDays(
            @RequestParam int actualDaysWorked) {
        
        SalaryOvertimeConfig config = configService.getConfig();
        int effectiveDays = configService.getEffectiveWorkingDays(actualDaysWorked);
        
        Map<String, Object> result = new HashMap<>();
        result.put("actualDaysWorked", actualDaysWorked);
        result.put("thresholdDays", config.getFullMonthSalaryThresholdDays());
        result.put("fullMonthDays", config.getSalaryCalculationDaysInMonth());
        result.put("thresholdEnabled", config.getEnableFullMonthSalaryThreshold());
        result.put("effectiveDays", effectiveDays);
        result.put("getsFullSalary", effectiveDays == config.getSalaryCalculationDaysInMonth());
        
        return ResponseEntity.ok(result);
    }
}
