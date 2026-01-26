package com.example.hrms.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class MonthlySummaryDTO {
    private String empCode;
    private String empName;
    private String name; // Alias for empName (backward compatibility)
    private String department;
    private String designation;
    private String shiftName;
    
    // Attendance counts
    private int totalWorkingDays;
    private int present;
    private int absent;
    private int leaveDays;
    private int leave; // Alias for leaveDays (backward compatibility)
    private int paidLeaveDays;    // Paid leave days (counts as present)
    private int unpaidLeaveDays;   // Unpaid leave days (counts as absent)
    private int halfDays;
    private int weeklyOff;
    private int holidays;
    
    // Late tracking
    private int lateDays;
    private int earlyOutDays;
    private int totalLateMinutes;       // Total late minutes in the month
    private int totalEarlyOutMinutes;   // Total early out minutes in the month
    private int lateDeductionDays;      // 3 lates = 1 absent
    
    // OT tracking
    private int overtimeDays;
    private int otMinutes;
    private BigDecimal overtimeHours;
    
    // Work hours
    private int totalWorkMinutes;
    private BigDecimal totalWorkHours;
    private BigDecimal avgWorkHoursPerDay;
    
    // Dual shift tracking
    private int dualShiftDays;
    
    // Salary info (optional, for integrated reports)
    private BigDecimal basicSalary;
    private BigDecimal finalPayment;
    private BigDecimal workingDayAmount;
    private BigDecimal grossSalary;
    private BigDecimal netSalary;
    
    // Status summary
    private String payrollStatus; // DRAFT, APPROVED, PAID
}
