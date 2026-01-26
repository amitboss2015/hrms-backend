package com.example.hrms.attendance.dto;

import lombok.*;
import java.util.List;

/**
 * Response wrapper for attendance logs with summary totals.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceLogsResponse {
    private List<DailyPunchLogDTO> logs;
    private int totalOtDeductionMins;    // Total OT deduction in minutes (positive = OT earned, negative = deduction)
    private int totalLateDeductionMins;  // Total Late deduction in minutes
    private int totalEarlyDeductionMins; // Total Early checkout deduction in minutes
    private int paidLeaveDays;           // Total paid leave days in the month
    private int unpaidLeaveDays;         // Total unpaid leave days in the month
}
