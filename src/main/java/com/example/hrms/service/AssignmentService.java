
package com.example.hrms.service;

import com.example.hrms.domain.*;
import com.example.hrms.domain.enums.PatternType;
import com.example.hrms.repo.EmployeeShiftAssignmentRepository;
import com.example.hrms.repo.EmployeeRepository;
import com.example.hrms.repo.ShiftRepository;
import com.example.hrms.tenant.TenantContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AssignmentService {
  private final EmployeeShiftAssignmentRepository repo;
  private final EmployeeRepository empRepo;
  private final ShiftRepository shiftRepo;

  public AssignmentService(EmployeeShiftAssignmentRepository repo, EmployeeRepository empRepo, ShiftRepository shiftRepo) {
    this.repo = repo; this.empRepo = empRepo; this.shiftRepo = shiftRepo;
  }

  public List<EmployeeShiftAssignment> listByEmpCode(String empCode) {
    return repo.findByEmployee_EmpCode(empCode);
  }

  @Transactional
  public List<EmployeeShiftAssignment> bulkAssign(List<Map<String,Object>> payload) {
    List<EmployeeShiftAssignment> result = new ArrayList<>();
    String tenantId = TenantContext.getTenantId();
    
    for (Map<String,Object> m : payload) {
      String empCode = (String) m.get("empCode");
      String shiftCode = (String) m.get("shiftCode");
      String patternType = (String) m.getOrDefault("patternType","NONE");
      String patternJson = (String) m.getOrDefault("patternJson", null);
      LocalDate start = LocalDate.parse((String) m.get("startDate"));
      LocalDate end = LocalDate.parse((String) m.get("endDate"));
      boolean primary = (Boolean) m.getOrDefault("primary", Boolean.TRUE);
      String remarks = (String) m.getOrDefault("remarks", null);

      // Use tenant-aware lookup for employee
      Employee emp = (tenantId != null 
          ? empRepo.findByTenantIdAndEmpCode(tenantId, empCode)
          : empRepo.findByEmpCode(empCode))
          .orElseThrow(() -> new RuntimeException("Emp not found " + empCode));
          
      // Use tenant-aware lookup for shift
      Shift shift = null;
      if (shiftCode != null) {
        shift = (tenantId != null 
            ? shiftRepo.findByTenantIdAndCode(tenantId, shiftCode)
            : shiftRepo.findByCode(shiftCode))
            .orElseThrow(() -> new RuntimeException("Shift not found " + shiftCode));
      }

      EmployeeShiftAssignment a = new EmployeeShiftAssignment();
      a.setEmployee(emp);
      a.setShift(shift);
      a.setPatternType(PatternType.valueOf(patternType));
      a.setPatternJson(patternJson);
      a.setStartDate(start);
      a.setEndDate(end);
      a.setPrimaryAssignment(primary);
      a.setRemarks(remarks);
      result.add(a);
    }
    return repo.saveAll(result);
  }

    /**
     * Get all assignments for current tenant
     */
    public List<EmployeeShiftAssignment> getAll() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            return repo.findAll(); // Fallback for backward compatibility
        }
        // Filter by tenant - assignments belong to employees which have tenantId
        return repo.findAll().stream()
            .filter(a -> a.getEmployee() != null && tenantId.equals(a.getEmployee().getTenantId()))
            .collect(Collectors.toList());
    }

    public List<EmployeeShiftAssignment> listByShiftCode(String shiftCode) {
        String tenantId = TenantContext.getTenantId();
        List<EmployeeShiftAssignment> result = repo.findByShift_Code(shiftCode);
        
        // Filter by tenant - only return assignments for current tenant's employees
        if (tenantId != null && !tenantId.isEmpty()) {
            return result.stream()
                .filter(a -> a.getEmployee() != null && tenantId.equals(a.getEmployee().getTenantId()))
                .collect(Collectors.toList());
        }
        return result;
    }
    
    /**
     * List assignments by employee code for current tenant
     */
    public List<EmployeeShiftAssignment> listByEmpCodeForTenant(String empCode) {
        String tenantId = TenantContext.getTenantId();
        List<EmployeeShiftAssignment> result = repo.findByEmployee_EmpCode(empCode);
        
        // Filter by tenant
        if (tenantId != null && !tenantId.isEmpty()) {
            return result.stream()
                .filter(a -> a.getEmployee() != null && tenantId.equals(a.getEmployee().getTenantId()))
                .collect(Collectors.toList());
        }
        return result;
    }
}
