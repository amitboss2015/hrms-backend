package com.example.hrms.admin.controller;

import com.example.hrms.admin.domain.ImportedFile;
import com.example.hrms.admin.domain.ImportedFile.FileType;
import com.example.hrms.admin.service.DataResetService;
import com.example.hrms.admin.service.ImportedFileService;
import com.example.hrms.service.excel.BiometricAssociationExcelService;
import com.example.hrms.service.excel.EmployeeSalaryExcelService;
import com.example.hrms.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for data management operations:
 * - Reset month data before re-import
 * - Employee salary bulk update (template download/upload)
 * - Biometric device association (template download/upload)
 * - Imported file storage and retrieval for audit
 */
@RestController
@RequestMapping("/api/admin/data")
@Slf4j
public class DataManagementController {

    private final DataResetService dataResetService;
    private final EmployeeSalaryExcelService salaryExcelService;
    private final BiometricAssociationExcelService biometricExcelService;
    private final ImportedFileService importedFileService;

    public DataManagementController(
            DataResetService dataResetService,
            EmployeeSalaryExcelService salaryExcelService,
            BiometricAssociationExcelService biometricExcelService,
            ImportedFileService importedFileService) {
        this.dataResetService = dataResetService;
        this.salaryExcelService = salaryExcelService;
        this.biometricExcelService = biometricExcelService;
        this.importedFileService = importedFileService;
    }

    // ============ DATA RESET OPERATIONS ============

    /**
     * Preview what data will be affected by reset.
     * Use this before actual reset to show confirmation dialog.
     */
    @GetMapping("/reset/preview")
    public ResponseEntity<Map<String, Object>> getResetPreview(
            @RequestParam Integer year,
            @RequestParam Integer month) {
        String tenantId = TenantContext.getTenantId();
        log.info("Reset preview requested for tenant={}, year={}, month={}", tenantId, year, month);
        return ResponseEntity.ok(dataResetService.getResetPreview(tenantId, year, month));
    }

    /**
     * Reset all data for a specific month.
     * This clears attendance, payroll, and loan associations for the month.
     * Use before re-importing attendance data.
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetMonthData(
            @RequestParam Integer year,
            @RequestParam Integer month,
            @RequestParam(defaultValue = "false") boolean confirmed) {
        
        String tenantId = TenantContext.getTenantId();
        log.info("Reset requested for tenant={}, year={}, month={}, confirmed={}", 
                tenantId, year, month, confirmed);
        
        if (!confirmed) {
            // Return preview instead of actually resetting
            Map<String, Object> preview = dataResetService.getResetPreview(tenantId, year, month);
            preview.put("requiresConfirmation", true);
            return ResponseEntity.ok(preview);
        }
        
        return ResponseEntity.ok(dataResetService.resetMonthData(tenantId, year, month));
    }

    // ============ SALARY TEMPLATE OPERATIONS ============

    /**
     * Download employee salary template.
     * Contains current basic salary and increment, with columns to update.
     */
    @GetMapping("/salary/template")
    public ResponseEntity<byte[]> downloadSalaryTemplate() throws IOException {
        String tenantId = TenantContext.getTenantId();
        log.info("Salary template download requested for tenant={}", tenantId);
        
        byte[] content = salaryExcelService.generateSalaryTemplate(tenantId);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=employee_salary_template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    /**
     * Upload employee salary updates.
     * Updates basic_salary and increment from the uploaded Excel file.
     */
    @PostMapping("/salary/import")
    public ResponseEntity<Map<String, Object>> importSalaryUpdates(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String uploadedBy) throws IOException {
        
        String tenantId = TenantContext.getTenantId();
        log.info("Salary import requested for tenant={}, filename={}", tenantId, file.getOriginalFilename());
        
        Map<String, Object> result = salaryExcelService.importSalaryUpdates(file);
        
        // Store file for audit
        try {
            importedFileService.storeFile(
                    tenantId, file, FileType.SALARY_UPDATE,
                    null, null, uploadedBy,
                    (Integer) result.get("updatedCount") + (Integer) result.get("skippedCount"),
                    (Integer) result.get("updatedCount"),
                    result.get("errors") != null ? ((List<?>) result.get("errors")).size() : 0,
                    (String) result.get("message")
            );
        } catch (Exception e) {
            log.warn("Failed to store imported file for audit: {}", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    // ============ BIOMETRIC ASSOCIATION OPERATIONS ============

    /**
     * Download biometric association template.
     * Contains current employee-device mappings with columns to update.
     */
    @GetMapping("/biometric/template")
    public ResponseEntity<byte[]> downloadBiometricTemplate() throws IOException {
        String tenantId = TenantContext.getTenantId();
        log.info("Biometric template download requested for tenant={}", tenantId);
        
        byte[] content = biometricExcelService.generateTemplate(tenantId);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=biometric_association_template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    /**
     * Upload biometric association updates.
     * Updates employee-device mappings from the uploaded Excel file.
     */
    @PostMapping("/biometric/import")
    public ResponseEntity<Map<String, Object>> importBiometricAssociations(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String uploadedBy) throws IOException {
        
        String tenantId = TenantContext.getTenantId();
        log.info("Biometric import requested for tenant={}, filename={}", tenantId, file.getOriginalFilename());
        
        Map<String, Object> result = biometricExcelService.importAssociations(file);
        
        // Store file for audit
        try {
            importedFileService.storeFile(
                    tenantId, file, FileType.BIOMETRIC_ASSOCIATION,
                    null, null, uploadedBy,
                    (Integer) result.get("updatedCount") + (Integer) result.get("skippedCount"),
                    (Integer) result.get("updatedCount"),
                    result.get("errors") != null ? ((List<?>) result.get("errors")).size() : 0,
                    (String) result.get("message")
            );
        } catch (Exception e) {
            log.warn("Failed to store imported file for audit: {}", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    // ============ IMPORTED FILE MANAGEMENT ============

    /**
     * Get list of imported files for audit.
     */
    @GetMapping("/files")
    public ResponseEntity<List<Map<String, Object>>> getImportedFiles(
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(defaultValue = "50") int limit) {
        
        String tenantId = TenantContext.getTenantId();
        FileType type = fileType != null ? FileType.valueOf(fileType.toUpperCase()) : null;
        
        return ResponseEntity.ok(importedFileService.getImportedFiles(tenantId, type, year, month, limit));
    }

    /**
     * Download a previously imported file.
     */
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<byte[]> downloadImportedFile(@PathVariable Long fileId) {
        String tenantId = TenantContext.getTenantId();
        
        Optional<ImportedFile> fileOpt = importedFileService.getFileForDownload(fileId, tenantId);
        
        if (fileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        ImportedFile file = fileOpt.get();
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getOriginalFileName())
                .contentType(MediaType.parseMediaType(
                        file.getContentType() != null ? file.getContentType() : "application/octet-stream"))
                .body(file.getFileContent());
    }

    /**
     * Get import statistics for dashboard.
     */
    @GetMapping("/files/stats")
    public ResponseEntity<Map<String, Object>> getImportStatistics() {
        String tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(importedFileService.getImportStatistics(tenantId));
    }

    /**
     * Delete old imported files (cleanup).
     */
    @DeleteMapping("/files/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupOldFiles(
            @RequestParam(defaultValue = "90") int daysOld) {
        
        String tenantId = TenantContext.getTenantId();
        int deleted = importedFileService.deleteOldFiles(tenantId, daysOld);
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "deleted", deleted,
                "message", "Deleted " + deleted + " files older than " + daysOld + " days"
        ));
    }
}
