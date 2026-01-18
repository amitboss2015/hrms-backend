package com.example.hrms.attendance.controller;

import com.example.hrms.attendance.domain.ImportBatch;
import com.example.hrms.attendance.domain.ImportError;
import com.example.hrms.attendance.dto.AttendanceImportPreview;
import com.example.hrms.attendance.dto.ImportResultDTO;
import com.example.hrms.attendance.repo.AttendanceDayRepository;
import com.example.hrms.attendance.repo.AttendancePunchRepository;
import com.example.hrms.attendance.repo.AttendanceSessionRepository;
import com.example.hrms.attendance.repo.ImportBatchRepository;
import com.example.hrms.attendance.repo.ImportErrorRepository;
import com.example.hrms.attendance.service.AttendanceEngine;
import com.example.hrms.attendance.service.AttendanceExcelService;
import com.example.hrms.attendance.service.AttendanceImportService;
import com.example.hrms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/attendance")
@CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.DELETE, RequestMethod.OPTIONS})
@RequiredArgsConstructor
@Slf4j
public class AttendanceImportController {

    private final AttendanceImportService importService;
    private final AttendanceEngine engine;
    private final ImportBatchRepository batchRepo;
    private final ImportErrorRepository errorRepo;
    private final AttendancePunchRepository punchRepo;
    private final AttendanceSessionRepository sessionRepo;
    private final AttendanceDayRepository dayRepo;
    private final AttendanceExcelService attendanceExcelService;

    /**
     * Preview attendance import before confirming.
     * Parses the file and returns summary of what will be imported.
     */
    @PostMapping("/import/preview")
    public ResponseEntity<AttendanceImportPreview> previewImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam("month") int month,
            @RequestParam("year") int year) {
        
        String tenantId = TenantContext.getTenantId();
        log.info("Preview import request: tenant={}, month={}, year={}, file={}", tenantId, month, year, file.getOriginalFilename());
        
        // Use tenant ID hash as org ID for multi-tenancy
        Long orgId = getOrgIdFromTenant(tenantId);
        AttendanceImportPreview preview = importService.previewImport(orgId, tenantId, file, month, year);
        
        return ResponseEntity.ok(preview);
    }

    /**
     * Import attendance from biometric Excel file.
     * The file should have a "Logs" sheet (Sheet 2) with day-wise punch times.
     */
    @PostMapping("/import")
    public ResponseEntity<ImportResultDTO> importFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("month") int month,
            @RequestParam("year") int year,
            @RequestHeader(value = "X-User", required = false) String uploadedBy) {

        String tenantId = TenantContext.getTenantId();
        // Use tenant ID hash as org ID for multi-tenancy
        Long orgId = getOrgIdFromTenant(tenantId);
        log.info("Import request: tenant={}, orgId={}, month={}, year={}", tenantId, orgId, month, year);
        
        var result = importService.importLogsExcel(orgId, tenantId, file, month, year, uploadedBy == null ? "admin" : uploadedBy);

        // If not a duplicate, rebuild the org month
        if (!result.isDuplicate()) {
            engine.rebuildOrgMonth(orgId, YearMonth.of(year, month));
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Get list of all import batches for the organization.
     */
    @GetMapping("/import/batches")
    public ResponseEntity<List<Map<String, Object>>> listBatches() {

        String tenantId = TenantContext.getTenantId();
        Long orgId = getOrgIdFromTenant(tenantId);
        List<ImportBatch> batches = batchRepo.findByOrgIdOrderByUploadedAtDesc(orgId);

        List<Map<String, Object>> result = batches.stream().map(b -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", b.getId());
            map.put("month", b.getMonth());
            map.put("year", b.getYear());
            map.put("uploadedBy", b.getUploadedBy());
            map.put("uploadedAt", b.getUploadedAt());
            map.put("totalRows", b.getTotalRows());
            map.put("successRows", b.getSuccessRows());
            map.put("errorRows", b.getErrorRows());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Check if attendance for a specific month/year has already been uploaded.
     */
    @GetMapping("/import/check")
    public ResponseEntity<Map<String, Object>> checkDuplicate(
            @RequestParam("month") int month,
            @RequestParam("year") int year) {

        String tenantId = TenantContext.getTenantId();
        Long orgId = getOrgIdFromTenant(tenantId);
        List<ImportBatch> existing = batchRepo.findByOrgIdAndMonthAndYear(orgId, month, year);

        Map<String, Object> result = new HashMap<>();
        result.put("exists", !existing.isEmpty());
        if (!existing.isEmpty()) {
            ImportBatch b = existing.get(0);
            result.put("batchId", b.getId());
            result.put("uploadedAt", b.getUploadedAt());
            result.put("uploadedBy", b.getUploadedBy());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Delete an import batch and all associated data.
     * This allows re-uploading attendance for the same month/year.
     */
    @DeleteMapping("/import/batches/{batchId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteBatch(
            @PathVariable Long batchId) {

        String tenantId = TenantContext.getTenantId();
        Long orgId = getOrgIdFromTenant(tenantId);

        ImportBatch batch = batchRepo.findById(batchId).orElse(null);
        if (batch == null || !orgId.equals(batch.getOrgId())) {
            return ResponseEntity.notFound().build();
        }

        // Delete associated data
        punchRepo.deleteByImportBatchId(batchId);
        errorRepo.deleteByBatchId(batchId);

        // Also delete computed sessions and days for the month
        // (They will be regenerated on next import)
        YearMonth ym = YearMonth.of(batch.getYear(), batch.getMonth());
        sessionRepo.deleteByOrgIdAndWorkDateBetween(orgId, ym.atDay(1), ym.atEndOfMonth());
        dayRepo.deleteByOrgIdAndWorkDateBetween(orgId, ym.atDay(1), ym.atEndOfMonth());

        // Delete the batch itself
        batchRepo.delete(batch);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Batch " + batchId + " and all associated data deleted successfully.");
        return ResponseEntity.ok(result);
    }

    /**
     * Download errors for a specific batch as CSV.
     */
    @GetMapping("/import/batches/{batchId}/errors.csv")
    public ResponseEntity<String> downloadErrors(@PathVariable Long batchId) {
        List<ImportError> errors = errorRepo.findByBatchId(batchId);

        StringBuilder csv = new StringBuilder();
        csv.append("Row,Column,Error Code,Error Message,Raw Payload\n");

        for (ImportError e : errors) {
            csv.append(e.getRowNum()).append(",");
            csv.append(escapeCSV(e.getColumnName())).append(",");
            csv.append(escapeCSV(e.getErrorCode())).append(",");
            csv.append(escapeCSV(e.getErrorMessage())).append(",");
            csv.append(escapeCSV(e.getRawPayloadJson())).append("\n");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=import_errors_" + batchId + ".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString());
    }

    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Recalculate attendance for a specific month/year.
     * This rebuilds all attendance sessions and day rollups from raw punches.
     */
    @PostMapping("/recalculate")
    @Transactional
    public ResponseEntity<Map<String, Object>> recalculateAttendance(
            @RequestParam("month") int month,
            @RequestParam("year") int year) {

        String tenantId = TenantContext.getTenantId();
        Long orgId = getOrgIdFromTenant(tenantId);
        YearMonth ym = YearMonth.of(year, month);

        // Delete existing computed data
        sessionRepo.deleteByOrgIdAndWorkDateBetween(orgId, ym.atDay(1), ym.atEndOfMonth());
        dayRepo.deleteByOrgIdAndWorkDateBetween(orgId, ym.atDay(1), ym.atEndOfMonth());

        // Find all employees with punches in this month
        java.time.Instant fromUtc = ym.atDay(1).atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toInstant();
        java.time.Instant toUtc = ym.atEndOfMonth().plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toInstant();
        List<Long> empIds = punchRepo.findDistinctEmployeeIdsForRange(orgId, fromUtc, toUtc);

        // Rebuild for each employee
        int rebuilt = 0;
        for (Long empId : empIds) {
            engine.rebuildEmployeeMonth(orgId, empId, ym);
            rebuilt++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Recalculated attendance for " + rebuilt + " employees for " + ym);
        result.put("employeesProcessed", rebuilt);
        return ResponseEntity.ok(result);
    }

    // ==================== TEMPLATE DOWNLOAD ====================

    /**
     * Download the attendance import template for a specific month/year.
     * The template is pre-filled with employee list and has the same format as biometric exports.
     */
    @GetMapping("/template/download")
    public ResponseEntity<byte[]> downloadAttendanceTemplate(
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year) {
        try {
            String tenantId = TenantContext.getTenantId();
            
            // Default to current month/year if not provided
            java.time.YearMonth ym = (month != null && year != null) 
                ? java.time.YearMonth.of(year, month)
                : java.time.YearMonth.now();
            
            byte[] template = attendanceExcelService.generateTemplate(tenantId, ym);
            
            String filename = String.format("attendance_template_%d_%02d.xlsx", ym.getYear(), ym.getMonthValue());
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(template);
        } catch (Exception e) {
            log.error("Error generating attendance template", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Convert tenant ID (String) to org ID (Long) for multi-tenancy.
     * Uses hashCode to generate a consistent unique ID per tenant.
     */
    private Long getOrgIdFromTenant(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) {
            return 1L; // Default for backwards compatibility
        }
        // Use absolute value of hashCode to ensure positive number
        // Add a base offset to avoid collision with legacy org IDs
        return (long) Math.abs(tenantId.hashCode()) + 10000L;
    }
}
