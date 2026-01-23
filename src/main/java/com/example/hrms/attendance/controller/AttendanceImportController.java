package com.example.hrms.attendance.controller;

import com.example.hrms.admin.domain.ImportedFile;
import com.example.hrms.admin.domain.ImportedFile.FileType;
import com.example.hrms.admin.service.ImportedFileService;
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

import java.io.IOException;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/attendance")
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
    private final ImportedFileService importedFileService;
    private final com.example.hrms.payroll.repo.PayrollRepository payrollRepo;
    private final com.example.hrms.loan.service.LoanService loanService;
    
    // Maximum number of attendance Excel files to keep per device/month/year
    private static final int MAX_FILES_PER_MONTH = 3;

    /**
     * Preview attendance import before confirming.
     * Parses the file and returns summary of what will be imported.
     * 
     * @param deviceId Optional device ID for multi-device support. If provided, uses device mappings for employee resolution.
     */
    @PostMapping("/import/preview")
    public ResponseEntity<AttendanceImportPreview> previewImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam("month") int month,
            @RequestParam("year") int year,
            @RequestParam(value = "deviceId", required = false) Long deviceId) {
        
        String tenantId = TenantContext.getTenantId();
        log.info("Preview import request: tenant={}, device={}, month={}, year={}, file={}", 
            tenantId, deviceId, month, year, file.getOriginalFilename());
        
        // Use tenant ID hash as org ID for multi-tenancy
        Long orgId = getOrgIdFromTenant(tenantId);
        
        AttendanceImportPreview preview;
        if (deviceId != null) {
            // Use device-aware preview
            preview = importService.previewImportWithDevice(orgId, tenantId, deviceId, file, month, year);
        } else {
            // Use standard preview (backward compatible)
            preview = importService.previewImport(orgId, tenantId, file, month, year);
        }
        
        return ResponseEntity.ok(preview);
    }

    /**
     * Import attendance from biometric Excel file.
     * The file should have a "Logs" sheet (Sheet 2) with day-wise punch times.
     * 
     * @param deviceId Optional device ID for multi-device support. If not provided, uses direct emp_code matching.
     */
    @PostMapping("/import")
    public ResponseEntity<ImportResultDTO> importFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("month") int month,
            @RequestParam("year") int year,
            @RequestParam(value = "deviceId", required = false) Long deviceId,
            @RequestHeader(value = "X-User", required = false) String uploadedBy) {

        String tenantId = TenantContext.getTenantId();
        // Use tenant ID hash as org ID for multi-tenancy
        Long orgId = getOrgIdFromTenant(tenantId);
        log.info("Import request: tenant={}, orgId={}, device={}, month={}, year={}", tenantId, orgId, deviceId, month, year);
        
        ImportResultDTO result;
        if (deviceId != null) {
            // Use device-aware import
            result = importService.importLogsExcelWithDevice(orgId, tenantId, deviceId, file, month, year, 
                uploadedBy == null ? "admin" : uploadedBy);
        } else {
            // Use standard import (backward compatible)
            result = importService.importLogsExcel(orgId, tenantId, file, month, year, 
                uploadedBy == null ? "admin" : uploadedBy);
        }

        // If not a duplicate, rebuild the org month
        if (!result.isDuplicate()) {
            engine.rebuildOrgMonth(orgId, YearMonth.of(year, month));
        }
        
        // Store the Excel file for audit (keep last 3 per month/year/device)
        try {
            storeAttendanceFile(tenantId, file, year, month, deviceId, uploadedBy, result);
        } catch (Exception e) {
            log.warn("Failed to store attendance file for audit: {}", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }
    
    /**
     * Store attendance Excel file for audit purposes.
     * Keeps only the last MAX_FILES_PER_MONTH files per month/year.
     */
    private void storeAttendanceFile(String tenantId, MultipartFile file, int year, int month, 
                                      Long deviceId, String uploadedBy, ImportResultDTO result) {
        try {
            // Build processing notes
            String notes = String.format("Device: %s, Success: %d, Failed: %d", 
                    deviceId != null ? deviceId.toString() : "N/A",
                    result.getSuccess(), 
                    result.getFailed());
            
            // Store the file
            importedFileService.storeFile(
                    tenantId, file, FileType.ATTENDANCE_LOG,
                    year, month, uploadedBy,
                    result.getTotal(),
                    result.getSuccess(),
                    result.getFailed(),
                    notes
            );
            
            // Cleanup old files - keep only last MAX_FILES_PER_MONTH
            cleanupOldAttendanceFiles(tenantId, year, month);
            
            log.info("Stored attendance file for audit: {} ({}/{})", file.getOriginalFilename(), month, year);
        } catch (Exception e) {
            log.error("Error storing attendance file: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Remove old attendance files, keeping only the last MAX_FILES_PER_MONTH.
     */
    private void cleanupOldAttendanceFiles(String tenantId, int year, int month) {
        try {
            List<Map<String, Object>> files = importedFileService.getImportedFiles(
                    tenantId, FileType.ATTENDANCE_LOG, year, month, 100);
            
            if (files.size() > MAX_FILES_PER_MONTH) {
                // Files are ordered by uploadedAt DESC, so skip first MAX_FILES_PER_MONTH and delete rest
                for (int i = MAX_FILES_PER_MONTH; i < files.size(); i++) {
                    Long fileId = ((Number) files.get(i).get("id")).longValue();
                    importedFileService.deleteFile(tenantId, fileId);
                    log.info("Deleted old attendance file: {} (keeping only last {})", fileId, MAX_FILES_PER_MONTH);
                }
            }
        } catch (Exception e) {
            log.warn("Error cleaning up old attendance files: {}", e.getMessage());
        }
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

        // Delete payroll for the month (so it can be regenerated with new attendance)
        int payrollDeleted = payrollRepo.deleteByTenantIdAndYearAndMonth(tenantId, batch.getYear(), batch.getMonth());
        log.info("Deleted {} payroll records for {}/{}", payrollDeleted, batch.getMonth(), batch.getYear());
        
        // Clear loan deduction associations for this month (makes loans available for re-deduction)
        loanService.clearOneTimeLoanAssociationsForMonth(tenantId, batch.getMonth(), batch.getYear());
        log.info("Cleared loan associations for {}/{}", batch.getMonth(), batch.getYear());

        // Delete the batch itself
        batchRepo.delete(batch);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Batch " + batchId + " and all associated data (including payroll) deleted successfully.");
        result.put("payrollDeleted", payrollDeleted);
        return ResponseEntity.ok(result);
    }

    /**
     * Get list of stored attendance Excel files for a specific month/year.
     * These are the original files that were imported.
     */
    @GetMapping("/import/files")
    public ResponseEntity<List<Map<String, Object>>> getImportedFiles(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        
        String tenantId = TenantContext.getTenantId();
        List<Map<String, Object>> files = importedFileService.getImportedFiles(
                tenantId, FileType.ATTENDANCE_LOG, year, month, 50);
        
        return ResponseEntity.ok(files);
    }
    
    /**
     * Download a previously imported attendance Excel file.
     */
    @GetMapping("/import/files/{fileId}/download")
    public ResponseEntity<byte[]> downloadImportedFile(@PathVariable Long fileId) {
        String tenantId = TenantContext.getTenantId();
        
        var fileOpt = importedFileService.getFileForDownload(fileId, tenantId);
        
        if (fileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        ImportedFile file = fileOpt.get();
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getOriginalFileName())
                .contentType(MediaType.parseMediaType(
                        file.getContentType() != null ? file.getContentType() : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.getFileContent());
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
     * Sync attendance with leave records for a specific month/year.
     * This is a lightweight operation that:
     * 1. Updates ABSENT days to LEAVE if leave records exist
     * 2. Creates LEAVE attendance records for days with no attendance but approved leaves
     */
    @PostMapping("/import/recalculate")
    @Transactional
    public ResponseEntity<Map<String, Object>> recalculateAttendance(
            @RequestParam("month") int month,
            @RequestParam("year") int year) {

        String tenantId = TenantContext.getTenantId();
        Long orgId = getOrgIdFromTenant(tenantId);
        YearMonth ym = YearMonth.of(year, month);
        java.time.LocalDate from = ym.atDay(1);
        java.time.LocalDate to = ym.atEndOfMonth();

        log.info("Syncing attendance with leaves for tenant={}, month={}/{}", tenantId, month, year);

        int updated = 0;
        int created = 0;

        // Step 1: Update existing ABSENT days to LEAVE if leave exists
        List<com.example.hrms.attendance.domain.AttendanceDay> absentDays = 
            dayRepo.findByTenantIdAndWorkDateBetweenAndStatus(tenantId, from, to, "ABSENT");
        
        for (var day : absentDays) {
            var employee = importService.getEmployeeById(day.getEmployeeId());
            if (employee == null) continue;
            
            String empCode = employee.getEmpCode();
            if (empCode == null) continue;
            
            boolean hasLeave = importService.hasApprovedLeave(tenantId, empCode, day.getWorkDate());
            if (hasLeave) {
                day.setStatus("LEAVE");
                dayRepo.save(day);
                updated++;
            }
        }

        // Step 2: Find approved leaves in this period and create LEAVE records if no attendance exists
        List<com.example.hrms.leave.domain.EmployeeLeave> leaves = 
            importService.getApprovedLeavesForPeriod(tenantId, from, to);
        
        for (var leave : leaves) {
            // For each day in the leave period
            java.time.LocalDate leaveDate = leave.getStartDate();
            while (!leaveDate.isAfter(leave.getEndDate()) && !leaveDate.isAfter(to)) {
                if (!leaveDate.isBefore(from)) {
                    // Get employee ID
                    var employee = importService.getEmployeeByCode(tenantId, leave.getEmpId());
                    if (employee != null) {
                        // Check if attendance record exists for this date
                        boolean exists = dayRepo.existsByTenantIdAndEmployeeIdAndWorkDate(tenantId, employee.getId(), leaveDate);
                        if (!exists) {
                            // Create LEAVE record
                            com.example.hrms.attendance.domain.AttendanceDay leaveDay = 
                                com.example.hrms.attendance.domain.AttendanceDay.builder()
                                    .tenantId(tenantId)
                                    .orgId(orgId)
                                    .employeeId(employee.getId())
                                    .workDate(leaveDate)
                                    .status("LEAVE")
                                    .totalWorkMin(0)
                                    .punchCount(0)
                                    .build();
                            dayRepo.save(leaveDay);
                            created++;
                        }
                    }
                }
                leaveDate = leaveDate.plusDays(1);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Updated " + updated + " ABSENT→LEAVE, Created " + created + " new LEAVE records for " + ym);
        result.put("daysUpdated", updated);
        result.put("daysCreated", created);
        return ResponseEntity.ok(result);
    }

    /**
     * Full recalculation of attendance from punches.
     * This re-processes all punches through the attendance engine,
     * recalculating working hours, overtime, etc.
     */
    @PostMapping("/import/rebuild")
    @Transactional
    public ResponseEntity<Map<String, Object>> rebuildAttendance(
            @RequestParam("month") int month,
            @RequestParam("year") int year) {

        String tenantId = TenantContext.getTenantId();
        Long orgId = getOrgIdFromTenant(tenantId);
        YearMonth ym = YearMonth.of(year, month);

        log.info("Full rebuild of attendance for tenant={}, month={}/{}", tenantId, month, year);

        try {
            engine.rebuildOrgMonth(orgId, ym);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Successfully rebuilt attendance for " + ym);
            result.put("month", month);
            result.put("year", year);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error rebuilding attendance: {}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    // ==================== TEMPLATE & FORMAT HELP ====================

    /**
     * Get attendance template information with format instructions.
     * Returns template details and guidance for users.
     */
    @GetMapping("/template/info")
    public ResponseEntity<Map<String, Object>> getTemplateInfo() {
        Map<String, Object> info = new HashMap<>();
        
        info.put("title", "Attendance Import Template");
        info.put("description", "Download the sample attendance template and replace with your data in the exact same format.");
        
        // Format instructions
        Map<String, Object> format = new HashMap<>();
        format.put("fileType", "Excel (.xlsx)");
        format.put("sheetName", "List of Logs");
        format.put("row1", "Title: 'List of Logs'");
        format.put("row3", "Period information (Month/Year)");
        format.put("row4", "Day numbers (1, 2, 3, ... 31)");
        format.put("dataRows", "For each employee: Employee Code/Name, followed by punch times");
        format.put("punchFormat", "HH:mm or HH:mm:ss (24-hour format) - Multiple punches separated by comma");
        info.put("format", format);
        
        // Sample data
        info.put("sampleData", Map.of(
            "employee", "EMP001 - John Doe",
            "day1", "09:05, 13:00, 14:00, 18:30",
            "day2", "08:58, 18:15",
            "explanation", "Each cell contains IN/OUT punch times for that day"
        ));
        
        // Help message
        info.put("helpMessage", "If your biometric machine exports data in a different format, " +
                "please contact our support team. Share your attendance logs format and we will help you.");
        info.put("supportEmail", "support@hrms.com");
        info.put("downloadUrl", "/api/attendance/template/download");
        info.put("sampleDownloadUrl", "/api/attendance/template/sample");
        
        return ResponseEntity.ok(info);
    }

    /**
     * Download a SAMPLE attendance template with real example data.
     * This is a STATIC file from actual biometric machine export.
     * Shows users the exact format they need to follow.
     */
    @GetMapping("/template/sample")
    public ResponseEntity<byte[]> downloadSampleTemplate(
            @RequestParam(value = "format", defaultValue = "xlsx") String format) {
        try {
            // Load static sample file from resources
            org.springframework.core.io.ClassPathResource resource = 
                new org.springframework.core.io.ClassPathResource("templates/attendance_sample.csv");
            
            byte[] csvData = resource.getInputStream().readAllBytes();
            
            if ("csv".equalsIgnoreCase(format)) {
                // Return CSV directly
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=attendance_sample_template.csv")
                        .contentType(MediaType.parseMediaType("text/csv"))
                        .body(csvData);
            } else {
                // Convert CSV to Excel format
                byte[] excelData = convertCsvToExcel(csvData);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=attendance_sample_template.xlsx")
                        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .body(excelData);
            }
        } catch (Exception e) {
            log.error("Error loading sample template", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Convert CSV data to Excel format.
     * Handles CSV with multiline quoted values properly.
     */
    private byte[] convertCsvToExcel(byte[] csvData) throws IOException {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("List of Logs");
            
            // Create styles
            org.apache.poi.ss.usermodel.CellStyle titleStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);
            
            org.apache.poi.ss.usermodel.CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            dataStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
            dataStyle.setWrapText(true);
            
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_GREEN.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            
            // Parse CSV properly handling multiline quoted values
            String csvContent = new String(csvData, java.nio.charset.StandardCharsets.UTF_8);
            java.util.List<java.util.List<String>> rows = parseMultilineCsv(csvContent);
            
            int rowNum = 0;
            for (java.util.List<String> rowData : rows) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum);
                boolean hasTimeData = false;
                
                for (int col = 0; col < rowData.size(); col++) {
                    org.apache.poi.ss.usermodel.Cell cell = row.createCell(col);
                    String cellValue = rowData.get(col);
                    
                    cell.setCellValue(cellValue);
                    
                    // Apply styles based on content
                    if (cellValue.contains("No :") || cellValue.contains("Name :") || 
                        cellValue.contains("Dept :") || cellValue.contains("Period") ||
                        cellValue.contains("List of Logs")) {
                        cell.setCellStyle(titleStyle);
                    } else if (cellValue.matches("\\d+") && cellValue.length() <= 2) {
                        cell.setCellStyle(headerStyle);
                    } else if (cellValue.contains(":") && cellValue.contains("\n")) {
                        cell.setCellStyle(dataStyle);
                        hasTimeData = true;
                    }
                }
                
                if (hasTimeData) {
                    row.setHeightInPoints(35);
                }
                rowNum++;
            }
            
            // Set column widths
            for (int col = 0; col <= 31; col++) {
                sheet.setColumnWidth(col, 10 * 256);
            }
            
            // Add Instructions sheet
            org.apache.poi.ss.usermodel.Sheet instructionSheet = workbook.createSheet("Instructions");
            createInstructionSheet(instructionSheet, workbook);
            
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
    
    /**
     * Parse CSV content that may contain multiline quoted values.
     * Returns a list of rows, where each row is a list of cell values.
     */
    private java.util.List<java.util.List<String>> parseMultilineCsv(String csvContent) {
        java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
        java.util.List<String> currentRow = new java.util.ArrayList<>();
        StringBuilder currentCell = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < csvContent.length(); i++) {
            char c = csvContent.charAt(i);
            char nextChar = (i + 1 < csvContent.length()) ? csvContent.charAt(i + 1) : '\0';
            
            if (c == '"') {
                if (inQuotes && nextChar == '"') {
                    // Escaped quote
                    currentCell.append('"');
                    i++; // Skip next quote
                } else {
                    // Toggle quote state
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                // End of cell
                currentRow.add(currentCell.toString());
                currentCell = new StringBuilder();
            } else if (c == '\n' && !inQuotes) {
                // End of row
                currentRow.add(currentCell.toString());
                currentCell = new StringBuilder();
                rows.add(currentRow);
                currentRow = new java.util.ArrayList<>();
            } else if (c == '\r' && !inQuotes) {
                // Skip carriage return
            } else {
                currentCell.append(c);
            }
        }
        
        // Add last cell and row
        if (currentCell.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(currentCell.toString());
            rows.add(currentRow);
        }
        
        return rows;
    }
    
    /**
     * Create instruction sheet with format help message.
     */
    private void createInstructionSheet(org.apache.poi.ss.usermodel.Sheet sheet, org.apache.poi.xssf.usermodel.XSSFWorkbook workbook) {
        org.apache.poi.ss.usermodel.CellStyle titleStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        titleStyle.setFont(titleFont);
        
        org.apache.poi.ss.usermodel.CellStyle normalStyle = workbook.createCellStyle();
        normalStyle.setWrapText(true);
        
        int rowNum = 0;
        
        // English Instructions
        org.apache.poi.ss.usermodel.Row row1 = sheet.createRow(rowNum++);
        row1.createCell(0).setCellValue("📋 ATTENDANCE IMPORT INSTRUCTIONS");
        row1.getCell(0).setCellStyle(titleStyle);
        
        rowNum++;
        sheet.createRow(rowNum++).createCell(0).setCellValue("🇬🇧 ENGLISH:");
        sheet.createRow(rowNum++).createCell(0).setCellValue("1. This is the sample format from a biometric machine.");
        sheet.createRow(rowNum++).createCell(0).setCellValue("2. Your attendance file should have the same format.");
        sheet.createRow(rowNum++).createCell(0).setCellValue("3. Each employee has 3 rows: Info row, Punch data row, Day numbers row.");
        sheet.createRow(rowNum++).createCell(0).setCellValue("4. Punch times format: IN time on first line, OUT time on second line (e.g., 09:00\\n17:30)");
        sheet.createRow(rowNum++).createCell(0).setCellValue("5. Empty cells = absent or no punch recorded.");
        sheet.createRow(rowNum++).createCell(0).setCellValue("6. If your biometric machine exports differently, please contact admin.");
        
        rowNum += 2;
        sheet.createRow(rowNum++).createCell(0).setCellValue("🇮🇳 हिंदी:");
        sheet.createRow(rowNum++).createCell(0).setCellValue("1. यह बायोमेट्रिक मशीन का सैंपल फॉर्मेट है।");
        sheet.createRow(rowNum++).createCell(0).setCellValue("2. आपकी अटेंडेंस फाइल का फॉर्मेट इसी तरह होना चाहिए।");
        sheet.createRow(rowNum++).createCell(0).setCellValue("3. हर कर्मचारी के 3 rows हैं: Info row, Punch data row, Day numbers row।");
        sheet.createRow(rowNum++).createCell(0).setCellValue("4. पंच टाइम फॉर्मेट: पहली लाइन में IN टाइम, दूसरी लाइन में OUT टाइम (जैसे 09:00\\n17:30)");
        sheet.createRow(rowNum++).createCell(0).setCellValue("5. खाली सेल = अनुपस्थित या कोई पंच रिकॉर्ड नहीं।");
        sheet.createRow(rowNum++).createCell(0).setCellValue("6. अगर आपकी बायोमेट्रिक मशीन अलग फॉर्मेट में एक्सपोर्ट करती है, तो कृपया एडमिन से संपर्क करें।");
        
        rowNum += 2;
        sheet.createRow(rowNum++).createCell(0).setCellValue("⚠️ IMPORTANT / महत्वपूर्ण:");
        sheet.createRow(rowNum++).createCell(0).setCellValue("Every biometric device has its own format. If different, contact: support@chandrahr.in");
        sheet.createRow(rowNum++).createCell(0).setCellValue("हर बायोमेट्रिक डिवाइस का अपना फॉर्मेट होता है। अगर अलग हो तो संपर्क करें: support@chandrahr.in");
        
        sheet.setColumnWidth(0, 100 * 256);
    }

    /**
     * Request format help from administrator.
     * Sends notification to superadmin about user's format compatibility issue.
     */
    @PostMapping("/template/request-help")
    public ResponseEntity<Map<String, Object>> requestFormatHelp(
            @RequestBody Map<String, String> request,
            @RequestParam(value = "file", required = false) MultipartFile sampleFile) {
        
        String tenantId = TenantContext.getTenantId();
        String userEmail = request.get("email");
        String userName = request.get("name");
        String message = request.get("message");
        String biometricDevice = request.get("biometricDevice");
        
        log.info("Format help request from tenant={}, user={}, device={}", tenantId, userEmail, biometricDevice);
        
        // TODO: Send email notification to superadmin
        // For now, log the request
        log.info("=== FORMAT HELP REQUEST ===");
        log.info("Tenant: {}", tenantId);
        log.info("User: {} ({})", userName, userEmail);
        log.info("Biometric Device: {}", biometricDevice);
        log.info("Message: {}", message);
        log.info("===========================");
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Your request has been submitted. Our support team will contact you shortly at " + userEmail);
        response.put("ticketId", "FMT-" + System.currentTimeMillis());
        response.put("supportEmail", "support@hrms.com");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Download the attendance import template for a specific month/year and biometric device.
     * The template is pre-filled with employee list and has the same format as biometric exports.
     * The deviceId/deviceCode is encoded in the filename for validation during import.
     */
    @GetMapping("/template/download")
    public ResponseEntity<byte[]> downloadAttendanceTemplate(
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "deviceId", required = false) Long deviceId,
            @RequestParam(value = "deviceCode", required = false) String deviceCode) {
        try {
            String tenantId = TenantContext.getTenantId();
            
            // Default to current month/year if not provided
            java.time.YearMonth ym = (month != null && year != null) 
                ? java.time.YearMonth.of(year, month)
                : java.time.YearMonth.now();
            
            byte[] template = attendanceExcelService.generateTemplate(tenantId, ym, deviceId);
            
            // Build filename with embedded device info (similar to employee import)
            String filename;
            if (deviceId != null && deviceCode != null && !deviceCode.isEmpty()) {
                // Encode device info in filename: attendance_template_YYYY_MM_device_ID_CODE.xlsx
                filename = String.format("attendance_template_%d_%02d_device_%d_%s.xlsx", 
                        ym.getYear(), ym.getMonthValue(), deviceId, deviceCode.replaceAll("[^a-zA-Z0-9_-]", "_"));
                log.info("Generating attendance template with device: {} ({})", deviceCode, deviceId);
            } else {
                filename = String.format("attendance_template_%d_%02d.xlsx", ym.getYear(), ym.getMonthValue());
            }
            
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
