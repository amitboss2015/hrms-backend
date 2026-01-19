package com.example.hrms.attendance.controller;

import com.example.hrms.attendance.domain.BiometricDevice;
import com.example.hrms.attendance.domain.BiometricDeviceMapping;
import com.example.hrms.attendance.repo.BiometricDeviceRepository;
import com.example.hrms.attendance.repo.BiometricDeviceMappingRepository;
import com.example.hrms.domain.Employee;
import com.example.hrms.repo.EmployeeRepository;
import com.example.hrms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for managing biometric devices and their employee mappings.
 * Supports multi-device attendance tracking with different employee codes per device.
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Slf4j
public class BiometricDeviceController {
    
    private final BiometricDeviceRepository deviceRepo;
    private final BiometricDeviceMappingRepository mappingRepo;
    private final EmployeeRepository employeeRepo;
    
    // ==================== DEVICE CRUD ====================
    
    /**
     * List all biometric devices for the current tenant
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listDevices(
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        String tenantId = TenantContext.getTenantId();
        
        List<BiometricDevice> devices = activeOnly 
            ? deviceRepo.findByTenantIdAndIsActiveTrueOrderByDeviceCodeAsc(tenantId)
            : deviceRepo.findByTenantIdOrderByDeviceCodeAsc(tenantId);
        
        List<Map<String, Object>> result = devices.stream().map(d -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", d.getId());
            map.put("deviceCode", d.getDeviceCode());
            map.put("deviceName", d.getDeviceName());
            map.put("location", d.getLocation());
            map.put("description", d.getDescription());
            map.put("serialNumber", d.getSerialNumber());
            map.put("isActive", d.getIsActive());
            map.put("isDefault", d.getIsDefault());
            map.put("mappingCount", mappingRepo.countByDeviceId(d.getId()));
            map.put("createdAt", d.getCreatedAt());
            map.put("updatedAt", d.getUpdatedAt());
            return map;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Get a specific device by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getDevice(@PathVariable Long id) {
        String tenantId = TenantContext.getTenantId();
        
        return deviceRepo.findById(id)
            .filter(d -> d.getTenantId().equals(tenantId))
            .map(d -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", d.getId());
                map.put("deviceCode", d.getDeviceCode());
                map.put("deviceName", d.getDeviceName());
                map.put("location", d.getLocation());
                map.put("description", d.getDescription());
                map.put("serialNumber", d.getSerialNumber());
                map.put("isActive", d.getIsActive());
                map.put("isDefault", d.getIsDefault());
                map.put("createdAt", d.getCreatedAt());
                map.put("updatedAt", d.getUpdatedAt());
                return ResponseEntity.ok(map);
            })
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Create a new biometric device
     */
    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createDevice(@RequestBody Map<String, Object> request) {
        String tenantId = TenantContext.getTenantId();
        
        String deviceCode = (String) request.get("deviceCode");
        if (deviceCode == null || deviceCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Device code is required"));
        }
        
        deviceCode = deviceCode.trim().toUpperCase();
        
        // Check for duplicate
        if (deviceRepo.existsByTenantIdAndDeviceCode(tenantId, deviceCode)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Device code already exists"));
        }
        
        // Check if this should be the default device
        boolean isDefault = Boolean.TRUE.equals(request.get("isDefault"));
        boolean isFirstDevice = deviceRepo.countByTenantId(tenantId) == 0;
        
        // If this is the first device, make it default
        if (isFirstDevice) {
            isDefault = true;
        }
        
        // If setting as default, unset any existing default
        if (isDefault) {
            deviceRepo.findByTenantIdAndIsDefaultTrue(tenantId).ifPresent(existing -> {
                existing.setIsDefault(false);
                deviceRepo.save(existing);
            });
        }
        
        BiometricDevice device = BiometricDevice.builder()
            .tenantId(tenantId)
            .deviceCode(deviceCode)
            .deviceName((String) request.get("deviceName"))
            .location((String) request.get("location"))
            .description((String) request.get("description"))
            .serialNumber((String) request.get("serialNumber"))
            .isActive(request.get("isActive") != null ? (Boolean) request.get("isActive") : true)
            .isDefault(isDefault)
            .build();
        
        device = deviceRepo.save(device);
        log.info("Created biometric device: {} for tenant {}", deviceCode, tenantId);
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", device.getId());
        result.put("deviceCode", device.getDeviceCode());
        result.put("deviceName", device.getDeviceName());
        result.put("isDefault", device.getIsDefault());
        result.put("message", "Device created successfully");
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Update a biometric device
     */
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateDevice(
            @PathVariable Long id, 
            @RequestBody Map<String, Object> request) {
        String tenantId = TenantContext.getTenantId();
        
        Optional<BiometricDevice> deviceOpt = deviceRepo.findById(id)
            .filter(d -> d.getTenantId().equals(tenantId));
        
        if (deviceOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        BiometricDevice device = deviceOpt.get();
        
        if (request.containsKey("deviceName")) {
            device.setDeviceName((String) request.get("deviceName"));
        }
        if (request.containsKey("location")) {
            device.setLocation((String) request.get("location"));
        }
        if (request.containsKey("description")) {
            device.setDescription((String) request.get("description"));
        }
        if (request.containsKey("serialNumber")) {
            device.setSerialNumber((String) request.get("serialNumber"));
        }
        if (request.containsKey("isActive")) {
            device.setIsActive((Boolean) request.get("isActive"));
        }
        if (Boolean.TRUE.equals(request.get("isDefault"))) {
            // Unset any existing default
            deviceRepo.findByTenantIdAndIsDefaultTrue(tenantId).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    existing.setIsDefault(false);
                    deviceRepo.save(existing);
                }
            });
            device.setIsDefault(true);
        }
        
        deviceRepo.save(device);
        log.info("Updated biometric device: {} for tenant {}", device.getDeviceCode(), tenantId);
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", device.getId());
        result.put("deviceCode", device.getDeviceCode());
        result.put("message", "Device updated successfully");
        return ResponseEntity.ok(result);
    }
    
    /**
     * Delete a biometric device
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteDevice(@PathVariable Long id) {
        String tenantId = TenantContext.getTenantId();
        
        Optional<BiometricDevice> deviceOpt = deviceRepo.findById(id)
            .filter(d -> d.getTenantId().equals(tenantId));
        
        if (deviceOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        BiometricDevice device = deviceOpt.get();
        
        // Check if device has mappings
        long mappingCount = mappingRepo.countByDeviceId(id);
        if (mappingCount > 0) {
            Map<String, Object> errorResult = new LinkedHashMap<>();
            errorResult.put("error", "Cannot delete device with existing mappings");
            errorResult.put("mappingCount", mappingCount);
            errorResult.put("message", "Please remove all employee mappings first or deactivate the device instead");
            return ResponseEntity.badRequest().body(errorResult);
        }
        
        deviceRepo.delete(device);
        log.info("Deleted biometric device: {} for tenant {}", device.getDeviceCode(), tenantId);
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Device deleted successfully");
        return ResponseEntity.ok(result);
    }
    
    // ==================== DEVICE MAPPINGS ====================
    
    /**
     * List all mappings for a device
     */
    @GetMapping("/{deviceId}/mappings")
    public ResponseEntity<List<Map<String, Object>>> listMappings(@PathVariable Long deviceId) {
        String tenantId = TenantContext.getTenantId();
        
        // Verify device belongs to tenant
        if (!deviceRepo.findById(deviceId).filter(d -> d.getTenantId().equals(tenantId)).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        List<BiometricDeviceMapping> mappings = mappingRepo.findByTenantIdAndDeviceIdOrderByDeviceEmpCodeAsc(tenantId, deviceId);
        
        List<Map<String, Object>> result = mappings.stream().map(m -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("deviceEmpCode", m.getDeviceEmpCode());
            map.put("deviceEmpName", m.getDeviceEmpName());
            map.put("employeeId", m.getEmployee().getId());
            map.put("hrmsEmpCode", m.getEmployee().getEmpCode());
            map.put("employeeName", m.getEmployee().getFirstName() + 
                (m.getEmployee().getLastName() != null ? " " + m.getEmployee().getLastName() : ""));
            map.put("isActive", m.getIsActive());
            map.put("createdAt", m.getCreatedAt());
            return map;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Add a mapping to a device
     */
    @PostMapping("/{deviceId}/mappings")
    @Transactional
    public ResponseEntity<Map<String, Object>> addMapping(
            @PathVariable Long deviceId,
            @RequestBody Map<String, Object> request) {
        String tenantId = TenantContext.getTenantId();
        
        // Verify device belongs to tenant
        BiometricDevice device = deviceRepo.findById(deviceId)
            .filter(d -> d.getTenantId().equals(tenantId))
            .orElse(null);
        
        if (device == null) {
            return ResponseEntity.notFound().build();
        }
        
        String deviceEmpCode = (String) request.get("deviceEmpCode");
        if (deviceEmpCode == null || deviceEmpCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Device employee code is required"));
        }
        deviceEmpCode = deviceEmpCode.trim();
        
        // Get employee - can be by ID or by HRMS emp code
        Long employeeId = null;
        if (request.get("employeeId") != null) {
            employeeId = Long.valueOf(request.get("employeeId").toString());
        } else if (request.get("hrmsEmpCode") != null) {
            String hrmsCode = request.get("hrmsEmpCode").toString().trim();
            employeeId = employeeRepo.findByTenantIdAndEmpCode(tenantId, hrmsCode)
                .map(Employee::getId)
                .orElse(null);
        }
        
        if (employeeId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Employee not found"));
        }
        
        Employee employee = employeeRepo.findById(employeeId)
            .filter(e -> e.getTenantId().equals(tenantId))
            .orElse(null);
        
        if (employee == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Employee not found in this tenant"));
        }
        
        // Check for duplicate mapping
        if (mappingRepo.existsByTenantIdAndDeviceIdAndDeviceEmpCode(tenantId, deviceId, deviceEmpCode)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "This device code is already mapped",
                "message", "Device code '" + deviceEmpCode + "' is already assigned to another employee on this device"
            ));
        }
        
        BiometricDeviceMapping mapping = BiometricDeviceMapping.builder()
            .tenantId(tenantId)
            .device(device)
            .deviceEmpCode(deviceEmpCode)
            .employee(employee)
            .deviceEmpName((String) request.get("deviceEmpName"))
            .isActive(true)
            .build();
        
        mapping = mappingRepo.save(mapping);
        log.info("Created device mapping: device={}, deviceCode={}, employee={}", 
            device.getDeviceCode(), deviceEmpCode, employee.getEmpCode());
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", mapping.getId());
        result.put("deviceEmpCode", mapping.getDeviceEmpCode());
        result.put("employeeId", employee.getId());
        result.put("hrmsEmpCode", employee.getEmpCode());
        result.put("employeeName", employee.getFirstName() + 
            (employee.getLastName() != null ? " " + employee.getLastName() : ""));
        result.put("message", "Mapping created successfully");
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Delete a mapping
     */
    @DeleteMapping("/{deviceId}/mappings/{mappingId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteMapping(
            @PathVariable Long deviceId,
            @PathVariable Long mappingId) {
        String tenantId = TenantContext.getTenantId();
        
        Optional<BiometricDeviceMapping> mappingOpt = mappingRepo.findById(mappingId)
            .filter(m -> m.getTenantId().equals(tenantId) && m.getDevice().getId().equals(deviceId));
        
        if (mappingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        mappingRepo.delete(mappingOpt.get());
        log.info("Deleted device mapping: id={}", mappingId);
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Mapping deleted successfully");
        return ResponseEntity.ok(result);
    }
    
    /**
     * Bulk import mappings from Excel/CSV data
     */
    @PostMapping("/{deviceId}/mappings/bulk")
    @Transactional
    public ResponseEntity<Map<String, Object>> bulkImportMappings(
            @PathVariable Long deviceId,
            @RequestBody List<Map<String, String>> mappings) {
        String tenantId = TenantContext.getTenantId();
        
        BiometricDevice device = deviceRepo.findById(deviceId)
            .filter(d -> d.getTenantId().equals(tenantId))
            .orElse(null);
        
        if (device == null) {
            return ResponseEntity.notFound().build();
        }
        
        int created = 0, updated = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        
        for (Map<String, String> row : mappings) {
            String deviceEmpCode = row.get("deviceEmpCode");
            String hrmsEmpCode = row.get("hrmsEmpCode");
            String deviceEmpName = row.get("deviceEmpName");
            
            if (deviceEmpCode == null || deviceEmpCode.isBlank() || hrmsEmpCode == null || hrmsEmpCode.isBlank()) {
                failed++;
                errors.add("Missing required fields: deviceEmpCode or hrmsEmpCode");
                continue;
            }
            
            deviceEmpCode = deviceEmpCode.trim();
            hrmsEmpCode = hrmsEmpCode.trim();
            
            // Find employee
            Optional<Employee> empOpt = employeeRepo.findByTenantIdAndEmpCode(tenantId, hrmsEmpCode);
            if (empOpt.isEmpty()) {
                failed++;
                errors.add("Employee not found: " + hrmsEmpCode);
                continue;
            }
            
            Employee employee = empOpt.get();
            
            // Check if mapping exists
            Optional<BiometricDeviceMapping> existingOpt = mappingRepo.findActiveMapping(tenantId, deviceId, deviceEmpCode);
            
            if (existingOpt.isPresent()) {
                // Update existing
                BiometricDeviceMapping existing = existingOpt.get();
                existing.setEmployee(employee);
                existing.setDeviceEmpName(deviceEmpName);
                mappingRepo.save(existing);
                updated++;
            } else {
                // Create new
                BiometricDeviceMapping mapping = BiometricDeviceMapping.builder()
                    .tenantId(tenantId)
                    .device(device)
                    .deviceEmpCode(deviceEmpCode)
                    .employee(employee)
                    .deviceEmpName(deviceEmpName)
                    .isActive(true)
                    .build();
                mappingRepo.save(mapping);
                created++;
            }
        }
        
        log.info("Bulk import mappings for device {}: created={}, updated={}, failed={}", 
            device.getDeviceCode(), created, updated, failed);
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("updated", updated);
        result.put("failed", failed);
        result.put("errors", errors);
        result.put("message", String.format("Processed %d mappings: %d created, %d updated, %d failed", 
            mappings.size(), created, updated, failed));
        
        return ResponseEntity.ok(result);
    }
    
    // ==================== UTILITY ENDPOINTS ====================
    
    /**
     * Get default device for the tenant
     */
    @GetMapping("/default")
    public ResponseEntity<Map<String, Object>> getDefaultDevice() {
        String tenantId = TenantContext.getTenantId();
        
        return deviceRepo.findByTenantIdAndIsDefaultTrue(tenantId)
            .map(d -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", d.getId());
                map.put("deviceCode", d.getDeviceCode());
                map.put("deviceName", d.getDeviceName());
                map.put("location", d.getLocation());
                return ResponseEntity.ok(map);
            })
            .orElse(ResponseEntity.ok(Map.of("message", "No default device configured")));
    }
    
    /**
     * Get or create a default device (for backward compatibility)
     * This is called automatically when a tenant tries to import attendance without any devices configured.
     */
    @PostMapping("/ensure-default")
    @Transactional
    public ResponseEntity<Map<String, Object>> ensureDefaultDevice() {
        String tenantId = TenantContext.getTenantId();
        
        // Check if any device exists
        Optional<BiometricDevice> existing = deviceRepo.findByTenantIdAndIsDefaultTrue(tenantId);
        if (existing.isPresent()) {
            BiometricDevice d = existing.get();
            return ResponseEntity.ok(Map.of(
                "id", d.getId(),
                "deviceCode", d.getDeviceCode(),
                "deviceName", d.getDeviceName(),
                "existed", true
            ));
        }
        
        // Create a default device
        BiometricDevice defaultDevice = BiometricDevice.builder()
            .tenantId(tenantId)
            .deviceCode("DEFAULT")
            .deviceName("Default Device")
            .description("Auto-created default device for backward compatibility")
            .isActive(true)
            .isDefault(true)
            .build();
        
        defaultDevice = deviceRepo.save(defaultDevice);
        log.info("Created default device for tenant {}", tenantId);
        
        return ResponseEntity.ok(Map.of(
            "id", defaultDevice.getId(),
            "deviceCode", defaultDevice.getDeviceCode(),
            "deviceName", defaultDevice.getDeviceName(),
            "existed", false,
            "message", "Default device created"
        ));
    }
    
    /**
     * Lookup employee by device code and employee code
     * Used for testing/debugging mapping resolution
     */
    @GetMapping("/lookup")
    public ResponseEntity<Map<String, Object>> lookupEmployee(
            @RequestParam String deviceCode,
            @RequestParam String empCode) {
        String tenantId = TenantContext.getTenantId();
        
        Optional<BiometricDeviceMapping> mapping = mappingRepo.findByDeviceCodeAndEmpCode(tenantId, deviceCode, empCode);
        
        if (mapping.isPresent()) {
            BiometricDeviceMapping m = mapping.get();
            Employee e = m.getEmployee();
            return ResponseEntity.ok(Map.of(
                "found", true,
                "mappingId", m.getId(),
                "employeeId", e.getId(),
                "hrmsEmpCode", e.getEmpCode(),
                "employeeName", e.getFirstName() + (e.getLastName() != null ? " " + e.getLastName() : ""),
                "deviceCode", deviceCode,
                "deviceEmpCode", empCode
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                "found", false,
                "deviceCode", deviceCode,
                "deviceEmpCode", empCode,
                "message", "No mapping found for this device and employee code"
            ));
        }
    }
}
