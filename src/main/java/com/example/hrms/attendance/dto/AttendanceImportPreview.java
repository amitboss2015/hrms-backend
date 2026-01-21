package com.example.hrms.attendance.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * Preview DTO for attendance import.
 * Shows what will be imported before final confirmation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceImportPreview {
    
    private boolean valid;
    private String message;
    
    // File details
    private String fileName;
    private String detectedFormat; // e.g., "BIOMETRIC_LOGS", "STANDARD_TEMPLATE"
    private String detectedPeriod; // e.g., "July 2025"
    private int detectedMonth;
    private int detectedYear;
    
    // Employee matching
    private int totalEmployeesInFile;
    private int matchedEmployees;
    private int unmatchedEmployees;
    private List<EmployeeMatch> employeeMatches;
    
    // Punch statistics
    private int totalPunchRecords;
    private int daysWithData;
    private Map<Integer, Integer> punchesPerDay; // Day number -> punch count
    
    // Sample data for preview
    private List<SampleRow> sampleRows;
    
    // Existing batch info (if duplicate)
    private boolean duplicateExists;
    private Long existingBatchId;
    private String existingBatchDate;
    
    // Warnings for validation (shift assignment, etc.)
    private List<String> warnings;
    private int employeesWithoutShift;
    
    /**
     * Employee match info showing which employees were found/not found
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EmployeeMatch {
        private String empCodeInFile;
        private String nameInFile;
        private boolean matched;
        private Long matchedEmployeeId;
        private String matchedEmpCode;
        private String matchedName;
        private int punchCount;
        private boolean hasShiftAssignment; // Shift assigned for the import period
        private String shiftCode; // Assigned shift code (if any)
        private String matchType; // How the match was made: EXACT, DEVICE_MAPPING, INTELLIGENT, null if not matched
    }
    
    /**
     * Sample row for preview showing first few days of data
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SampleRow {
        private String empCode;
        private String name;
        private List<DayPunches> days; // First 7 days only
    }
    
    /**
     * Punches for a single day
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DayPunches {
        private int dayOfMonth;
        private List<String> punches; // e.g., ["08:59", "17:39"]
        private String status; // e.g., "PRESENT", "ABSENT", "ONLY_IN"
    }
    
    // Factory methods
    public static AttendanceImportPreview error(String message) {
        return AttendanceImportPreview.builder()
                .valid(false)
                .message(message)
                .build();
    }
    
    public static AttendanceImportPreview duplicate(Long batchId, String batchDate, int month, int year) {
        return AttendanceImportPreview.builder()
                .valid(false)
                .duplicateExists(true)
                .existingBatchId(batchId)
                .existingBatchDate(batchDate)
                .detectedMonth(month)
                .detectedYear(year)
                .message("Attendance for this period has already been uploaded. Delete the existing batch first to re-upload.")
                .build();
    }
}
