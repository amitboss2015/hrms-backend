package com.example.hrms.service.excel;

import com.example.hrms.domain.Employee;
import com.example.hrms.repo.EmployeeRepository;
import com.example.hrms.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Service for importing/exporting employee salary data (basic salary and increment).
 * Provides template download and bulk update functionality.
 */
@Service
@Slf4j
public class EmployeeSalaryExcelService {

    private final EmployeeRepository employeeRepo;

    private static final String[] SALARY_TEMPLATE_HEADERS = {
        "Emp Code", "Employee Name", "Department", "Basic Salary", "Increment"
    };

    public EmployeeSalaryExcelService(EmployeeRepository employeeRepo) {
        this.employeeRepo = employeeRepo;
    }

    /**
     * Generate salary template with current employee data.
     * Admin can download, fill in new values, and upload to bulk update.
     */
    public byte[] generateSalaryTemplate(String tenantId) throws IOException {
        List<Employee> employees = employeeRepo.findByTenantIdAndStatusOrderByEmpCodeAsc(
                tenantId, com.example.hrms.domain.enums.EmployeeStatus.ACTIVE);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Employee Salary");

            // Header styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle editableStyle = createEditableStyle(workbook);
            CellStyle readOnlyStyle = createReadOnlyStyle(workbook);

            // Create header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < SALARY_TEMPLATE_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(SALARY_TEMPLATE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 20 * 256);
            }

            // Populate employee data
            int rowNum = 1;
            for (Employee emp : employees) {
                Row row = sheet.createRow(rowNum++);
                
                // Read-only columns (gray background)
                Cell empCodeCell = row.createCell(0);
                empCodeCell.setCellValue(emp.getEmpCode());
                empCodeCell.setCellStyle(readOnlyStyle);
                
                Cell nameCell = row.createCell(1);
                nameCell.setCellValue(getFullName(emp));
                nameCell.setCellStyle(readOnlyStyle);
                
                Cell deptCell = row.createCell(2);
                deptCell.setCellValue(emp.getDepartment() != null ? emp.getDepartment() : "");
                deptCell.setCellStyle(readOnlyStyle);
                
                // Editable columns (yellow background) - edit these values directly
                Cell basicCell = row.createCell(3);
                basicCell.setCellValue(emp.getBaseSalary() != null ? emp.getBaseSalary().doubleValue() : 0);
                basicCell.setCellStyle(editableStyle);
                
                Cell incrCell = row.createCell(4);
                incrCell.setCellValue(emp.getIncrement() != null ? emp.getIncrement().doubleValue() : 0);
                incrCell.setCellStyle(editableStyle);
            }

            // Add instructions sheet
            addInstructionsSheet(workbook);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Import salary updates from Excel file.
     * Updates basic_salary and increment for matching employees.
     */
    @Transactional
    public Map<String, Object> importSalaryUpdates(MultipartFile file) throws IOException {
        Map<String, Object> result = new HashMap<>();
        String tenantId = TenantContext.getTenantId();
        
        if (tenantId == null || tenantId.isEmpty()) {
            result.put("success", false);
            result.put("error", "Tenant context not set");
            return result;
        }

        List<String> errors = new ArrayList<>();
        int updatedCount = 0;
        int skippedCount = 0;

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.rowIterator();

            if (!rowIterator.hasNext()) {
                result.put("success", false);
                result.put("error", "Excel file is empty");
                return result;
            }

            // Skip header row
            rowIterator.next();

            int rowNum = 1;
            while (rowIterator.hasNext()) {
                rowNum++;
                Row row = rowIterator.next();

                String empCode = getCellStringValue(row.getCell(0));
                if (empCode == null || empCode.trim().isEmpty()) {
                    skippedCount++;
                    continue;
                }

                Optional<Employee> empOpt = employeeRepo.findByTenantIdAndEmpCode(tenantId, empCode.trim());
                if (empOpt.isEmpty()) {
                    errors.add("Row " + rowNum + ": Employee not found: " + empCode);
                    continue;
                }

                Employee emp = empOpt.get();
                boolean updated = false;

                // Update basic salary (column 3 - "Basic Salary")
                Cell basicCell = row.getCell(3);
                if (basicCell != null) {
                    try {
                        BigDecimal newBasic = getNumericValue(basicCell);
                        if (newBasic != null && newBasic.compareTo(BigDecimal.ZERO) >= 0) {
                            if (emp.getBaseSalary() == null || newBasic.compareTo(emp.getBaseSalary()) != 0) {
                                emp.setBaseSalary(newBasic);
                                updated = true;
                            }
                        }
                    } catch (Exception e) {
                        errors.add("Row " + rowNum + ": Invalid basic salary value");
                    }
                }

                // Update increment (column 4 - "Increment")
                Cell incrCell = row.getCell(4);
                if (incrCell != null) {
                    try {
                        BigDecimal newIncr = getNumericValue(incrCell);
                        if (newIncr != null && newIncr.compareTo(BigDecimal.ZERO) >= 0) {
                            if (emp.getIncrement() == null || newIncr.compareTo(emp.getIncrement()) != 0) {
                                emp.setIncrement(newIncr);
                                updated = true;
                            }
                        }
                    } catch (Exception e) {
                        errors.add("Row " + rowNum + ": Invalid increment value");
                    }
                }

                if (updated) {
                    employeeRepo.save(emp);
                    updatedCount++;
                } else {
                    skippedCount++;
                }
            }
        }

        result.put("success", errors.isEmpty());
        result.put("updatedCount", updatedCount);
        result.put("skippedCount", skippedCount);
        result.put("errors", errors);
        result.put("message", String.format("Updated %d employees, skipped %d, errors: %d", 
                updatedCount, skippedCount, errors.size()));

        return result;
    }

    private String getFullName(Employee emp) {
        String firstName = emp.getFirstName() != null ? emp.getFirstName() : "";
        String lastName = emp.getLastName() != null ? emp.getLastName() : "";
        return (firstName + " " + lastName).trim();
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            default:
                return null;
        }
    }

    private BigDecimal getNumericValue(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case NUMERIC:
                return BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING:
                String value = cell.getStringCellValue().trim();
                if (value.isEmpty()) return null;
                return new BigDecimal(value.replaceAll("[^0-9.]", ""));
            default:
                return null;
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createEditableStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createReadOnlyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private void addInstructionsSheet(Workbook workbook) {
        Sheet instructionSheet = workbook.createSheet("Instructions");
        int rowNum = 0;
        instructionSheet.createRow(rowNum++).createCell(0).setCellValue("EMPLOYEE SALARY UPDATE TEMPLATE");
        rowNum++;
        instructionSheet.createRow(rowNum++).createCell(0).setCellValue("Instructions:");
        instructionSheet.createRow(rowNum++).createCell(0).setCellValue("1. Gray columns (Emp Code, Name, Dept) are READ-ONLY - do not modify");
        instructionSheet.createRow(rowNum++).createCell(0).setCellValue("2. Yellow columns (Basic Salary, Increment) are EDITABLE - update these values");
        instructionSheet.createRow(rowNum++).createCell(0).setCellValue("3. Edit the Basic Salary and Increment values as needed");
        instructionSheet.createRow(rowNum++).createCell(0).setCellValue("4. Only changed values will be updated in the system");
        instructionSheet.createRow(rowNum++).createCell(0).setCellValue("5. Upload the file to apply changes");
        rowNum++;
        instructionSheet.createRow(rowNum++).createCell(0).setCellValue("Note: Only active employees are included in this template");
        instructionSheet.setColumnWidth(0, 70 * 256);
    }
}
