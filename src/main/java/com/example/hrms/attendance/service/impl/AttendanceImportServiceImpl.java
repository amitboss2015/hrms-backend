package com.example.hrms.attendance.service.impl;

import com.example.hrms.attendance.domain.*;
import com.example.hrms.attendance.dto.AttendanceImportPreview;
import com.example.hrms.attendance.dto.ImportResultDTO;
import com.example.hrms.attendance.repo.*;
import com.example.hrms.attendance.service.AttendanceImportService;
import com.example.hrms.attendance.service.AttendanceEngine;
import com.example.hrms.domain.Employee;
import com.example.hrms.leave.repo.EmployeeLeaveRepository;
import com.example.hrms.leave.domain.EmployeeLeave;
import com.example.hrms.leave.domain.enums.LeaveStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceImportServiceImpl implements AttendanceImportService {

    private final AttendancePunchRepository punchRepo;
    private final ImportBatchRepository batchRepo;
    private final ImportErrorRepository errorRepo;
    private final com.example.hrms.repo.EmployeeRepository employeeRepo;
    private final AttendanceEngine attendanceEngine;
    private final EmployeeLeaveRepository leaveRepo;

    private static final ZoneId DEFAULT_TZ = ZoneId.of("Asia/Kolkata");
    
    // Threshold for considering a punch as cross-midnight (before this hour = likely OUT from previous day)
    private static final int CROSS_MIDNIGHT_THRESHOLD_HOUR = 6;

    /**
     * Preview the attendance file before importing.
     * Parses the file without saving anything, shows what will be imported.
     * This is the legacy non-tenant-aware version - forwards to tenant-aware version with null.
     */
    @Override
    public AttendanceImportPreview previewImport(Long orgId, MultipartFile file, int month, int year) {
        return previewImport(orgId, null, file, month, year);
    }
    
    /**
     * Preview the attendance file before importing (tenant-aware version).
     * Parses the file without saving anything, shows what will be imported.
     */
    @Override
    public AttendanceImportPreview previewImport(Long orgId, String tenantId, MultipartFile file, int month, int year) {
        log.info("Preview import for org={}, tenant={}, month={}, year={}", orgId, tenantId, month, year);
        
        YearMonth ymFromParams = YearMonth.of(year, month);
        
        // Check for duplicate upload
        List<ImportBatch> existingBatches = batchRepo.findByOrgIdAndMonthAndYear(orgId, month, year);
        if (!existingBatches.isEmpty()) {
            ImportBatch existing = existingBatches.get(0);
            return AttendanceImportPreview.duplicate(
                existing.getId(), 
                existing.getUploadedAt().toString(),
                month, year
            );
        }
        
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sh = findLogsSheet(wb);
            DataFormatter fmt = new DataFormatter();
            
            // Parse period from first few rows
            YearMonth ymUsed = ymFromParams;
            for (int r = 0; r <= 3; r++) {
                Row row = sh.getRow(r);
                if (row == null) continue;
                for (int c = 0; c <= 5; c++) {
                    String cell = readCell(row, c, fmt);
                    if (cell.contains("~") || cell.contains("–")) {
                        YearMonth ym = parseYearMonthFromPeriod(cell);
                        if (ym != null) {
                            ymUsed = ym;
                            break;
                        }
                    }
                }
            }
            
            int daysInMonth = ymUsed.lengthOfMonth();
            List<AttendanceImportPreview.EmployeeMatch> employeeMatches = new ArrayList<>();
            List<AttendanceImportPreview.SampleRow> sampleRows = new ArrayList<>();
            Map<Integer, Integer> punchesPerDay = new HashMap<>();
            int totalPunchRecords = 0;
            Set<Integer> daysWithDataSet = new HashSet<>();
            
            // Find first employee block
            int rowIdx = 0;
            while (rowIdx < sh.getLastRowNum()) {
                Row row = sh.getRow(rowIdx);
                if (row != null) {
                    String firstCell = readCell(row, 0, fmt).toLowerCase();
                    if (firstCell.startsWith("no") && firstCell.contains(":")) {
                        break;
                    }
                }
                rowIdx++;
            }
            
            // Parse employee blocks
            int sampleCount = 0;
            while (rowIdx < sh.getLastRowNum()) {
                Row metaRow = sh.getRow(rowIdx);
                Row punchRow = sh.getRow(rowIdx + 1);
                
                if (metaRow == null) {
                    rowIdx++;
                    continue;
                }
                
                String firstCell = readCell(metaRow, 0, fmt);
                if (!firstCell.toLowerCase().startsWith("no")) {
                    rowIdx++;
                    continue;
                }
                
                // Extract employee code
                String empCodeRaw = readCell(metaRow, 2, fmt);
                
                // Find employee name
                String empName = "";
                for (int c = 8; c <= 12; c++) {
                    String cell = readCell(metaRow, c, fmt);
                    if (cell.toLowerCase().contains("name")) {
                        for (int nc = c + 1; nc <= c + 3; nc++) {
                            String nameVal = readCell(metaRow, nc, fmt);
                            if (!isBlank(nameVal) && !nameVal.toLowerCase().contains(":")) {
                                empName = nameVal;
                                break;
                            }
                        }
                        break;
                    }
                }
                if (isBlank(empName)) {
                    empName = readCell(metaRow, 10, fmt);
                }
                
                if (isBlank(empCodeRaw)) {
                    rowIdx += 2;
                    continue;
                }
                
                String empCode = normalizeEmpCode(empCodeRaw);
                // Use tenant-aware lookup if tenantId provided, otherwise fallback to global lookup
                Optional<Employee> matchedEmployee = tenantId != null 
                    ? employeeRepo.findByTenantIdAndEmpCode(tenantId, empCode)
                    : employeeRepo.findByEmpCode(empCode);
                
                // Count punches for this employee
                int empPunchCount = 0;
                List<AttendanceImportPreview.DayPunches> dayPunchesList = new ArrayList<>();
                
                if (punchRow != null) {
                    for (int dayCol = 0; dayCol < daysInMonth; dayCol++) {
                        String cellVal = readCell(punchRow, dayCol, fmt);
                        if (isBlank(cellVal)) continue;
                        
                        String[] times = cellVal.split("\\r?\\n");
                        List<String> validTimes = new ArrayList<>();
                        
                        for (String rawTime : times) {
                            if (rawTime == null) continue;
                            rawTime = rawTime.trim();
                            if (rawTime.isEmpty()) continue;
                            
                            LocalTime lt = parseTimeSafe(rawTime);
                            if (lt != null) {
                                validTimes.add(rawTime);
                                empPunchCount++;
                                totalPunchRecords++;
                                daysWithDataSet.add(dayCol + 1);
                                punchesPerDay.merge(dayCol + 1, 1, Integer::sum);
                            }
                        }
                        
                        // For sample rows, collect first 7 days
                        if (sampleCount < 5 && dayCol < 7 && !validTimes.isEmpty()) {
                            String status = validTimes.size() >= 2 ? "PRESENT" : "ONLY_IN";
                            dayPunchesList.add(AttendanceImportPreview.DayPunches.builder()
                                    .dayOfMonth(dayCol + 1)
                                    .punches(validTimes)
                                    .status(status)
                                    .build());
                        }
                    }
                }
                
                // Add employee match
                AttendanceImportPreview.EmployeeMatch match = AttendanceImportPreview.EmployeeMatch.builder()
                        .empCodeInFile(empCodeRaw)
                        .nameInFile(empName)
                        .matched(matchedEmployee.isPresent())
                        .matchedEmployeeId(matchedEmployee.map(Employee::getId).orElse(null))
                        .matchedEmpCode(matchedEmployee.map(Employee::getEmpCode).orElse(null))
                        .matchedName(matchedEmployee.map(e -> e.getFirstName() + " " + (e.getLastName() != null ? e.getLastName() : "")).orElse(null))
                        .punchCount(empPunchCount)
                        .build();
                employeeMatches.add(match);
                
                // Add sample row (first 5 employees only)
                if (sampleCount < 5 && !dayPunchesList.isEmpty()) {
                    sampleRows.add(AttendanceImportPreview.SampleRow.builder()
                            .empCode(empCode)
                            .name(empName)
                            .days(dayPunchesList)
                            .build());
                    sampleCount++;
                }
                
                rowIdx += 2;
            }
            
            // Calculate summary
            int matchedCount = (int) employeeMatches.stream().filter(AttendanceImportPreview.EmployeeMatch::isMatched).count();
            int unmatchedCount = employeeMatches.size() - matchedCount;
            
            String periodStr = ymUsed.getMonth().toString() + " " + ymUsed.getYear();
            
            log.info("Preview complete: {} employees ({} matched, {} unmatched), {} punch records",
                    employeeMatches.size(), matchedCount, unmatchedCount, totalPunchRecords);
            
            return AttendanceImportPreview.builder()
                    .valid(true)
                    .message("File parsed successfully. Ready to import.")
                    .fileName(file.getOriginalFilename())
                    .detectedFormat("BIOMETRIC_LOGS")
                    .detectedPeriod(periodStr)
                    .detectedMonth(ymUsed.getMonthValue())
                    .detectedYear(ymUsed.getYear())
                    .totalEmployeesInFile(employeeMatches.size())
                    .matchedEmployees(matchedCount)
                    .unmatchedEmployees(unmatchedCount)
                    .employeeMatches(employeeMatches)
                    .totalPunchRecords(totalPunchRecords)
                    .daysWithData(daysWithDataSet.size())
                    .punchesPerDay(punchesPerDay)
                    .sampleRows(sampleRows)
                    .duplicateExists(false)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error previewing file: {}", e.getMessage(), e);
            return AttendanceImportPreview.error("Failed to parse file: " + e.getMessage());
        }
    }

    /**
     * Import attendance (legacy non-tenant-aware version)
     */
    @Override
    @Transactional
    public ImportResultDTO importLogsExcel(Long orgId, MultipartFile file, int month, int year, String uploadedBy) {
        return importLogsExcel(orgId, null, file, month, year, uploadedBy);
    }
    
    /**
     * Import attendance from biometric Excel file (tenant-aware version)
     */
    @Override
    @Transactional
    public ImportResultDTO importLogsExcel(Long orgId, String tenantId, MultipartFile file, int month, int year, String uploadedBy) {
        YearMonth ymFromParams = YearMonth.of(year, month);
        log.info("Import attendance for org={}, tenant={}, month={}, year={}", orgId, tenantId, month, year);

        // Check for duplicate upload for same org, month, year
        List<ImportBatch> existingBatches = batchRepo.findByOrgIdAndMonthAndYear(orgId, month, year);
        if (!existingBatches.isEmpty()) {
            return ImportResultDTO.builder()
                    .batchId(String.valueOf(existingBatches.get(0).getId()))
                    .total(0).success(0).failed(0)
                    .errorsCsvUrl(null)
                    .message("Attendance for " + ymFromParams.getMonth() + " " + year + " has already been uploaded. " +
                            "Batch ID: " + existingBatches.get(0).getId() + ". Delete the existing batch first to re-upload.")
                    .duplicate(true)
                    .existingBatchId(existingBatches.get(0).getId())
                    .build();
        }

        ImportBatch batch = ImportBatch.builder()
                .orgId(orgId)
                .uploadedBy(uploadedBy)
                .uploadedAt(Instant.now())
                .month(month)
                .year(year)
                .templateVersion("biometric-logs-v3")
                .totalRows(0).successRows(0).errorRows(0)
                .build();
        batch = batchRepo.save(batch);

        int total = 0, success = 0, failed = 0;
        Set<Long> affectedEmployeeIds = new HashSet<>();
        YearMonth ymUsed = ymFromParams;
        
        // Batch collection for performance - save all at once instead of individually
        List<AttendancePunch> punchBatch = new ArrayList<>();
        List<ImportError> errorBatch = new ArrayList<>();

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            // Find the "Logs" sheet
            Sheet sh = findLogsSheet(wb);
            DataFormatter fmt = new DataFormatter();

            // Parse period from Row 2 (0-indexed: row 1)
            // Format in your Excel: "Period :" in col A, date range in col B or C
            for (int r = 0; r <= 3; r++) {
                Row row = sh.getRow(r);
                if (row == null) continue;
                for (int c = 0; c <= 5; c++) {
                    String cell = readCell(row, c, fmt);
                    if (cell.contains("~") || cell.contains("–")) {
                        YearMonth ym = parseYearMonthFromPeriod(cell);
                        if (ym != null) {
                            ymUsed = ym;
                            break;
                        }
                    }
                }
            }

            int daysInMonth = ymUsed.lengthOfMonth();

            // Find the first employee block by looking for "No :" pattern
            int rowIdx = 0;
            while (rowIdx < sh.getLastRowNum()) {
                Row row = sh.getRow(rowIdx);
                if (row != null) {
                    String firstCell = readCell(row, 0, fmt).toLowerCase();
                    if (firstCell.startsWith("no") && firstCell.contains(":")) {
                        break; // Found the first employee meta row
                    }
                }
                rowIdx++;
            }

            // Parse employee blocks
            // Structure based on your Excel:
            // Meta Row: "No :" [col A], empCode [col C], ... "Name :" [col I], empName [col K], "Dept :" [col S]
            // Punch Row: Times in columns A-AE (0-30 for days 1-31)
            
            while (rowIdx < sh.getLastRowNum()) {
                Row metaRow = sh.getRow(rowIdx);
                Row punchRow = sh.getRow(rowIdx + 1);

                if (metaRow == null) {
                    rowIdx++;
                    continue;
                }

                String firstCell = readCell(metaRow, 0, fmt);
                
                // Check if this is an employee meta row (contains "No :" pattern)
                if (!firstCell.toLowerCase().startsWith("no")) {
                    rowIdx++;
                    continue;
                }

                // Extract employee code - in your Excel it's after "No :" label
                // Looking at structure: A="No :", B="", C=empCode
                String empCodeRaw = readCell(metaRow, 2, fmt);
                
                // Find employee name - usually after "Name :" which could be around column I (8) or K (10)
                String empName = "";
                for (int c = 8; c <= 12; c++) {
                    String cell = readCell(metaRow, c, fmt);
                    if (cell.toLowerCase().contains("name")) {
                        // Name value is in the next non-empty cell
                        for (int nc = c + 1; nc <= c + 3; nc++) {
                            String nameVal = readCell(metaRow, nc, fmt);
                            if (!isBlank(nameVal) && !nameVal.toLowerCase().contains(":")) {
                                empName = nameVal;
                                break;
                            }
                        }
                        break;
                    }
                }
                // If not found with label, try column K directly
                if (isBlank(empName)) {
                    empName = readCell(metaRow, 10, fmt);
                }

                if (isBlank(empCodeRaw)) {
                    rowIdx += 2; // Skip to next block
                    continue;
                }

                String empCode = normalizeEmpCode(empCodeRaw);
                Long employeeId = resolveEmployeeId(empCode, tenantId);

                if (employeeId == null) {
                    failed++;
                    errorBatch.add(ImportError.builder()
                            .batchId(batch.getId())
                            .rowNum(rowIdx + 1)
                            .columnName("C")
                            .errorCode("EMP_NOT_FOUND")
                            .errorMessage("No employee found for code '" + empCodeRaw + "' (name: " + empName + ")")
                            .rawPayloadJson("{\"code\":\"" + escapeJson(empCodeRaw) + "\",\"name\":\"" + escapeJson(empName) + "\"}")
                            .build());
                    rowIdx += 2;
                    continue;
                }

                affectedEmployeeIds.add(employeeId);

                // Parse punch times from punchRow
                if (punchRow != null) {
                    for (int dayCol = 0; dayCol < daysInMonth; dayCol++) {
                        String cellVal = readCell(punchRow, dayCol, fmt);
                        if (isBlank(cellVal)) continue;

                        // Parse multiple punch times (separated by newlines)
                        String[] times = cellVal.split("\\r?\\n");
                        List<PunchInfo> dayPunches = new ArrayList<>();

                        for (int punchIdx = 0; punchIdx < times.length; punchIdx++) {
                            String rawTime = times[punchIdx];
                            if (rawTime == null) continue;
                            rawTime = rawTime.trim();
                            if (rawTime.isEmpty()) continue;

                            total++;
                            try {
                                LocalTime lt = parseTime(rawTime);
                                LocalDate punchDate = ymUsed.atDay(dayCol + 1);
                                
                                // Determine if this is a cross-midnight punch
                                // If it's before 6 AM and it's the first punch of the day (or only punch),
                                // it's likely an OUT from a shift that started the previous day
                                boolean isCrossMidnightOut = false;
                                if (lt.getHour() < CROSS_MIDNIGHT_THRESHOLD_HOUR) {
                                    // Check if this is likely an OUT from previous day
                                    // Heuristic: if it's the only punch or first punch before 6 AM
                                    if (punchIdx == 0 && (times.length == 1 || 
                                        (times.length > 1 && !isBlank(times[1]) && parseTimeSafe(times[1].trim()) != null 
                                         && parseTimeSafe(times[1].trim()).getHour() >= CROSS_MIDNIGHT_THRESHOLD_HOUR))) {
                                        isCrossMidnightOut = true;
                                    }
                                }

                                // Determine punch type
                                String punchType;
                                if (isCrossMidnightOut) {
                                    // This punch belongs to the previous day as OUT
                                    punchType = "OUT";
                                } else {
                                    // Normal alternating IN/OUT
                                    int effectivePunchNum = dayPunches.size() + 1;
                                    punchType = (effectivePunchNum % 2 == 1) ? "IN" : "OUT";
                                }

                                Instant utc = ZonedDateTime.of(punchDate, lt, DEFAULT_TZ).toInstant();

                                AttendancePunch p = AttendancePunch.builder()
                                        .orgId(orgId)
                                        .employeeId(employeeId)
                                        .punchTsUtc(utc)
                                        .timezone(DEFAULT_TZ.getId())
                                        .source("BIOMETRIC_IMPORT")
                                        .punchTypeHint(punchType)
                                        .prevDayCheckout(isCrossMidnightOut)
                                        .deviceId(null)
                                        .shiftHint(determineShiftHint(lt, isCrossMidnightOut))
                                        .importBatchId(batch.getId())
                                        .build();

                                punchBatch.add(p);
                                dayPunches.add(new PunchInfo(lt, punchType, isCrossMidnightOut));
                                success++;
                            } catch (Exception ex) {
                                failed++;
                                errorBatch.add(ImportError.builder()
                                        .batchId(batch.getId())
                                        .rowNum(rowIdx + 2)
                                        .columnName(colLetter(dayCol))
                                        .errorCode("TIME_PARSE")
                                        .errorMessage("Invalid time '" + rawTime + "' for day " + (dayCol + 1) + ": " + ex.getMessage())
                                        .rawPayloadJson("{\"value\":\"" + escapeJson(rawTime) + "\"}")
                                        .build());
                            }
                        }
                    }
                }

                rowIdx += 2; // Move to next employee block (meta + punch rows)
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Excel: " + e.getMessage(), e);
        }
        
        // Batch save all punches at once for performance (instead of 1922 individual saves)
        if (!punchBatch.isEmpty()) {
            log.info("💾 Batch saving {} punch records...", punchBatch.size());
            punchRepo.saveAll(punchBatch);
            log.info("✅ Punch records saved successfully");
        }
        
        // Batch save all errors
        if (!errorBatch.isEmpty()) {
            errorRepo.saveAll(errorBatch);
        }
        
        // Update batch stats
        batch.setTotalRows(total);
        batch.setSuccessRows(success);
        batch.setErrorRows(failed);
        batchRepo.save(batch);

        // Recompute attendance sessions and day rollups
        log.info("🔄 Rebuilding attendance for {} employees...", affectedEmployeeIds.size());
        for (Long empId : affectedEmployeeIds) {
            attendanceEngine.rebuildEmployeeMonth(orgId, empId, ymUsed);
        }
        log.info("✅ Attendance rebuild complete");

        String message;
        if (failed == 0 && success > 0) {
            message = "Attendance uploaded successfully. Processed " + success + " punch records for " + affectedEmployeeIds.size() + " employees.";
        } else if (success == 0) {
            message = "No records imported. Please check the file format or ensure employees exist in the system.";
        } else {
            message = "Imported with some errors. Success: " + success + ", Failed: " + failed;
        }

        return ImportResultDTO.builder()
                .batchId(String.valueOf(batch.getId()))
                .total(total).success(success).failed(failed)
                .errorsCsvUrl(failed > 0 ? "/attendance/import/batches/" + batch.getId() + "/errors.csv" : null)
                .message(message)
                .duplicate(false)
                .build();
    }

    // ========== Helper Methods ==========
    
    private record PunchInfo(LocalTime time, String type, boolean crossMidnight) {}

    private Sheet findLogsSheet(Workbook wb) {
        // Try to find sheet named "Logs" or containing "log"
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            String name = wb.getSheetName(i);
            if (name != null && name.toLowerCase().contains("log")) {
                return wb.getSheetAt(i);
            }
        }
        // Fallback: if more than one sheet, use the second one (often Logs is sheet 2)
        if (wb.getNumberOfSheets() > 1) {
            return wb.getSheetAt(1);
        }
        return wb.getSheetAt(0);
    }

    private String readCell(Row row, int col, DataFormatter fmt) {
        if (row == null) return "";
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        String v = fmt.formatCellValue(cell);
        return v == null ? "" : v.trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String normalizeEmpCode(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        // Handle Excel numeric formatting (e.g., "2.0" -> "2")
        if (s.matches("^\\d+\\.0$")) s = s.substring(0, s.indexOf('.'));
        if (s.contains(".")) {
            try {
                double d = Double.parseDouble(s);
                s = String.valueOf((int) d);
            } catch (Exception ignore) {}
        }
        try {
            s = String.valueOf(Integer.parseInt(s));
        } catch (Exception ignore) {}
        return s;
    }

    private Long resolveEmployeeId(String empCode, String tenantId) {
        if (isBlank(empCode)) return null;
        // Use tenant-aware lookup if tenantId provided, otherwise fallback to global lookup
        Optional<Employee> employee = tenantId != null 
            ? employeeRepo.findByTenantIdAndEmpCode(tenantId, empCode)
            : employeeRepo.findByEmpCode(empCode);
        return employee.map(Employee::getId).orElse(null);
    }

    private YearMonth parseYearMonthFromPeriod(String period) {
        if (period == null) return null;
        try {
            // Format: "2025/07/01 ~ 07/31" or similar
            String left = period.split("[~–]")[0].trim();
            String[] parts = left.split("[/\\-]");
            int y = Integer.parseInt(parts[0].trim());
            int m = Integer.parseInt(parts[1].trim());
            return YearMonth.of(y, m);
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalTime parseTime(String token) {
        String t = token.trim();
        
        // Handle HH:MM format (e.g., "08:59", "17:39", "00:33")
        if (t.matches("^\\d{1,2}:\\d{2}$")) {
            String[] hm = t.split(":");
            int h = Integer.parseInt(hm[0]);
            int m = Integer.parseInt(hm[1]);
            return LocalTime.of(h, m);
        }
        
        // Handle HH:MM:SS format
        if (t.matches("^\\d{1,2}:\\d{2}:\\d{2}$")) {
            String[] parts = t.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            return LocalTime.of(h, m);
        }
        
        // Handle AM/PM format
        String upper = t.toUpperCase();
        if (upper.matches("^\\d{1,2}:\\d{2}\\s*(AM|PM)$")) {
            boolean pm = upper.endsWith("PM");
            String[] hm = upper.replaceAll("\\s*(AM|PM)$", "").split(":");
            int h = Integer.parseInt(hm[0]);
            int m = Integer.parseInt(hm[1]);
            if (pm && h < 12) h += 12;
            if (!pm && h == 12) h = 0;
            return LocalTime.of(h, m);
        }
        
        // Try standard parsing as fallback
        return LocalTime.parse(t, DateTimeFormatter.ofPattern("HH:mm"));
    }
    
    private LocalTime parseTimeSafe(String token) {
        try {
            return parseTime(token);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Determine shift hint based on punch time
     */
    private String determineShiftHint(LocalTime time, boolean isCrossMidnightOut) {
        if (isCrossMidnightOut) {
            return "NIGHT"; // Cross-midnight punches are from night shifts
        }
        int hour = time.getHour();
        if (hour >= 6 && hour < 14) {
            return "MORNING";
        } else if (hour >= 14 && hour < 22) {
            return "EVENING";
        } else {
            return "NIGHT";
        }
    }

    private String colLetter(int index) {
        int i = index;
        StringBuilder sb = new StringBuilder();
        do {
            int r = i % 26;
            sb.append((char) ('A' + r));
            i = i / 26 - 1;
        } while (i >= 0);
        return sb.reverse().toString();
    }
    
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    
    /**
     * Get employee by ID
     */
    @Override
    public Employee getEmployeeById(Long employeeId) {
        return employeeRepo.findById(employeeId).orElse(null);
    }
    
    /**
     * Check if employee has approved leave for a date
     */
    @Override
    public boolean hasApprovedLeave(String tenantId, String empCode, LocalDate date) {
        List<EmployeeLeave> leaves = leaveRepo.findByTenantIdAndEmpIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            tenantId, empCode, date, date);
        return leaves.stream().anyMatch(l -> l.getStatus() == LeaveStatus.APPROVED);
    }
}
