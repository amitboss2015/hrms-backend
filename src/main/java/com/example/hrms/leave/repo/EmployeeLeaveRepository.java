package com.example.hrms.leave.repo;

import com.example.hrms.leave.domain.EmployeeLeave;
import com.example.hrms.leave.domain.enums.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface EmployeeLeaveRepository extends JpaRepository<EmployeeLeave, Long> {
  
  // Tenant-aware methods (entity field is now tenantId)
  List<EmployeeLeave> findByTenantIdAndEmpIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
    String tenantId, String empId, LocalDate end, LocalDate start);
  
  List<EmployeeLeave> findByTenantIdAndEmpIdOrderByStartDateDesc(String tenantId, String empId);
  
  List<EmployeeLeave> findByTenantIdAndEmpIdAndLeaveTypeIdAndStatusIn(
    String tenantId, String empId, Long leaveTypeId, List<LeaveStatus> statuses);
  
  @Query("SELECT COALESCE(SUM(e.totalDays), 0) FROM EmployeeLeave e " +
         "WHERE e.tenantId = :tenantId AND e.empId = :empId AND e.leaveType.id = :leaveTypeId " +
         "AND e.status IN :statuses " +
         "AND YEAR(e.startDate) = :year AND MONTH(e.startDate) = :month")
  BigDecimal sumTotalDaysByMonth(@Param("tenantId") String tenantId, 
                                  @Param("empId") String empId,
                                  @Param("leaveTypeId") Long leaveTypeId,
                                  @Param("statuses") List<LeaveStatus> statuses,
                                  @Param("year") Integer year, 
                                  @Param("month") Integer month);
  
  @Query("SELECT COALESCE(SUM(e.totalDays), 0) FROM EmployeeLeave e " +
         "WHERE e.tenantId = :tenantId AND e.empId = :empId AND e.leaveType.id = :leaveTypeId " +
         "AND e.status IN :statuses AND YEAR(e.startDate) = :year")
  BigDecimal sumTotalDaysByYear(@Param("tenantId") String tenantId, 
                                 @Param("empId") String empId,
                                 @Param("leaveTypeId") Long leaveTypeId,
                                 @Param("statuses") List<LeaveStatus> statuses,
                                 @Param("year") Integer year);

  // For leave reports - find leaves overlapping with a date range
  List<EmployeeLeave> findByTenantIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
    String tenantId, LocalDate endDate, LocalDate startDate);
  
  // For employee leave report - find leaves starting within a date range
  List<EmployeeLeave> findByTenantIdAndEmpIdAndStartDateBetween(
    String tenantId, String empId, LocalDate from, LocalDate to);
  
  // ===== LEGACY METHODS (backward compatibility) =====
  
  @Deprecated
  default List<EmployeeLeave> findByOrgIdAndEmpIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
      String orgId, String empId, LocalDate end, LocalDate start) {
    return findByTenantIdAndEmpIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(orgId, empId, end, start);
  }
  
  @Deprecated
  default List<EmployeeLeave> findByOrgIdAndEmpIdOrderByStartDateDesc(String orgId, String empId) {
    return findByTenantIdAndEmpIdOrderByStartDateDesc(orgId, empId);
  }
  
  @Deprecated
  default List<EmployeeLeave> findByOrgIdAndEmpIdAndLeaveTypeIdAndStatusIn(
      String orgId, String empId, Long leaveTypeId, List<LeaveStatus> statuses) {
    return findByTenantIdAndEmpIdAndLeaveTypeIdAndStatusIn(orgId, empId, leaveTypeId, statuses);
  }
  
  @Deprecated
  default List<EmployeeLeave> findByOrgIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
      String orgId, LocalDate endDate, LocalDate startDate) {
    return findByTenantIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(orgId, endDate, startDate);
  }
  
  @Deprecated
  default List<EmployeeLeave> findByOrgIdAndEmpIdAndStartDateBetween(
      String orgId, String empId, LocalDate from, LocalDate to) {
    return findByTenantIdAndEmpIdAndStartDateBetween(orgId, empId, from, to);
  }
}
