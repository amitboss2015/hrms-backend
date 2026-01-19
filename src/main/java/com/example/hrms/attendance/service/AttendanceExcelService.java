package com.example.hrms.attendance.service;

import com.example.hrms.attendance.domain.AttendanceDay;
import com.example.hrms.attendance.dto.AttendanceImportDTO;
import com.example.hrms.attendance.dto.AttendanceImportResult;
import com.example.hrms.attendance.repo.AttendanceDayRepository;
import com.example.hrms.domain.Employee;
import com.example.hrms.repo.EmployeeRepository;
import com.example.hrms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Service for generating attendance Excel templates and importing attendance data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceExcelService {

    private final EmployeeRepository employeeRepo;
    private final AttendanceDayRepository attendanceDayRepo;

    // Template is now matrix-style (days as columns) like biometric exports

    // Valid status values
    private static final Set<String> VALID_STATUSES = Set.of(
            "PRESENT", "ABSENT", "HALF_DAY", "LEAVE", "HOLIDAY", "WEEKLY_OFF", "LATE", "EARLY_EXIT"
    );

    // Date formatters to try
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
    );

    // Time formatters to try
    private static final List<DateTimeFormatter> TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("HH:mm:ss"),
            DateTimeFormatter.ofPattern("h:mm a"),
            DateTimeFormatter.ofPattern("hh:mm a")
    );

    /**
     * Generate a matrix-style attendance import template Excel file (backward compatible - no device filter).
     */
    public byte[] generateTemplate(String tenantId, java.time.YearMonth yearMonth) throws IOException {
        return generateTemplate(tenantId, yearMonth, null);
    }

    /**
     * Generate a matrix-style attendance import template Excel file.
     * Format: EmpCode | Name | 1 | 2 | 3 | ... | 31 (days as columns)
     * Each day cell contains IN time on line 1, OUT time on line 2
     * 
     * @param tenantId The tenant ID
     * @param yearMonth The year/month for the template
     * @param deviceId Optional - if provided, only employees assigned to this device will be included,
     *                 and device_emp_code will be used in the EmpCode column
     */
    public byte[] generateTemplate(String tenantId, java.time.YearMonth yearMonth, Long deviceId) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            int daysInMonth = yearMonth.lengthOfMonth();
            
            Sheet sheet = workbook.createSheet("Logs");

            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Create cell style for data cells (with wrap text for multi-line punch times)
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setWrapText(true);
            dataStyle.setVerticalAlignment(VerticalAlignment.TOP);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            // Row 0: Period info
            Row periodRow = sheet.createRow(0);
            periodRow.createCell(0).setCellValue("Period:");
            periodRow.createCell(1).setCellValue(yearMonth.getYear() + "/" + 
                String.format("%02d", yearMonth.getMonthValue()) + "/01 ~ " +
                String.format("%02d", daysInMonth));

            // Row 1: Header row with day numbers
            Row headerRow = sheet.createRow(1);
            Cell empCodeHeader = headerRow.createCell(0);
            empCodeHeader.setCellValue("EmpCode");
            empCodeHeader.setCellStyle(headerStyle);
            sheet.setColumnWidth(0, 3000);

            Cell nameHeader = headerRow.createCell(1);
            nameHeader.setCellValue("Name");
            nameHeader.setCellStyle(headerStyle);
            sheet.setColumnWidth(1, 5000);

            // Day columns (1 to 31)
            for (int day = 1; day <= 31; day++) {
                Cell dayCell = headerRow.createCell(day + 1);
                dayCell.setCellValue(day);
                dayCell.setCellStyle(headerStyle);
                sheet.setColumnWidth(day + 1, 2500);
            }

            // Get employees - filter by device if deviceId is provided
            List<Employee> employees;
            if (deviceId != null) {
                employees = employeeRepo.findByBiometricDeviceId(deviceId);
                log.info("Generating attendance template for device ID: {} with {} employees", deviceId, employees.size());
            } else {
                employees = employeeRepo.findByTenantId(tenantId);
            }
            
            int rowNum = 2;
            for (Employee emp : employees) {
                Row dataRow = sheet.createRow(rowNum);
                dataRow.setHeightInPoints(30); // Height for 2 lines
                
                // For device-specific templates, use deviceEmpCode if available
                Cell codeCell = dataRow.createCell(0);
                String empCodeToUse = (deviceId != null && emp.getDeviceEmpCode() != null && !emp.getDeviceEmpCode().isEmpty())
                        ? emp.getDeviceEmpCode()
                        : emp.getEmpCode();
                codeCell.setCellValue(empCodeToUse);
                codeCell.setCellStyle(dataStyle);
                
                Cell nameCell = dataRow.createCell(1);
                nameCell.setCellValue(emp.getFirstName() + 
                    (emp.getLastName() != null ? " " + emp.getLastName() : ""));
                nameCell.setCellStyle(dataStyle);
                
                // Empty cells for each day (user will fill in)
                for (int day = 1; day <= 31; day++) {
                    Cell dayCell = dataRow.createCell(day + 1);
                    dayCell.setCellStyle(dataStyle);
                    // Leave empty - user will fill with format: "09:00\n17:30"
                }
                
                rowNum++;
            }

            // If no employees, add a sample row
            if (employees.isEmpty()) {
                Row sampleRow = sheet.createRow(2);
                sampleRow.setHeightInPoints(30);
                sampleRow.createCell(0).setCellValue("EMP001");
                sampleRow.createCell(1).setCellValue("Sample Employee");
                Cell sampleDayCell = sampleRow.createCell(2);
                sampleDayCell.setCellValue("09:00\n17:30");
                sampleDayCell.setCellStyle(dataStyle);
            }

            // Create instruction sheet
            Sheet instructionSheet = workbook.createSheet("Instructions");
            createInstructionSheet(instructionSheet, workbook);

            // Write to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Create instruction sheet with field descriptions for matrix-style template.
     */
    private void createInstructionSheet(Sheet sheet, Workbook workbook) {
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        String[][] instructions = {
                {"ATTENDANCE IMPORT TEMPLATE - INSTRUCTIONS", "", ""},
                {"", "", ""},
                {"FORMAT:", "", ""},
                {"Column A", "EmpCode", "Employee code as registered in the system"},
                {"Column B", "Name", "Employee name (for reference only, not used in import)"},
                {"Column C onwards", "Day 1, 2, 3... 31", "Each column represents a day of the month"},
                {"", "", ""},
                {"HOW TO FILL PUNCH TIMES:", "", ""},
                {"Single punch", "09:00", "Just enter the time in HH:MM format"},
                {"IN and OUT", "09:00\\n17:30", "Enter IN time on first line, OUT on second line (press Alt+Enter for new line)"},
                {"Multiple punches", "09:00\\n12:30\\n13:30\\n17:30", "Enter all punch times, each on a new line"},
                {"No attendance", "(leave empty)", "Leave the cell empty for absent/no punch days"},
                {"", "", ""},
                {"IMPORTANT NOTES:", "", ""},
                {"1.", "Cross-midnight punches", "Times like 00:33, 01:00 are treated as OUT from previous day's night shift"},
                {"2.", "Time format", "Use 24-hour format (HH:MM). Examples: 09:00, 17:30, 00:33"},
                {"3.", "Employee matching", "EmpCode must match exactly with the code in the system"},
                {"4.", "Days beyond month", "Columns for days that don't exist in the month (e.g., 31 in Feb) are ignored"},
                {"", "", ""},
                {"EXAMPLE:", "", ""},
                {"EmpCode", "Name", "1 | 2 | 3 | ..."},
                {"2", "md sarwar", "08:59\\n17:39 | 08:58\\n17:45 | 08:57\\n17:39 | ..."},
                {"4", "dablu kumar", "09:07 | 09:09\\n17:37 | 09:09\\n17:37 | ..."},
        };

        for (int i = 0; i < instructions.length; i++) {
            Row row = sheet.createRow(i);
            for (int j = 0; j < instructions[i].length; j++) {
                Cell cell = row.createCell(j);
                cell.setCellValue(instructions[i][j]);
                if (i == 0 || i == 2 || i == 7 || i == 13 || i == 19) {
                    cell.setCellStyle(headerStyle);
                }
            }
        }

        sheet.setColumnWidth(0, 6000);
        sheet.setColumnWidth(1, 8000);
        sheet.setColumnWidth(2, 20000);
    }

    /**
     * Import attendance data from an Excel file.
     */
    @Transactional
    public AttendanceImportResult importAttendance(MultipartFile file, String tenantId) {
        if (file == null || file.isEmpty()) {
            return AttendanceImportResult.failure("File is empty or not provided");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            return AttendanceImportResult.failure("Invalid file format. Please upload an Excel file (.xlsx or .xls)");
        }

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return AttendanceImportResult.failure("Excel file has no sheets");
            }

            // Get all employees for this tenant (for validation)
            Map<String, Employee> employeeMap = getEmployeeMap(tenantId);
            if (employeeMap.isEmpty()) {
                return AttendanceImportResult.failure("No employees found for this organization. Please add employees first.");
            }

            // Parse and validate rows
            List<AttendanceImportDTO> validEntries = new ArrayList<>();
            List<AttendanceImportResult.RowError> errors = new ArrayList<>();
            int totalRows = 0;
            int skippedRows = 0;

            // Find header row and data start
            int headerRowIndex = findHeaderRow(sheet);
            if (headerRowIndex < 0) {
                return AttendanceImportResult.failure("Could not find header row. Expected columns: EmpCode, Date, InTime, OutTime");
            }

            // Get column indices
            Map<String, Integer> columnIndices = getColumnIndices(sheet.getRow(headerRowIndex));

            // Process data rows
            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) {
                    skippedRows++;
                    continue;
                }

                totalRows++;
                int rowNum = i + 1; // Excel row numbers are 1-based

                try {
                    AttendanceImportDTO dto = parseRow(row, rowNum, columnIndices);
                    
                    // Validate the entry
                    List<String> validationErrors = validateEntry(dto, employeeMap, tenantId);
                    
                    if (validationErrors.isEmpty()) {
                        validEntries.add(dto);
                    } else {
                        for (String error : validationErrors) {
                            errors.add(AttendanceImportResult.RowError.builder()
                                    .rowNumber(rowNum)
                                    .empCode(dto.getEmpCode())
                                    .date(dto.getDate() != null ? dto.getDate().toString() : "")
                                    .error(error)
                                    .build());
                        }
                    }
                } catch (Exception e) {
                    errors.add(AttendanceImportResult.RowError.builder()
                            .rowNumber(rowNum)
                            .error("Failed to parse row: " + e.getMessage())
                            .build());
                }
            }

            if (validEntries.isEmpty() && errors.isEmpty()) {
                return AttendanceImportResult.failure("No valid data rows found in the file");
            }

            // Save valid entries
            int successCount = 0;
            int updatedCount = 0;

            for (AttendanceImportDTO dto : validEntries) {
                try {
                    boolean isUpdate = saveAttendanceEntry(dto, employeeMap, tenantId);
                    if (isUpdate) {
                        updatedCount++;
                    } else {
                        successCount++;
                    }
                } catch (Exception e) {
                    log.error("Failed to save attendance entry for {} on {}: {}", 
                            dto.getEmpCode(), dto.getDate(), e.getMessage());
                    errors.add(AttendanceImportResult.RowError.builder()
                            .rowNumber(dto.getRowNumber())
                            .empCode(dto.getEmpCode())
                            .date(dto.getDate().toString())
                            .error("Failed to save: " + e.getMessage())
                            .build());
                }
            }

            // Build result
            if (errors.isEmpty()) {
                return AttendanceImportResult.success(totalRows, successCount, updatedCount, skippedRows);
            } else {
                return AttendanceImportResult.partial(totalRows, successCount, errors.size(), 
                        updatedCount, skippedRows, errors);
            }

        } catch (IOException e) {
            log.error("Error reading Excel file", e);
            return AttendanceImportResult.failure("Error reading file: " + e.getMessage());
        }
    }

    /**
     * Get map of employee code to Employee for validation.
     */
    private Map<String, Employee> getEmployeeMap(String tenantId) {
        List<Employee> employees = employeeRepo.findByTenantId(tenantId);
        Map<String, Employee> map = new HashMap<>();
        for (Employee emp : employees) {
            map.put(emp.getEmpCode().toUpperCase().trim(), emp);
            // Also add without leading zeros for flexibility
            String codeWithoutLeadingZeros = emp.getEmpCode().replaceFirst("^0+", "");
            if (!codeWithoutLeadingZeros.isEmpty()) {
                map.put(codeWithoutLeadingZeros.toUpperCase(), emp);
            }
        }
        return map;
    }

    /**
     * Find the header row in the sheet.
     */
    private int findHeaderRow(Sheet sheet) {
        for (int i = 0; i <= Math.min(10, sheet.getLastRowNum()); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                for (int j = 0; j < row.getLastCellNum(); j++) {
                    String value = getCellStringValue(row.getCell(j)).toUpperCase();
                    if (value.contains("EMPCODE") || value.contains("EMP_CODE") || 
                        value.contains("EMPLOYEE CODE") || value.contains("EMP CODE")) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Get column indices from header row.
     */
    private Map<String, Integer> getColumnIndices(Row headerRow) {
        Map<String, Integer> indices = new HashMap<>();
        
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            String header = getCellStringValue(headerRow.getCell(i)).toUpperCase()
                    .replaceAll("[^A-Z]", ""); // Remove special chars
            
            if (header.contains("EMPCODE") || header.contains("EMPLOYEECODE")) {
                indices.put("empCode", i);
            } else if (header.contains("DATE") && !header.contains("TIME")) {
                indices.put("date", i);
            } else if (header.contains("INTIME") || header.contains("CHECKIN") || header.contains("PUNCHIN")) {
                indices.put("inTime", i);
            } else if (header.contains("OUTTIME") || header.contains("CHECKOUT") || header.contains("PUNCHOUT")) {
                indices.put("outTime", i);
            } else if (header.contains("SHIFT")) {
                indices.put("shiftCode", i);
            } else if (header.contains("STATUS")) {
                indices.put("status", i);
            } else if (header.contains("REMARK") || header.contains("NOTE") || header.contains("COMMENT")) {
                indices.put("remarks", i);
            }
        }
        
        return indices;
    }

    /**
     * Parse a data row into AttendanceImportDTO.
     */
    private AttendanceImportDTO parseRow(Row row, int rowNum, Map<String, Integer> columnIndices) {
        AttendanceImportDTO dto = new AttendanceImportDTO();
        dto.setRowNumber(rowNum);
        
        // EmpCode
        if (columnIndices.containsKey("empCode")) {
            dto.setEmpCode(getCellStringValue(row.getCell(columnIndices.get("empCode"))).trim());
        }
        
        // Date
        if (columnIndices.containsKey("date")) {
            Cell dateCell = row.getCell(columnIndices.get("date"));
            dto.setDate(parseDateCell(dateCell));
        }
        
        // InTime
        if (columnIndices.containsKey("inTime")) {
            Cell timeCell = row.getCell(columnIndices.get("inTime"));
            dto.setInTime(parseTimeCell(timeCell));
        }
        
        // OutTime
        if (columnIndices.containsKey("outTime")) {
            Cell timeCell = row.getCell(columnIndices.get("outTime"));
            dto.setOutTime(parseTimeCell(timeCell));
        }
        
        // ShiftCode
        if (columnIndices.containsKey("shiftCode")) {
            dto.setShiftCode(getCellStringValue(row.getCell(columnIndices.get("shiftCode"))).trim());
        }
        
        // Status
        if (columnIndices.containsKey("status")) {
            dto.setStatus(getCellStringValue(row.getCell(columnIndices.get("status"))).trim().toUpperCase());
        }
        
        // Remarks
        if (columnIndices.containsKey("remarks")) {
            dto.setRemarks(getCellStringValue(row.getCell(columnIndices.get("remarks"))).trim());
        }
        
        return dto;
    }

    /**
     * Validate an attendance entry.
     */
    private List<String> validateEntry(AttendanceImportDTO dto, Map<String, Employee> employeeMap, String tenantId) {
        List<String> errors = new ArrayList<>();
        
        // Required: EmpCode
        if (dto.getEmpCode() == null || dto.getEmpCode().isEmpty()) {
            errors.add("Employee Code is required");
        } else {
            // Check if employee exists
            String empCodeKey = dto.getEmpCode().toUpperCase().trim();
            if (!employeeMap.containsKey(empCodeKey)) {
                errors.add("Employee not found: " + dto.getEmpCode());
            }
        }
        
        // Required: Date
        if (dto.getDate() == null) {
            errors.add("Date is required and must be in valid format (DD/MM/YYYY)");
        } else {
            // Validate date is not in the future
            if (dto.getDate().isAfter(LocalDate.now())) {
                errors.add("Date cannot be in the future");
            }
            // Validate date is not too old (more than 2 years)
            if (dto.getDate().isBefore(LocalDate.now().minusYears(2))) {
                errors.add("Date is too old (more than 2 years ago)");
            }
        }
        
        // Validate times
        if (dto.getInTime() != null && dto.getOutTime() != null) {
            // OutTime before InTime could indicate cross-midnight shift - that's OK
            // But we can add a warning if they want
        }
        
        // Validate status if provided
        if (dto.getStatus() != null && !dto.getStatus().isEmpty()) {
            if (!VALID_STATUSES.contains(dto.getStatus())) {
                errors.add("Invalid status: " + dto.getStatus() + ". Valid values: " + VALID_STATUSES);
            }
        }
        
        // Must have at least times or status
        if (!dto.hasPunchTimes() && (dto.getStatus() == null || dto.getStatus().isEmpty())) {
            errors.add("Either punch times (InTime/OutTime) or Status is required");
        }
        
        return errors;
    }

    /**
     * Save an attendance entry.
     * @return true if this was an update, false if new insert
     */
    private boolean saveAttendanceEntry(AttendanceImportDTO dto, Map<String, Employee> employeeMap, String tenantId) {
        String empCodeKey = dto.getEmpCode().toUpperCase().trim();
        Employee employee = employeeMap.get(empCodeKey);
        
        // Check if entry already exists for this employee on this date
        Optional<AttendanceDay> existingOpt = attendanceDayRepo.findFirstByTenantIdAndEmployeeIdAndWorkDate(
                tenantId, employee.getId(), dto.getDate());
        
        AttendanceDay attendance;
        boolean isUpdate = existingOpt.isPresent();
        
        if (isUpdate) {
            attendance = existingOpt.get();
        } else {
            attendance = new AttendanceDay();
            attendance.setTenantId(tenantId);
            attendance.setEmployeeId(employee.getId());
            attendance.setWorkDate(dto.getDate());
        }
        
        // Set times (convert LocalTime to LocalDateTime for the entity)
        if (dto.getInTime() != null) {
            attendance.setFirstIn(dto.getDate().atTime(dto.getInTime()));
        }
        if (dto.getOutTime() != null) {
            // Handle cross-midnight: if outTime < inTime, it's the next day
            if (dto.getInTime() != null && dto.getOutTime().isBefore(dto.getInTime())) {
                attendance.setLastOut(dto.getDate().plusDays(1).atTime(dto.getOutTime()));
                attendance.setCrossedMidnight(true);
            } else {
                attendance.setLastOut(dto.getDate().atTime(dto.getOutTime()));
            }
        }
        
        // Set shift code if provided
        if (dto.getShiftCode() != null && !dto.getShiftCode().isEmpty()) {
            attendance.setShiftCodes(dto.getShiftCode());
        }
        
        // Set status if provided
        if (dto.getStatus() != null && !dto.getStatus().isEmpty()) {
            attendance.setStatus(dto.getStatus());
        } else if (dto.getInTime() != null) {
            attendance.setStatus("PRESENT");
        }
        
        // Set remarks
        if (dto.getRemarks() != null && !dto.getRemarks().isEmpty()) {
            attendance.setRemarks(dto.getRemarks());
        }
        
        // Calculate work minutes if both times are present
        if (attendance.getFirstIn() != null && attendance.getLastOut() != null) {
            int workMinutes = calculateWorkMinutesFromDateTime(attendance.getFirstIn(), attendance.getLastOut());
            attendance.setTotalWorkMin(workMinutes);
        }
        
        attendanceDayRepo.save(attendance);
        
        log.debug("Saved attendance for {} (ID:{}) on {}: {}", 
                employee.getEmpCode(), employee.getId(), dto.getDate(), dto.getStatus());
        
        return isUpdate;
    }
    
    /**
     * Calculate work minutes between two LocalDateTime values.
     */
    private int calculateWorkMinutesFromDateTime(java.time.LocalDateTime inTime, java.time.LocalDateTime outTime) {
        return (int) java.time.Duration.between(inTime, outTime).toMinutes();
    }

    /**
     * Calculate work minutes between in and out times.
     */
    private int calculateWorkMinutes(LocalTime inTime, LocalTime outTime) {
        if (outTime.isAfter(inTime)) {
            // Same day
            return (int) java.time.Duration.between(inTime, outTime).toMinutes();
        } else {
            // Cross midnight
            int minutesToMidnight = (int) java.time.Duration.between(inTime, LocalTime.MAX).toMinutes();
            int minutesFromMidnight = (int) java.time.Duration.between(LocalTime.MIN, outTime).toMinutes();
            return minutesToMidnight + minutesFromMidnight + 1;
        }
    }

    /**
     * Check if a row is empty.
     */
    private boolean isRowEmpty(Row row) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellStringValue(cell).trim();
                if (!value.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Get string value from a cell, handling different types.
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString();
                }
                // Avoid scientific notation for numbers
                double numValue = cell.getNumericCellValue();
                if (numValue == Math.floor(numValue)) {
                    return String.valueOf((long) numValue);
                }
                return String.valueOf(numValue);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        return "";
                    }
                }
            default:
                return "";
        }
    }

    /**
     * Parse a date from a cell.
     */
    private LocalDate parseDateCell(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            
            String dateStr = getCellStringValue(cell).trim();
            if (dateStr.isEmpty()) {
                return null;
            }
            
            // Try different date formats
            for (DateTimeFormatter formatter : DATE_FORMATTERS) {
                try {
                    return LocalDate.parse(dateStr, formatter);
                } catch (DateTimeParseException e) {
                    // Try next format
                }
            }
            
            log.warn("Could not parse date: {}", dateStr);
            return null;
        } catch (Exception e) {
            log.warn("Error parsing date cell: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse a time from a cell.
     */
    private LocalTime parseTimeCell(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                // Excel stores times as fractions of a day
                double numValue = cell.getNumericCellValue();
                if (numValue >= 0 && numValue < 1) {
                    int totalMinutes = (int) Math.round(numValue * 24 * 60);
                    int hours = totalMinutes / 60;
                    int minutes = totalMinutes % 60;
                    return LocalTime.of(hours % 24, minutes);
                }
            }
            
            String timeStr = getCellStringValue(cell).trim();
            if (timeStr.isEmpty() || timeStr.equals("-")) {
                return null;
            }
            
            // Try different time formats
            for (DateTimeFormatter formatter : TIME_FORMATTERS) {
                try {
                    return LocalTime.parse(timeStr, formatter);
                } catch (DateTimeParseException e) {
                    // Try next format
                }
            }
            
            log.warn("Could not parse time: {}", timeStr);
            return null;
        } catch (Exception e) {
            log.warn("Error parsing time cell: {}", e.getMessage());
            return null;
        }
    }
}
