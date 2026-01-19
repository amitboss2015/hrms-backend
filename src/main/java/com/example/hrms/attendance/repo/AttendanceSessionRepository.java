// AttendanceSessionRepository.java
package com.example.hrms.attendance.repo;

import com.example.hrms.attendance.domain.AttendanceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {
    
    List<AttendanceSession> findByEmployeeIdAndWorkDateBetween(Long empId, LocalDate from, LocalDate to);

    /**
     * Delete all sessions for a specific org and date range.
     */
    @Modifying
    @Query("DELETE FROM AttendanceSession s WHERE s.orgId = :orgId AND s.workDate >= :fromDate AND s.workDate <= :toDate")
    void deleteByOrgIdAndWorkDateBetween(@Param("orgId") Long orgId, 
                                          @Param("fromDate") LocalDate fromDate, 
                                          @Param("toDate") LocalDate toDate);

    /**
     * OPTIMIZED: Batch delete sessions for multiple employees.
     */
    @Modifying
    @Query("DELETE FROM AttendanceSession s WHERE s.employeeId IN :employeeIds AND s.workDate >= :fromDate AND s.workDate <= :toDate")
    void deleteByEmployeeIdInAndWorkDateBetween(@Param("employeeIds") List<Long> employeeIds,
                                                 @Param("fromDate") LocalDate fromDate,
                                                 @Param("toDate") LocalDate toDate);
}
