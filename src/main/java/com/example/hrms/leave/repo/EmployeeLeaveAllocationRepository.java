package com.example.hrms.leave.repo;

import com.example.hrms.leave.domain.EmployeeLeaveAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmployeeLeaveAllocationRepository extends JpaRepository<EmployeeLeaveAllocation, Long> {
  
  // Tenant-aware methods
  Optional<EmployeeLeaveAllocation> findByTenantIdAndEmpIdAndLeaveTypeIdAndLeaveYear(
    String tenantId, String empId, Long leaveTypeId, Integer leaveYear);
  
  List<EmployeeLeaveAllocation> findByTenantIdAndEmpIdAndLeaveYear(
    String tenantId, String empId, Integer leaveYear);
  
  List<EmployeeLeaveAllocation> findByTenantIdAndLeaveYear(String tenantId, Integer leaveYear);
  
  // ===== LEGACY METHODS (backward compatibility) =====
  
  @Deprecated
  default Optional<EmployeeLeaveAllocation> findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYear(
      String orgId, String empId, Long leaveTypeId, Integer leaveYear) {
    return findByTenantIdAndEmpIdAndLeaveTypeIdAndLeaveYear(orgId, empId, leaveTypeId, leaveYear);
  }
  
  @Deprecated
  default List<EmployeeLeaveAllocation> findByOrgIdAndEmpIdAndLeaveYear(
      String orgId, String empId, Integer leaveYear) {
    return findByTenantIdAndEmpIdAndLeaveYear(orgId, empId, leaveYear);
  }
  
  @Deprecated
  default List<EmployeeLeaveAllocation> findByOrgIdAndLeaveYear(String orgId, Integer leaveYear) {
    return findByTenantIdAndLeaveYear(orgId, leaveYear);
  }
}
