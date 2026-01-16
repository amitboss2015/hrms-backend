package com.example.hrms.leave.repo;

import com.example.hrms.leave.domain.LeaveLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface LeaveLedgerRepository extends JpaRepository<LeaveLedger, Long> {
  Optional<LeaveLedger> findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYearAndLeaveMonth(
    String orgId, String empId, Long leaveTypeId, Integer year, Integer month);
  
  List<LeaveLedger> findByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYear(
    String orgId, String empId, Long leaveTypeId, Integer year);
  
  List<LeaveLedger> findByOrgIdAndEmpIdAndLeaveYear(String orgId, String empId, Integer year);
  
  List<LeaveLedger> findByOrgIdAndEmpIdAndLeaveYearOrderByLeaveMonthAsc(
    String orgId, String empId, Integer year);
  
  @Query("SELECT l FROM LeaveLedger l WHERE l.orgId = :orgId AND l.empId = :empId " +
         "AND l.leaveYear = :year AND l.leaveMonth <= :month ORDER BY l.leaveMonth DESC")
  List<LeaveLedger> findLatestUpToMonth(@Param("orgId") String orgId, 
                                        @Param("empId") String empId,
                                        @Param("year") Integer year, 
                                        @Param("month") Integer month);
  
  Optional<LeaveLedger> findTopByOrgIdAndEmpIdAndLeaveTypeIdAndLeaveYearOrderByLeaveMonthDesc(
    String orgId, String empId, Long leaveTypeId, Integer year);
}
