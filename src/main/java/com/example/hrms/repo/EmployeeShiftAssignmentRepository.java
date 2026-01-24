
package com.example.hrms.repo;

import com.example.hrms.domain.EmployeeShiftAssignment;
import com.example.hrms.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
        
    /**
     * Count employees with shift assignments for a tenant
     */
    @Query("SELECT COUNT(DISTINCT esa.employee.id) FROM EmployeeShiftAssignment esa WHERE esa.employee.tenantId = :tenantId")
    long countEmployeesWithShift(@Param("tenantId") String tenantId);
    
    /**
     * Find employees IDs that have shift assignments for a tenant
     */
    @Query("SELECT DISTINCT esa.employee.id FROM EmployeeShiftAssignment esa WHERE esa.employee.tenantId = :tenantId")
    List<Long> findEmployeeIdsWithShift(@Param("tenantId") String tenantId);
    
    /**
     * Check if an employee has any shift assignment
     */
    boolean existsByEmployee_Id(Long employeeId);
    
    /**
     * Delete all assignments for an employee
     */
    void deleteByEmployee_Id(Long employeeId);
}
