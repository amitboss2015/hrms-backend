// AttendanceDayRepository.java
package com.example.hrms.attendance.repo;

import com.example.hrms.attendance.domain.AttendanceDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AttendanceDayRepository extends JpaRepository<AttendanceDay, Long> {
    
    List<AttendanceDay> findByWorkDateBetween(LocalDate from, LocalDate to);
    
    List<AttendanceDay> findByEmployeeIdAndWorkDateBetween(Long empId, LocalDate from, LocalDate to);
    
    List<AttendanceDay> findByOrgIdAndWorkDateBetween(Long orgId, LocalDate from, LocalDate to);
    
    void deleteByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    /**
     * Delete all days for a specific org and date range.
     */
    @Modifying
    @Query("DELETE FROM AttendanceDay d WHERE d.orgId = :orgId AND d.workDate >= :fromDate AND d.workDate <= :toDate")
    void deleteByOrgIdAndWorkDateBetween(@Param("orgId") Long orgId, 
                                          @Param("fromDate") LocalDate fromDate, 
                                          @Param("toDate") LocalDate toDate);
}
