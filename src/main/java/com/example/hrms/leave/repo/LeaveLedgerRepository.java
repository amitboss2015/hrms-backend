package com.example.hrms.leave.repo;

import com.example.hrms.leave.domain.LeaveLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface LeaveLedgerRepository extends JpaRepository<LeaveLedger, Long> {
  
  // Tenant-aware methods
  Optional<LeaveLedger> findByTenantIdAndEmpIdAndLeaveTypeIdAndLeaveYearAndLeaveMonth(
    String tenantId, String empId, Long leaveTypeId, Integer year, Integer month);
  
  List<LeaveLedger> findByTenantIdAndEmpIdAndLeaveTypeIdAndLeaveYear(
    String tenantId, String empId, Long leaveTypeId, Integer year);
  
  List<LeaveLedger> findByTenantIdAndEmpIdAndLeaveYear(String tenantId, String empId, Integer year);
  
  List<LeaveLedger> findByTenantIdAndEmpIdAndLeaveYearOrderByLeaveMonthAsc(
    String tenantId, String empId, Integer year);
  
  @Query("SELECT l FROM LeaveLedger l WHERE l.tenantId = :tenantId AND l.empId = :empId " +
         "AND l.leaveYear = :year AND l.leaveMonth <= :month ORDER BY l.leaveMonth DESC")
  List<LeaveLedger> findLatestUpToMonth(@Param("tenantId") String tenantId, 
                                        @Param("empId") String empId,
                                        @Param("year") Integer year, 
                                        @Param("month") Integer month);
  
  Optional<LeaveLedger> findTopByTenantIdAndEmpIdAndLeaveTypeIdAndLeaveYearOrderByLeaveMonthDesc(
    String tenantId, String empId, Long leaveTypeId, Integer year);
  
  // ===== LEGACY METHODS (backward compatibility) =====
  
  @Deprecated
  default Optional<LeaveLedger> findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYearAndLeaveMonth(
      String orgId, String empId, Long leaveTypeId, Integer year, Integer month) {
    return findByTenantIdAndEmpIdAndLeaveTypeIdAndLeaveYearAndLeaveMonth(orgId, empId, leaveTypeId, year, month);
  }
  
  @Deprecated
  default List<LeaveLedger> findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYear(
      String orgId, String empId, Long leaveTypeId, Integer year) {
    return findByTenantIdAndEmpIdAndLeaveTypeIdAndLeaveYear(orgId, empId, leaveTypeId, year);
  }
  
  @Deprecated
  default List<LeaveLedger> findByOrgIdAndEmpIdAndLeaveYear(String orgId, String empId, Integer year) {
    return findByTenantIdAndEmpIdAndLeaveYear(orgId, empId, year);
  }
  
  @Deprecated
  default List<LeaveLedger> findByOrgIdAndEmpIdAndLeaveYearOrderByLeaveMonthAsc(
      String orgId, String empId, Integer year) {
    return findByTenantIdAndEmpIdAndLeaveYearOrderByLeaveMonthAsc(orgId, empId, year);
  }
  
  @Deprecated
  default Optional<LeaveLedger> findTopByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYearOrderByLeaveMonthDesc(
      String orgId, String empId, Long leaveTypeId, Integer year) {
    return findTopByTenantIdAndEmpIdAndLeaveTypeIdAndLeaveYearOrderByLeaveMonthDesc(orgId, empId, leaveTypeId, year);
  }
}
