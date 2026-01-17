package com.example.hrms.service.excel;

import com.example.hrms.domain.Employee;
import com.example.hrms.domain.enums.*;
import com.example.hrms.dto.EmployeeDTO;
import com.example.hrms.dto.EmployeeImportResult;
import com.example.hrms.repo.EmployeeRepository;
import com.example.hrms.tenant.TenantContext;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class EmployeeExcelService {

    private final EmployeeRepository employeeRepo;

    // Validation patterns
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern EMP_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Pattern PINCODE_PATTERN = Pattern.compile("^[1-9][0-9]{5}$");
    private static final Pattern AADHAAR_PATTERN = Pattern.compile("^[2-9]{1}[0-9]{11}$");
    private static final Pattern PAN_PATTERN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]{1}$");

    // Minimum required fields for import
    private static final String[] TEMPLATE_HEADERS = {
        "Emp Code*", "First Name*", "Last Name", "Phone", "Email", 
        "Department", "Designation", "Join Date (YYYY-MM-DD)", "Base Salary"
    };

    // Full headers for detailed template
    private static final String[] FULL_TEMPLATE_HEADERS = {
        "Emp Code*", "First Name*", "Last Name", "Phone", "Email",
        "Department", "Designation", "Employment Type", "Salary Basis", 
        "Base Salary", "Hourly Rate", "Join Date (YYYY-MM-DD)", "Status",
        "Address", "City", "State", "Pincode",
        "Aadhaar Number", "PAN Number", "Bank Account Number", "IFSC Code",
        "Bank Name", "Branch Name", "UAN Number", "ESIC Number",
        "Emergency Contact Name", "Emergency Contact Phone",
        "OT Allowed (Yes/No)", "Weekly Off Days"
    };

    public EmployeeExcelService(EmployeeRepository employeeRepo) {
        this.employeeRepo = employeeRepo;
    }

    /**
     * Generate download template with minimum required fields
     */
    public byte[] generateTemplate(boolean fullTemplate) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Employees");
            
            // Header style
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

            // Required field style (yellow background)
            CellStyle requiredStyle = workbook.createCellStyle();
            Font reqFont = workbook.createFont();
            reqFont.setBold(true);
            reqFont.setColor(IndexedColors.WHITE.getIndex());
            requiredStyle.setFont(reqFont);
            requiredStyle.setFillForegroundColor(IndexedColors.ORANGE.getIndex());
            requiredStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            requiredStyle.setBorderBottom(BorderStyle.THIN);
            requiredStyle.setBorderTop(BorderStyle.THIN);
            requiredStyle.setBorderLeft(BorderStyle.THIN);
            requiredStyle.setBorderRight(BorderStyle.THIN);

            String[] headers = fullTemplate ? FULL_TEMPLATE_HEADERS : TEMPLATE_HEADERS;
            
            // Create header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                // Required fields marked with * get orange background
                if (headers[i].endsWith("*")) {
                    cell.setCellStyle(requiredStyle);
                } else {
                    cell.setCellStyle(headerStyle);
                }
                sheet.setColumnWidth(i, 20 * 256); // 20 characters width
            }

            // Add sample data row
            Row sampleRow = sheet.createRow(1);
            if (fullTemplate) {
                sampleRow.createCell(0).setCellValue("EMP001");
                sampleRow.createCell(1).setCellValue("John");
                sampleRow.createCell(2).setCellValue("Doe");
                sampleRow.createCell(3).setCellValue("9876543210");
                sampleRow.createCell(4).setCellValue("john.doe@example.com");
                sampleRow.createCell(5).setCellValue("Engineering");
                sampleRow.createCell(6).setCellValue("Software Developer");
                sampleRow.createCell(7).setCellValue("FULL_TIME");
                sampleRow.createCell(8).setCellValue("MONTHLY");
                sampleRow.createCell(9).setCellValue("50000");
                sampleRow.createCell(10).setCellValue("");
                sampleRow.createCell(11).setCellValue(LocalDate.now().toString());
                sampleRow.createCell(12).setCellValue("ACTIVE");
                sampleRow.createCell(13).setCellValue("123 Main Street");
                sampleRow.createCell(14).setCellValue("Mumbai");
                sampleRow.createCell(15).setCellValue("Maharashtra");
                sampleRow.createCell(16).setCellValue("400001");
            } else {
                sampleRow.createCell(0).setCellValue("EMP001");
                sampleRow.createCell(1).setCellValue("John");
                sampleRow.createCell(2).setCellValue("Doe");
                sampleRow.createCell(3).setCellValue("9876543210");
                sampleRow.createCell(4).setCellValue("john.doe@example.com");
                sampleRow.createCell(5).setCellValue("Engineering");
                sampleRow.createCell(6).setCellValue("Software Developer");
                sampleRow.createCell(7).setCellValue(LocalDate.now().toString());
                sampleRow.createCell(8).setCellValue("50000");
            }

            // Add instructions sheet
            Sheet instructionSheet = workbook.createSheet("Instructions");
            int rowNum = 0;
            instructionSheet.createRow(rowNum++).createCell(0).setCellValue("EMPLOYEE IMPORT TEMPLATE - INSTRUCTIONS");
            rowNum++;
            instructionSheet.createRow(rowNum++).createCell(0).setCellValue("1. Fields marked with * are REQUIRED");
            instructionSheet.createRow(rowNum++).createCell(0).setCellValue("2. Emp Code: Unique employee code (letters, numbers, underscore, hyphen only)");
            instructionSheet.createRow(rowNum++).createCell(0).setCellValue("3. Phone: 10-digit Indian mobile number starting with 6-9");
            instructionSheet.createRow(rowNum++).createCell(0).setCellValue("4. Email: Valid email address format");
            instructionSheet.createRow(rowNum++).createCell(0).setCellValue("5. Join Date: Format YYYY-MM-DD (e.g., 2024-01-15)");
            instructionSheet.createRow(rowNum++).createCell(0).setCellValue("6. Employment Type: FULL_TIME, PART_TIME, CONTRACT, INTERN");
            instructionSheet.createRow(rowNum++).createCell(0).setCellValue("7. Salary Basis: MONTHLY, HOURLY, DAILY");
            instructionSheet.createRow(rowNum++).createCell(0).setCellValue("8. Status: ACTIVE, INACTIVE, TERMINATED, ON_LEAVE");
            instructionSheet.createRow(rowNum++).createCell(0).setCellValue("9. Weekly Off Days: SUNDAY or SATURDAY,SUNDAY (comma-separated)");
            rowNum++;
            instructionSheet.createRow(rowNum++).createCell(0).setCellValue("NOTE: Delete the sample row before importing!");
            
            instructionSheet.setColumnWidth(0, 80 * 256);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Import employees from Excel file with validation
     */
    @Transactional
    public EmployeeImportResult importFromExcel(MultipartFile file) throws IOException {
        EmployeeImportResult result = new EmployeeImportResult();
        
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("Tenant context not set. Please ensure you are logged in.");
            return result;
        }

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.rowIterator();
            
            if (!rowIterator.hasNext()) {
                result.setSuccess(false);
                result.setMessage("Excel file is empty");
                return result;
            }

            // Parse header row
            Row headerRow = rowIterator.next();
            Map<String, Integer> columnIndex = parseHeaderRow(headerRow);

            // Validate required columns exist
            if (!columnIndex.containsKey("Emp Code") && !columnIndex.containsKey("Emp Code*")) {
                result.setSuccess(false);
                result.setMessage("Missing required column: Emp Code");
                return result;
            }
            if (!columnIndex.containsKey("First Name") && !columnIndex.containsKey("First Name*")) {
                result.setSuccess(false);
                result.setMessage("Missing required column: First Name");
                return result;
            }

            List<Employee> employeesToSave = new ArrayList<>();
            Set<String> processedEmpCodes = new HashSet<>();
            int rowNum = 1; // Start from 1 (after header)

            while (rowIterator.hasNext()) {
                rowNum++;
                Row row = rowIterator.next();
                
                // Skip empty rows
                if (isEmptyRow(row)) {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    continue;
                }

                EmployeeDTO dto = parseRow(row, columnIndex, rowNum, result);
                
                if (dto == null) {
                    continue; // Errors already added to result
                }

                // Validate DTO
                List<String> validationErrors = validateEmployee(dto, tenantId, processedEmpCodes, rowNum);
                
                if (!validationErrors.isEmpty()) {
                    for (String error : validationErrors) {
                        result.addError(rowNum, dto.getEmpCode(), "", error);
                    }
                    continue;
                }

                processedEmpCodes.add(dto.getEmpCode().toUpperCase());
                
                // Convert to entity
                Employee employee = dto.toEntity();
                employee.setTenantId(tenantId);
                employeesToSave.add(employee);
            }

            result.setTotalRows(rowNum - 1);

            // Save valid employees
            if (!employeesToSave.isEmpty()) {
                List<Employee> saved = employeeRepo.saveAll(employeesToSave);
                result.setSuccessCount(saved.size());
                
                for (Employee e : saved) {
                    result.getImportedEmployees().add(EmployeeDTO.fromEntity(e));
                }
            }

            result.setErrorCount(result.getErrors().size());
            result.setSuccess(result.getErrors().isEmpty());
            
            if (result.isSuccess()) {
                result.setMessage(String.format("Successfully imported %d employees", result.getSuccessCount()));
            } else {
                result.setMessage(String.format("Import completed with errors. Success: %d, Errors: %d, Skipped: %d",
                        result.getSuccessCount(), result.getErrorCount(), result.getSkippedCount()));
            }

            return result;
        }
    }

    private Map<String, Integer> parseHeaderRow(Row headerRow) {
        Map<String, Integer> columnIndex = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String header = getCellStringValue(cell).trim();
                // Normalize header (remove asterisks, convert to standard names)
                header = header.replace("*", "").trim();
                columnIndex.put(header, i);
            }
        }
        return columnIndex;
    }

    private EmployeeDTO parseRow(Row row, Map<String, Integer> columnIndex, int rowNum, EmployeeImportResult result) {
        EmployeeDTO dto = new EmployeeDTO();
        
        try {
            // Required fields
            dto.setEmpCode(getStringValue(row, columnIndex, "Emp Code"));
            dto.setFirstName(getStringValue(row, columnIndex, "First Name"));
            
            // Skip if emp code is empty
            if (dto.getEmpCode() == null || dto.getEmpCode().trim().isEmpty()) {
                result.addError(rowNum, "", "Emp Code", "Employee code is required");
                return null;
            }
            
            if (dto.getFirstName() == null || dto.getFirstName().trim().isEmpty()) {
                result.addError(rowNum, dto.getEmpCode(), "First Name", "First name is required");
                return null;
            }

            // Optional fields
            dto.setLastName(getStringValue(row, columnIndex, "Last Name"));
            dto.setPhone(getStringValue(row, columnIndex, "Phone"));
            dto.setEmail(getStringValue(row, columnIndex, "Email"));
            dto.setDepartment(getStringValue(row, columnIndex, "Department"));
            dto.setDesignation(getStringValue(row, columnIndex, "Designation"));
            dto.setAddress(getStringValue(row, columnIndex, "Address"));
            dto.setCity(getStringValue(row, columnIndex, "City"));
            dto.setState(getStringValue(row, columnIndex, "State"));
            dto.setPincode(getStringValue(row, columnIndex, "Pincode"));
            dto.setAadhaar(getStringValue(row, columnIndex, "Aadhaar Number"));
            dto.setPan(getStringValue(row, columnIndex, "PAN Number"));
            dto.setBankAccount(getStringValue(row, columnIndex, "Bank Account Number"));
            dto.setIfsc(getStringValue(row, columnIndex, "IFSC Code"));
            dto.setBankName(getStringValue(row, columnIndex, "Bank Name"));
            dto.setBranchName(getStringValue(row, columnIndex, "Branch Name"));
            dto.setUanNumber(getStringValue(row, columnIndex, "UAN Number"));
            dto.setEsicNumber(getStringValue(row, columnIndex, "ESIC Number"));
            dto.setEmergencyContactName(getStringValue(row, columnIndex, "Emergency Contact Name"));
            dto.setEmergencyContactPhone(getStringValue(row, columnIndex, "Emergency Contact Phone"));

            // Employment type
            String empType = getStringValue(row, columnIndex, "Employment Type");
            if (empType != null && !empType.isEmpty()) {
                try {
                    dto.setEmploymentType(EmploymentType.valueOf(empType.toUpperCase().replace(" ", "_")));
                } catch (Exception e) {
                    result.addError(rowNum, dto.getEmpCode(), "Employment Type", 
                        "Invalid value. Use: FULL_TIME, PART_TIME, CONTRACT, INTERN");
                }
            }

            // Salary basis
            String salaryBasis = getStringValue(row, columnIndex, "Salary Basis");
            if (salaryBasis != null && !salaryBasis.isEmpty()) {
                try {
                    dto.setSalaryBasis(SalaryBasis.valueOf(salaryBasis.toUpperCase()));
                } catch (Exception e) {
                    result.addError(rowNum, dto.getEmpCode(), "Salary Basis", 
                        "Invalid value. Use: MONTHLY, HOURLY, DAILY");
                }
            }

            // Status
            String status = getStringValue(row, columnIndex, "Status");
            if (status != null && !status.isEmpty()) {
                try {
                    dto.setStatus(EmployeeStatus.valueOf(status.toUpperCase().replace(" ", "_")));
                } catch (Exception e) {
                    result.addError(rowNum, dto.getEmpCode(), "Status", 
                        "Invalid value. Use: ACTIVE, INACTIVE, TERMINATED, ON_LEAVE");
                }
            }

            // Base salary
            String baseSalary = getStringValue(row, columnIndex, "Base Salary");
            if (baseSalary != null && !baseSalary.isEmpty()) {
                try {
                    dto.setBaseSalary(new BigDecimal(baseSalary.replaceAll("[^0-9.]", "")));
                } catch (Exception e) {
                    result.addError(rowNum, dto.getEmpCode(), "Base Salary", "Invalid salary amount");
                }
            }

            // Hourly rate
            String hourlyRate = getStringValue(row, columnIndex, "Hourly Rate");
            if (hourlyRate != null && !hourlyRate.isEmpty()) {
                try {
                    dto.setHourlyRate(new BigDecimal(hourlyRate.replaceAll("[^0-9.]", "")));
                } catch (Exception e) {
                    result.addError(rowNum, dto.getEmpCode(), "Hourly Rate", "Invalid hourly rate");
                }
            }

            // Join date
            String joinDateStr = getStringValue(row, columnIndex, "Join Date");
            if (joinDateStr == null || joinDateStr.isEmpty()) {
                joinDateStr = getStringValue(row, columnIndex, "Join Date (YYYY-MM-DD)");
            }
            if (joinDateStr != null && !joinDateStr.isEmpty()) {
                try {
                    dto.setJoinDate(LocalDate.parse(joinDateStr));
                } catch (Exception e) {
                    result.addError(rowNum, dto.getEmpCode(), "Join Date", 
                        "Invalid date format. Use YYYY-MM-DD (e.g., 2024-01-15)");
                }
            }

            // OT Allowed
            String otAllowed = getStringValue(row, columnIndex, "OT Allowed");
            if (otAllowed == null || otAllowed.isEmpty()) {
                otAllowed = getStringValue(row, columnIndex, "OT Allowed (Yes/No)");
            }
            if (otAllowed != null) {
                dto.setOtAllowed("yes".equalsIgnoreCase(otAllowed) || "true".equalsIgnoreCase(otAllowed) || "1".equals(otAllowed));
            }

            // Weekly off days
            dto.setWeeklyOffDays(getStringValue(row, columnIndex, "Weekly Off Days"));

            return dto;
        } catch (Exception e) {
            result.addError(rowNum, dto.getEmpCode() != null ? dto.getEmpCode() : "", "", 
                "Error parsing row: " + e.getMessage());
            return null;
        }
    }

    private List<String> validateEmployee(EmployeeDTO dto, String tenantId, Set<String> processedEmpCodes, int rowNum) {
        List<String> errors = new ArrayList<>();

        // Emp code validation
        if (!EMP_CODE_PATTERN.matcher(dto.getEmpCode()).matches()) {
            errors.add("Emp Code can only contain letters, numbers, underscores and hyphens");
        }

        // Check for duplicate emp code in file
        if (processedEmpCodes.contains(dto.getEmpCode().toUpperCase())) {
            errors.add("Duplicate Emp Code in file: " + dto.getEmpCode());
        }

        // Check if emp code already exists in database for this tenant
        if (employeeRepo.existsByTenantIdAndEmpCode(tenantId, dto.getEmpCode())) {
            errors.add("Emp Code already exists: " + dto.getEmpCode());
        }

        // Phone validation
        if (dto.getPhone() != null && !dto.getPhone().isEmpty()) {
            String phone = dto.getPhone().replaceAll("[\\s-]", "");
            if (!PHONE_PATTERN.matcher(phone).matches()) {
                errors.add("Phone must be a valid 10-digit Indian mobile number starting with 6-9");
            }
        }

        // Email validation
        if (dto.getEmail() != null && !dto.getEmail().isEmpty()) {
            if (!EMAIL_PATTERN.matcher(dto.getEmail()).matches()) {
                errors.add("Email must be a valid email address");
            }
        }

        // Pincode validation
        if (dto.getPincode() != null && !dto.getPincode().isEmpty()) {
            if (!PINCODE_PATTERN.matcher(dto.getPincode()).matches()) {
                errors.add("Pincode must be a valid 6-digit Indian pincode");
            }
        }

        // Aadhaar validation
        if (dto.getAadhaar() != null && !dto.getAadhaar().isEmpty()) {
            String aadhaar = dto.getAadhaar().replaceAll("\\s", "");
            if (!AADHAAR_PATTERN.matcher(aadhaar).matches()) {
                errors.add("Aadhaar must be a valid 12-digit number");
            }
        }

        // PAN validation
        if (dto.getPan() != null && !dto.getPan().isEmpty()) {
            if (!PAN_PATTERN.matcher(dto.getPan().toUpperCase()).matches()) {
                errors.add("PAN must be in valid format (e.g., ABCDE1234F)");
            }
        }

        // Join date validation
        if (dto.getJoinDate() != null && dto.getJoinDate().isAfter(LocalDate.now())) {
            errors.add("Join date cannot be in the future");
        }

        // Base salary validation
        if (dto.getBaseSalary() != null && dto.getBaseSalary().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Base salary cannot be negative");
        }

        return errors;
    }

    private boolean isEmptyRow(Row row) {
        if (row == null) return true;
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellStringValue(cell);
                if (value != null && !value.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private String getStringValue(Row row, Map<String, Integer> columnIndex, String columnName) {
        Integer idx = columnIndex.get(columnName);
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        return getCellStringValue(cell);
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDate date = cell.getDateCellValue().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDate();
                    return date.toString();
                } else {
                    double value = cell.getNumericCellValue();
                    if (value == Math.floor(value)) {
                        return String.valueOf((long) value);
                    }
                    return String.valueOf(value);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue().trim();
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
}
