
package com.example.hrms.repo;

import com.example.hrms.domain.EmployeeShiftAssignment;
import com.example.hrms.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface EmployeeShiftAssignmentRepository extends JpaRepository<EmployeeShiftAssignment, Long> {
  List<EmployeeShiftAssignment> findByEmployee(Employee employee);
  List<EmployeeShiftAssignment> findByEmployee_Id(Long employeeId);
  List<EmployeeShiftAssignment> findByEmployee_EmpCode(String empCode);
  List<EmployeeShiftAssignment> findByEmployee_EmpCodeAndStartDateLessThanEqualAndEndDateGreaterThanEqual(String empCode, LocalDate end, LocalDate start);
    List<EmployeeShiftAssignment> findByShift_Code(String shiftCode);
    // ➕ new — used by the engine when we only have employeeId
    List<EmployeeShiftAssignment> findByEmployee_IdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
        Long employeeId, LocalDate end, LocalDate start);
}
