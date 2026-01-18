package com.example.hrms.attendance.service;

import com.example.hrms.attendance.dto.AttendanceImportPreview;
import com.example.hrms.attendance.dto.ImportResultDTO;
import org.springframework.web.multipart.MultipartFile;

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
}
