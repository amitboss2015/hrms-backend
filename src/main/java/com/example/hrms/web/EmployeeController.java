package com.example.hrms.web;

import com.example.hrms.attendance.repo.BiometricDeviceRepository;
import com.example.hrms.domain.Employee;
import com.example.hrms.dto.EmployeeDTO;
import com.example.hrms.dto.EmployeeImportResult;
import com.example.hrms.service.EmployeeService;
import com.example.hrms.service.excel.EmployeeExcelService;
import com.example.hrms.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {
    private final EmployeeService service;
    private final EmployeeExcelService excelService;
    private final BiometricDeviceRepository deviceRepo;
    
    public EmployeeController(EmployeeService service, EmployeeExcelService excelService, 
                               BiometricDeviceRepository deviceRepo) { 
        this.service = service; 
        this.excelService = excelService;
        this.deviceRepo = deviceRepo;
    }

    @GetMapping
    public List<Employee> list(){ return service.list(); }

    @GetMapping("/{empCode}")
    public ResponseEntity<Employee> get(@PathVariable("empCode") String empCode){
        return service.getByEmpCode(empCode).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create new employee with validation
     */
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody EmployeeDTO dto, BindingResult bindingResult){
        // Check for validation errors
        if (bindingResult.hasErrors()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Validation failed");
            
            Map<String, String> fieldErrors = new HashMap<>();
            for (FieldError error : bindingResult.getFieldErrors()) {
                fieldErrors.put(error.getField(), error.getDefaultMessage());
            }
            response.put("errors", fieldErrors);
            
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            Employee employee = dto.toEntity();
            
            // Handle biometric device assignment
            assignDeviceFromDto(employee, dto);
            
            Employee saved = service.upsert(employee);
            return ResponseEntity.created(URI.create("/api/employees/" + saved.getEmpCode())).body(saved);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Helper to assign biometric device from DTO
     */
    private void assignDeviceFromDto(Employee employee, EmployeeDTO dto) {
        String tenantId = TenantContext.getTenantId();
        
        // If device ID is provided, use it
        if (dto.getBiometricDeviceId() != null) {
            deviceRepo.findById(dto.getBiometricDeviceId())
                .filter(d -> d.getTenantId().equals(tenantId)) // Security: verify tenant
                .ifPresent(employee::setBiometricDevice);
        }
        
        // Handle device employee code
        if (dto.isUseEmpCodeAsDeviceCode()) {
            employee.setDeviceEmpCode(null); // Will use empCode as fallback
        } else if (dto.getDeviceEmpCode() != null && !dto.getDeviceEmpCode().isEmpty()) {
            employee.setDeviceEmpCode(dto.getDeviceEmpCode());
        }
    }

    /**
     * Update employee with validation
     */
    @PutMapping("/{empCode}")
    public ResponseEntity<?> update(@PathVariable("empCode") String empCode,
                                    @Valid @RequestBody EmployeeDTO dto, 
                                    BindingResult bindingResult) {
        // Check for validation errors
        if (bindingResult.hasErrors()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Validation failed");
            
            Map<String, String> fieldErrors = new HashMap<>();
            for (FieldError error : bindingResult.getFieldErrors()) {
                fieldErrors.put(error.getField(), error.getDefaultMessage());
            }
            response.put("errors", fieldErrors);
            
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            dto.setEmpCode(empCode);
            Employee employee = dto.toEntity();
            
            // Handle biometric device assignment
            assignDeviceFromDto(employee, dto);
            
            Employee saved = service.upsert(employee);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/{empCode}")
    public ResponseEntity<Void> delete(@PathVariable("empCode") String empCode){
        service.delete(empCode);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk-upload")
    public List<Employee> bulkUpload(@RequestBody List<Employee> list){
        return service.bulkUpsert(list);
    }

    /**
     * Download employee import template (Excel)
     * @param full If true, includes all fields. If false, only minimum required fields.
     */
    @GetMapping("/template/download")
    public ResponseEntity<byte[]> downloadTemplate(
            @RequestParam(value = "full", defaultValue = "false") boolean full) {
        try {
            byte[] content = excelService.generateTemplate(full);
            
            String filename = full ? "employee_import_template_full.xlsx" : "employee_import_template.xlsx";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(content.length);
            headers.setCacheControl(CacheControl.noCache().getHeaderValue());
            
            return new ResponseEntity<>(content, headers, HttpStatus.OK);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Import employees from Excel file with validation
     * @param file The Excel file to import
     * @param deviceId Optional biometric device ID to associate employees with
     */
    @PostMapping("/import")
    public ResponseEntity<EmployeeImportResult> importEmployees(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "deviceId", required = false) Long deviceId) {
        // Validate file
        if (file.isEmpty()) {
            EmployeeImportResult result = new EmployeeImportResult();
            result.setSuccess(false);
            result.setMessage("Please select a file to upload");
            return ResponseEntity.badRequest().body(result);
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            EmployeeImportResult result = new EmployeeImportResult();
            result.setSuccess(false);
            result.setMessage("Please upload an Excel file (.xlsx or .xls)");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            EmployeeImportResult result = excelService.importFromExcel(file, deviceId);
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(result);
            }
        } catch (IOException e) {
            EmployeeImportResult result = new EmployeeImportResult();
            result.setSuccess(false);
            result.setMessage("Error reading Excel file: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            EmployeeImportResult result = new EmployeeImportResult();
            result.setSuccess(false);
            result.setMessage("Error importing employees: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * Legacy import endpoint - redirects to new endpoint with null deviceId
     */
    @PostMapping("/import-excel")
    public ResponseEntity<EmployeeImportResult> importExcelLegacy(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "deviceId", required = false) Long deviceId) {
        return importEmployees(file, deviceId);
    }

    /**
     * Validate a single employee (for frontend validation)
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateEmployee(@Valid @RequestBody EmployeeDTO dto, BindingResult bindingResult) {
        Map<String, Object> response = new HashMap<>();
        
        if (bindingResult.hasErrors()) {
            response.put("valid", false);
            
            Map<String, String> fieldErrors = new HashMap<>();
            for (FieldError error : bindingResult.getFieldErrors()) {
                fieldErrors.put(error.getField(), error.getDefaultMessage());
            }
            response.put("errors", fieldErrors);
        } else {
            // Additional business validation
            List<String> businessErrors = new ArrayList<>();
            
            // Check if emp code already exists
            if (dto.getEmpCode() != null && service.getByEmpCode(dto.getEmpCode()).isPresent()) {
                businessErrors.add("Employee code already exists: " + dto.getEmpCode());
            }
            
            if (!businessErrors.isEmpty()) {
                response.put("valid", false);
                response.put("businessErrors", businessErrors);
            } else {
                response.put("valid", true);
                response.put("message", "Employee data is valid");
            }
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get validation rules (for frontend to display hints)
     */
    @GetMapping("/validation-rules")
    public ResponseEntity<?> getValidationRules() {
        Map<String, Object> rules = new LinkedHashMap<>();
        
        rules.put("empCode", Map.of(
            "required", true,
            "minLength", 1,
            "maxLength", 20,
            "pattern", "^[A-Za-z0-9_-]+$",
            "hint", "Letters, numbers, underscores and hyphens only"
        ));
        
        rules.put("firstName", Map.of(
            "required", true,
            "minLength", 2,
            "maxLength", 50,
            "pattern", "^[A-Za-z\\s.'-]+$",
            "hint", "Letters, spaces, dots, apostrophes and hyphens only"
        ));
        
        rules.put("lastName", Map.of(
            "required", false,
            "maxLength", 50,
            "pattern", "^[A-Za-z\\s.'-]*$",
            "hint", "Letters, spaces, dots, apostrophes and hyphens only"
        ));
        
        rules.put("phone", Map.of(
            "required", false,
            "pattern", "^[6-9]\\d{9}$",
            "hint", "10-digit Indian mobile number starting with 6-9"
        ));
        
        rules.put("email", Map.of(
            "required", false,
            "pattern", "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
            "hint", "Valid email address"
        ));
        
        rules.put("pincode", Map.of(
            "required", false,
            "pattern", "^[1-9][0-9]{5}$",
            "hint", "6-digit Indian pincode"
        ));
        
        rules.put("aadhaar", Map.of(
            "required", false,
            "pattern", "^[2-9]{1}[0-9]{3}\\s?[0-9]{4}\\s?[0-9]{4}$",
            "hint", "12-digit Aadhaar number"
        ));
        
        rules.put("pan", Map.of(
            "required", false,
            "pattern", "^[A-Z]{5}[0-9]{4}[A-Z]{1}$",
            "hint", "PAN format: ABCDE1234F"
        ));
        
        rules.put("ifsc", Map.of(
            "required", false,
            "pattern", "^[A-Z]{4}0[A-Z0-9]{6}$",
            "hint", "IFSC format: SBIN0001234"
        ));
        
        rules.put("employmentType", Map.of(
            "required", false,
            "options", List.of("FULL_TIME", "PART_TIME", "CONTRACT", "INTERN"),
            "default", "FULL_TIME"
        ));
        
        rules.put("salaryBasis", Map.of(
            "required", false,
            "options", List.of("MONTHLY", "HOURLY", "DAILY"),
            "default", "MONTHLY"
        ));
        
        rules.put("status", Map.of(
            "required", false,
            "options", List.of("ACTIVE", "INACTIVE", "TERMINATED", "ON_LEAVE"),
            "default", "ACTIVE"
        ));
        
        return ResponseEntity.ok(rules);
    }
    
    /**
     * Get employee code mapping (device codes → system codes).
     * This helps users understand which device codes map to which system-generated codes.
     * Useful for attendance import when users need to know the mapping.
     * 
     * @param deviceId Optional device ID to filter by specific device
     * @return List of employee code mappings with device code, system code, name, and device info
     */
    @GetMapping("/code-mapping")
    public ResponseEntity<List<Map<String, Object>>> getCodeMapping(
            @RequestParam(value = "deviceId", required = false) Long deviceId) {
        
        String tenantId = TenantContext.getTenantId();
        List<Employee> employees;
        
        if (deviceId != null) {
            // Filter by specific device
            employees = deviceRepo.findById(deviceId)
                    .filter(d -> d.getTenantId().equals(tenantId))
                    .map(d -> service.list().stream()
                            .filter(e -> e.getBiometricDevice() != null && 
                                    e.getBiometricDevice().getId().equals(deviceId))
                            .collect(java.util.stream.Collectors.toList()))
                    .orElse(Collections.emptyList());
        } else {
            // Get all employees for tenant
            employees = service.list();
        }
        
        List<Map<String, Object>> mappings = new ArrayList<>();
        for (Employee emp : employees) {
            Map<String, Object> mapping = new LinkedHashMap<>();
            mapping.put("employeeId", emp.getId());
            mapping.put("systemCode", emp.getEmpCode());
            mapping.put("deviceEmpCode", emp.getEffectiveDeviceEmpCode()); // Employee's code in the device
            mapping.put("firstName", emp.getFirstName());
            mapping.put("lastName", emp.getLastName());
            mapping.put("fullName", emp.getFirstName() + 
                    (emp.getLastName() != null ? " " + emp.getLastName() : ""));
            
            // Device information
            if (emp.getBiometricDevice() != null) {
                mapping.put("deviceId", emp.getBiometricDevice().getId());
                mapping.put("deviceCode", emp.getBiometricDevice().getDeviceCode()); // Device's code/name
                mapping.put("deviceName", emp.getBiometricDevice().getDeviceName());
            } else {
                mapping.put("deviceId", null);
                mapping.put("deviceCode", null);
                mapping.put("deviceName", "No Device Assigned");
            }
            
            // Status
            mapping.put("status", emp.getStatus() != null ? emp.getStatus().toString() : "ACTIVE");
            
            mappings.add(mapping);
        }
        
        // Sort by system code for easier lookup
        mappings.sort((a, b) -> {
            String codeA = (String) a.get("systemCode");
            String codeB = (String) b.get("systemCode");
            if (codeA == null) return 1;
            if (codeB == null) return -1;
            return codeA.compareTo(codeB);
        });
        
        return ResponseEntity.ok(mappings);
    }
}
