
package com.example.hrms.service;

import com.example.hrms.domain.*;
import com.example.hrms.domain.enums.EmployeeStatus;
import com.example.hrms.domain.enums.PatternType;
import com.example.hrms.repo.EmployeeShiftAssignmentRepository;
import com.example.hrms.repo.EmployeeRepository;
import com.example.hrms.repo.ShiftRepository;
import com.example.hrms.tenant.TenantContext;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AssignmentService {
  private final EmployeeShiftAssignmentRepository repo;
  private final EmployeeRepository empRepo;
  private final ShiftRepository shiftRepo;

  public AssignmentService(EmployeeShiftAssignmentRepository repo, EmployeeRepository empRepo, ShiftRepository shiftRepo) {
    this.repo = repo; this.empRepo = empRepo; this.shiftRepo = shiftRepo;
  }
  
  /**
   * Get shift assignment status for all employees of current tenant.
   * Returns count of employees with/without shift assignments.
   */
  public Map<String, Object> getShiftAssignmentStatus() {
      String tenantId = TenantContext.getTenantId();
      if (tenantId == null || tenantId.isEmpty()) {
          throw new RuntimeException("Tenant ID required");
      }
      
      // Get all active employees
      List<Employee> employees = empRepo.findByTenantIdAndStatus(tenantId, EmployeeStatus.ACTIVE);
      long totalEmployees = employees.size();
      
      // Get employees with shift assignments
      Set<Long> employeesWithShift = new HashSet<>(repo.findEmployeeIdsWithShift(tenantId));
      
      // Calculate unassigned
      List<Map<String, Object>> unassignedEmployees = new ArrayList<>();
      for (Employee emp : employees) {
          if (!employeesWithShift.contains(emp.getId())) {
              Map<String, Object> empInfo = new HashMap<>();
              empInfo.put("id", emp.getId());
              empInfo.put("empCode", emp.getEmpCode());
              empInfo.put("name", emp.getFirstName() + (emp.getLastName() != null ? " " + emp.getLastName() : ""));
              empInfo.put("department", emp.getDepartment());
              unassignedEmployees.add(empInfo);
          }
      }
      
      Map<String, Object> result = new HashMap<>();
      result.put("totalEmployees", totalEmployees);
      result.put("withShift", employeesWithShift.size());
      result.put("withoutShift", unassignedEmployees.size());
      result.put("unassignedEmployees", unassignedEmployees);
      result.put("allAssigned", unassignedEmployees.isEmpty());
      
      return result;
  }
  
  /**
   * Bulk assign default shift to all employees without shift assignments.
   * Uses the first available shift or creates a default if none exists.
   */
  @Transactional
  public Map<String, Object> assignDefaultShiftToAll(String shiftCode) {
      String tenantId = TenantContext.getTenantId();
      if (tenantId == null || tenantId.isEmpty()) {
          throw new RuntimeException("Tenant ID required");
      }
      
      // Find the shift
      Shift shift = shiftRepo.findByTenantIdAndCode(tenantId, shiftCode)
          .orElseThrow(() -> new RuntimeException("Shift not found: " + shiftCode));
      
      // Get all active employees
      List<Employee> employees = empRepo.findByTenantIdAndStatus(tenantId, EmployeeStatus.ACTIVE);
      
      // Get employees with shift assignments
      Set<Long> employeesWithShift = new HashSet<>(repo.findEmployeeIdsWithShift(tenantId));
      
      // Create assignments for unassigned employees
      List<EmployeeShiftAssignment> newAssignments = new ArrayList<>();
      LocalDate startDate = LocalDate.of(2020, 1, 1); // Historical start date
      LocalDate endDate = LocalDate.of(2099, 12, 31); // Far future end date
      
      int assignedCount = 0;
      for (Employee emp : employees) {
          if (!employeesWithShift.contains(emp.getId())) {
              EmployeeShiftAssignment assignment = new EmployeeShiftAssignment();
              assignment.setEmployee(emp);
              assignment.setShift(shift);
              assignment.setPatternType(PatternType.NONE);
              assignment.setStartDate(startDate);
              assignment.setEndDate(endDate);
              assignment.setPrimaryAssignment(true);
              assignment.setRemarks("Auto-assigned default shift");
              newAssignments.add(assignment);
              assignedCount++;
          }
      }
      
      if (!newAssignments.isEmpty()) {
          repo.saveAll(newAssignments);
          log.info("Assigned shift {} to {} employees for tenant {}", shiftCode, assignedCount, tenantId);
      }
      
      Map<String, Object> result = new HashMap<>();
      result.put("success", true);
      result.put("assigned", assignedCount);
      result.put("shiftCode", shiftCode);
      result.put("shiftName", shift.getName());
      result.put("message", "Assigned " + shiftCode + " to " + assignedCount + " employees");
      
      return result;
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
