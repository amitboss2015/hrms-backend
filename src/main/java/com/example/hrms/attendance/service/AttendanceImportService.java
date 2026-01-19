package com.example.hrms.attendance.service;

import com.example.hrms.attendance.dto.AttendanceImportPreview;
import com.example.hrms.attendance.dto.ImportResultDTO;
import com.example.hrms.domain.Employee;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

public interface AttendanceImportService {
    
    /**
     * Preview the attendance file before importing.
     * Parses the file, matches employees, and returns a summary of what will be imported.
     */
    AttendanceImportPreview previewImport(Long orgId, MultipartFile file, int month, int year);
    
    /**
     * Preview the attendance file before importing (tenant-aware version).
     * Parses the file, matches employees, and returns a summary of what will be imported.
     */
    AttendanceImportPreview previewImport(Long orgId, String tenantId, MultipartFile file, int month, int year);
    
    /**
     * Import attendance from biometric Excel file.
     */
    ImportResultDTO importLogsExcel(Long orgId, MultipartFile file, int month, int year, String uploadedBy);
    
    /**
     * Import attendance from biometric Excel file (tenant-aware version).
     */
    ImportResultDTO importLogsExcel(Long orgId, String tenantId, MultipartFile file, int month, int year, String uploadedBy);
    
    /**
     * Preview import with specific device (for multi-device support).
     */
    AttendanceImportPreview previewImportWithDevice(Long orgId, String tenantId, Long deviceId, MultipartFile file, int month, int year);
    
    /**
     * Import attendance with specific device (for multi-device support).
     */
    ImportResultDTO importLogsExcelWithDevice(Long orgId, String tenantId, Long deviceId, MultipartFile file, int month, int year, String uploadedBy);
    
    /**
     * Get employee by ID
     */
    Employee getEmployeeById(Long employeeId);
    
    /**
     * Get employee by tenant and code
     */
    Employee getEmployeeByCode(String tenantId, String empCode);
    
    /**
     * Check if employee has approved leave for a date
     */
    boolean hasApprovedLeave(String tenantId, String empCode, LocalDate date);
    
    /**
     * Get all approved leaves for a period
     */
    java.util.List<com.example.hrms.leave.domain.EmployeeLeave> getApprovedLeavesForPeriod(
        String tenantId, LocalDate from, LocalDate to);
}
