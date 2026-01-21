package com.example.hrms.service.excel;

import com.example.hrms.attendance.domain.BiometricDevice;
import com.example.hrms.attendance.repo.BiometricDeviceRepository;
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
import java.util.*;

/**
 * Service for importing/exporting employee-biometric device associations.
 * Allows bulk assignment of employees to biometric devices.
 */
@Service
@Slf4j
public class BiometricAssociationExcelService {

    private final EmployeeRepository employeeRepo;
    private final BiometricDeviceRepository deviceRepo;

    private static final String[] TEMPLATE_HEADERS = {
        "Emp Code", "Employee Name", "Department", "Current Device", 
        "New Device Code", "Device Emp Code (if different)"
    };

    public BiometricAssociationExcelService(EmployeeRepository employeeRepo, 
                                            BiometricDeviceRepository deviceRepo) {
        this.employeeRepo = employeeRepo;
        this.deviceRepo = deviceRepo;
    }

    /**
     * Generate biometric association template with current employee-device mappings.
     */
    public byte[] generateTemplate(String tenantId) throws IOException {
        List<Employee> employees = employeeRepo.findByTenantIdAndStatusOrderByEmpCodeAsc(
                tenantId, com.example.hrms.domain.enums.EmployeeStatus.ACTIVE);
        List<BiometricDevice> devices = deviceRepo.findByTenantIdOrderByDeviceCodeAsc(tenantId);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Biometric Associations");

            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle editableStyle = createEditableStyle(workbook);
            CellStyle readOnlyStyle = createReadOnlyStyle(workbook);

            // Create header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(TEMPLATE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 22 * 256);
            }

            // Populate employee data
            int rowNum = 1;
            for (Employee emp : employees) {
                Row row = sheet.createRow(rowNum++);
                
                // Read-only columns
                Cell empCodeCell = row.createCell(0);
                empCodeCell.setCellValue(emp.getEmpCode());
                empCodeCell.setCellStyle(readOnlyStyle);
                
                Cell nameCell = row.createCell(1);
                nameCell.setCellValue(getFullName(emp));
                nameCell.setCellStyle(readOnlyStyle);
                
                Cell deptCell = row.createCell(2);
                deptCell.setCellValue(emp.getDepartment() != null ? emp.getDepartment() : "");
                deptCell.setCellStyle(readOnlyStyle);
                
                Cell currentDeviceCell = row.createCell(3);
                String currentDevice = emp.getBiometricDevice() != null 
                        ? emp.getBiometricDevice().getDeviceCode() + " - " + emp.getBiometricDevice().getDeviceName()
                        : "Not Assigned";
                currentDeviceCell.setCellValue(currentDevice);
                currentDeviceCell.setCellStyle(readOnlyStyle);
                
                // Editable columns (yellow background)
                Cell newDeviceCell = row.createCell(4);
                newDeviceCell.setCellValue(emp.getBiometricDevice() != null 
                        ? emp.getBiometricDevice().getDeviceCode() : "");
                newDeviceCell.setCellStyle(editableStyle);
                
                Cell deviceEmpCodeCell = row.createCell(5);
                deviceEmpCodeCell.setCellValue(emp.getDeviceEmpCode() != null 
                        ? emp.getDeviceEmpCode() : "");
                deviceEmpCodeCell.setCellStyle(editableStyle);
            }

            // Add devices reference sheet
            addDevicesSheet(workbook, devices);
            
            // Add instructions sheet
            addInstructionsSheet(workbook, devices);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Import biometric associations from Excel file.
     */
    @Transactional
    public Map<String, Object> importAssociations(MultipartFile file) throws IOException {
        Map<String, Object> result = new HashMap<>();
        String tenantId = TenantContext.getTenantId();
        
        if (tenantId == null || tenantId.isEmpty()) {
            result.put("success", false);
            result.put("error", "Tenant context not set");
            return result;
        }

        // Pre-load devices for quick lookup
        List<BiometricDevice> devices = deviceRepo.findByTenantIdOrderByDeviceCodeAsc(tenantId);
        Map<String, BiometricDevice> deviceMap = new HashMap<>();
        for (BiometricDevice d : devices) {
            deviceMap.put(d.getDeviceCode().toUpperCase(), d);
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

                // Get new device code (column 4)
                String newDeviceCode = getCellStringValue(row.getCell(4));
                if (newDeviceCode != null && !newDeviceCode.trim().isEmpty()) {
                    BiometricDevice device = deviceMap.get(newDeviceCode.trim().toUpperCase());
                    if (device == null) {
                        errors.add("Row " + rowNum + ": Device not found: " + newDeviceCode);
                        continue;
                    }
                    emp.setBiometricDevice(device);
                    updated = true;
                } else {
                    // Clear device assignment if empty
                    if (emp.getBiometricDevice() != null) {
                        emp.setBiometricDevice(null);
                        updated = true;
                    }
                }

                // Get device emp code (column 5)
                String deviceEmpCode = getCellStringValue(row.getCell(5));
                if (deviceEmpCode != null && !deviceEmpCode.trim().isEmpty()) {
                    emp.setDeviceEmpCode(deviceEmpCode.trim());
                    updated = true;
                } else if (emp.getDeviceEmpCode() != null) {
                    emp.setDeviceEmpCode(null);
                    updated = true;
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

    private void addDevicesSheet(Workbook workbook, List<BiometricDevice> devices) {
        Sheet deviceSheet = workbook.createSheet("Available Devices");
        
        CellStyle headerStyle = createHeaderStyle(workbook);
        
        Row headerRow = deviceSheet.createRow(0);
        String[] headers = {"Device Code", "Device Name", "Location", "Status"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            deviceSheet.setColumnWidth(i, 25 * 256);
        }
        
        int rowNum = 1;
        for (BiometricDevice device : devices) {
            Row row = deviceSheet.createRow(rowNum++);
            row.createCell(0).setCellValue(device.getDeviceCode());
            row.createCell(1).setCellValue(device.getDeviceName());
            row.createCell(2).setCellValue(device.getLocation() != null ? device.getLocation() : "");
            row.createCell(3).setCellValue(Boolean.TRUE.equals(device.getIsActive()) ? "Active" : "Inactive");
        }
    }

    private void addInstructionsSheet(Workbook workbook, List<BiometricDevice> devices) {
        Sheet instructionSheet = workbook.createSheet("Instructions");
        int rowNum = 0;
        instructionSheet.createRow(rowNum++).createCell(0).setCellValue("BIOMETRIC DEVICE ASSOCIATION TEMPLATE");
        rowNum++;
        instructionSheet.createRow(rowNum++).createCell(0).setCellValue("Instructions:");
        instructionSheet.createRow(rowNum++).createCell(0).setCellValue("1. Gray columns are READ-ONLY (for reference)");
        instructionSheet.createRow(rowNum++).createCell(0).setCellValue("2. Yellow columns are EDITABLE");
        instructionSheet.createRow(rowNum++).createCell(0).setCellValue("3. 'New Device Code' - Enter the device code from 'Available Devices' sheet");
        instructionSheet.createRow(rowNum++).createCell(0).setCellValue("4. 'Device Emp Code' - Employee code used in the biometric device (if different from HRMS emp code)");
        instructionSheet.createRow(rowNum++).createCell(0).setCellValue("5. Leave 'New Device Code' empty to remove device assignment");
        rowNum++;
        instructionSheet.createRow(rowNum++).createCell(0).setCellValue("Available Device Codes:");
        for (BiometricDevice d : devices) {
            instructionSheet.createRow(rowNum++).createCell(0).setCellValue("  - " + d.getDeviceCode() + " (" + d.getDeviceName() + ")");
        }
        instructionSheet.setColumnWidth(0, 80 * 256);
    }
}
