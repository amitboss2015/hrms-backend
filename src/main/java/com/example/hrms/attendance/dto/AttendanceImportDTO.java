package com.example.hrms.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO for importing attendance records from Excel template.
 * Each row represents one attendance entry for an employee on a specific date.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceImportDTO {
    
    /**
     * Row number in the Excel file (for error reporting)
     */
    private int rowNumber;
    
    /**
     * Employee code - must match an existing employee in the system
     */
    @NotBlank(message = "Employee Code is required")
    private String empCode;
    
    /**
     * Date of attendance in format DD/MM/YYYY
     */
    @NotNull(message = "Date is required")
    private LocalDate date;
    
    /**
     * Check-in time in format HH:MM (24-hour)
     */
    private LocalTime inTime;
    
    /**
     * Check-out time in format HH:MM (24-hour)
     */
    private LocalTime outTime;
    
    /**
     * Shift code (optional) - if not provided, system will use employee's assigned shift
     */
    private String shiftCode;
    
    /**
     * Status override (optional) - PRESENT, ABSENT, HALF_DAY, LEAVE, HOLIDAY, WEEKLY_OFF
     * If not provided, system will calculate based on times
     */
    private String status;
    
    /**
     * Remarks/notes for this attendance entry
     */
    private String remarks;
    
    /**
     * Check if this is a valid entry (has at least empCode and date)
     */
    public boolean isValid() {
        return empCode != null && !empCode.trim().isEmpty() && date != null;
    }
    
    /**
     * Check if this entry has punch times
     */
    public boolean hasPunchTimes() {
        return inTime != null || outTime != null;
    }
}
