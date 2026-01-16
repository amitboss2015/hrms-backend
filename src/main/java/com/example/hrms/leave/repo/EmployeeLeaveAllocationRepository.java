package com.example.hrms.leave.repo;

import com.example.hrms.leave.domain.EmployeeLeaveAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmployeeLeaveAllocationRepository extends JpaRepository<EmployeeLeaveAllocation, Long> {
  Optional<EmployeeLeaveAllocation> findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYear(
    String orgId, String empId, Long leaveTypeId, Integer leaveYear);
  
  List<EmployeeLeaveAllocation> findByOrgIdAndEmpIdAndLeaveYear(
    String orgId, String empId, Integer leaveYear);
  
  List<EmployeeLeaveAllocation> findByOrgIdAndLeaveYear(String orgId, Integer leaveYear);
}
