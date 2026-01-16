package com.example.hrms.service;

import com.example.hrms.domain.Employee;
import com.example.hrms.domain.enums.EmployeeStatus;
import com.example.hrms.repo.EmployeeRepository;
import com.example.hrms.service.excel.EmployeeExcelImporter;
import com.example.hrms.tenant.TenantContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Service
public class EmployeeService {
    private final EmployeeRepository repo;
    
    public EmployeeService(EmployeeRepository repo) { 
        this.repo = repo; 
    }

    /**
     * List all employees for current tenant
     */
    public List<Employee> list() {
        String tenantId = TenantContext.getTenantIdOrDefault("ORG001");
        return repo.findByTenantId(tenantId);
    }
    
    /**
     * List all employees (legacy - for backward compatibility)
     */
    public List<Employee> listAll() {
        return repo.findAll();
    }

    /**
     * Get employee by code for current tenant
     */
    public Optional<Employee> getByEmpCode(String empCode) {
        String tenantId = TenantContext.getTenantIdOrDefault("ORG001");
        Optional<Employee> result = repo.findByTenantIdAndEmpCode(tenantId, empCode);
        // Fallback for backward compatibility
        if (result.isEmpty()) {
            result = repo.findByEmpCode(empCode);
        }
        return result;
    }

    /**
     * Upsert employee with tenant support
     */
    @Transactional
    public Employee upsert(Employee e) {
        String tenantId = TenantContext.getTenantIdOrDefault("ORG001");
        
        // Set tenant ID if not already set
        if (e.getTenantId() == null || e.getTenantId().isEmpty()) {
            e.setTenantId(tenantId);
        }
        
        return repo.findByTenantIdAndEmpCode(tenantId, e.getEmpCode()).map(cur -> {
            cur.setFirstName(e.getFirstName()); 
            cur.setLastName(e.getLastName());
            cur.setEmploymentType(e.getEmploymentType());
            cur.setDepartment(e.getDepartment()); 
            cur.setDesignation(e.getDesignation());
            cur.setSalaryBasis(e.getSalaryBasis()); 
            cur.setBaseSalary(e.getBaseSalary()); 
            cur.setHourlyRate(e.getHourlyRate());
            cur.setJoinDate(e.getJoinDate()); 
            cur.setStatus(e.getStatus());
            cur.setEmail(e.getEmail()); 
            cur.setPhone(e.getPhone()); 
            cur.setAddress(e.getAddress());
            cur.setCity(e.getCity()); 
            cur.setState(e.getState()); 
            cur.setPincode(e.getPincode());
            cur.setAadhaar(e.getAadhaar()); 
            cur.setPan(e.getPan()); 
            cur.setBankAccount(e.getBankAccount()); 
            cur.setIfsc(e.getIfsc());
            cur.setEmergencyContactName(e.getEmergencyContactName()); 
            cur.setEmergencyContactPhone(e.getEmergencyContactPhone());
            cur.setOtAllowed(e.isOtAllowed()); 
            cur.setOtDurationMinutes(e.getOtDurationMinutes());
            // Additional fields
            cur.setIncrement(e.getIncrement());
            cur.setUanNumber(e.getUanNumber());
            cur.setEsicNumber(e.getEsicNumber());
            cur.setBankName(e.getBankName());
            cur.setBranchName(e.getBranchName());
            cur.setHraPercent(e.getHraPercent());
            cur.setDaPercent(e.getDaPercent());
            cur.setConveyanceAllowance(e.getConveyanceAllowance());
            cur.setMedicalAllowance(e.getMedicalAllowance());
            cur.setSpecialAllowance(e.getSpecialAllowance());
            cur.setOtherAllowance(e.getOtherAllowance());
            cur.setEpfApplicable(e.getEpfApplicable());
            cur.setEsicApplicable(e.getEsicApplicable());
            cur.setPtApplicable(e.getPtApplicable());
            cur.setTdsApplicable(e.getTdsApplicable());
            cur.setWeeklyOffDays(e.getWeeklyOffDays());
            cur.setStandardWorkingHoursPerDay(e.getStandardWorkingHoursPerDay());
            cur.setWorkingDaysPerMonth(e.getWorkingDaysPerMonth());
            return repo.save(cur);
        }).orElseGet(() -> repo.save(e));
    }

    /**
     * Delete employee by code for current tenant
     */
    @Transactional
    public void delete(String empCode) {
        String tenantId = TenantContext.getTenantIdOrDefault("ORG001");
        repo.deleteByTenantIdAndEmpCode(tenantId, empCode);
    }

    /**
     * Bulk upsert with tenant support
     */
    @Transactional
    public List<Employee> bulkUpsert(List<Employee> employees) {
        String tenantId = TenantContext.getTenantIdOrDefault("ORG001");
        employees.forEach(e -> {
            if (e.getTenantId() == null || e.getTenantId().isEmpty()) {
                e.setTenantId(tenantId);
            }
        });
        return repo.saveAll(employees);
    }

    /**
     * Import employees from Excel with tenant support
     */
    @Transactional
    public List<Employee> importExcel(MultipartFile file) throws Exception {
        String tenantId = TenantContext.getTenantIdOrDefault("ORG001");
        List<Employee> employees = EmployeeExcelImporter.parse(file.getInputStream());
        employees.forEach(e -> e.setTenantId(tenantId));
        return repo.saveAll(employees);
    }
    
    /**
     * Get active employees for current tenant
     */
    public List<Employee> getActiveEmployees() {
        String tenantId = TenantContext.getTenantIdOrDefault("ORG001");
        return repo.findByTenantIdAndStatus(tenantId, EmployeeStatus.ACTIVE);
    }
    
    /**
     * Search employees by name/code for current tenant
     */
    public List<Employee> search(String query) {
        String tenantId = TenantContext.getTenantIdOrDefault("ORG001");
        return repo.searchByTenantId(tenantId, query);
    }
    
    /**
     * Count employees for current tenant
     */
    public long count() {
        String tenantId = TenantContext.getTenantIdOrDefault("ORG001");
        return repo.countByTenantId(tenantId);
    }
}
