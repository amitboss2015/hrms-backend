// AttendancePunchRepository.java
package com.example.hrms.attendance.repo;

import com.example.hrms.attendance.domain.AttendancePunch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AttendancePunchRepository extends JpaRepository<AttendancePunch, Long> {
    
    List<AttendancePunch> findByEmployeeIdAndPunchTsUtcBetweenOrderByPunchTsUtcAsc(
            Long employeeId, Instant start, Instant end);

    @Query("""
            select distinct p.employeeId
            from AttendancePunch p
            where (:orgId is null or p.orgId = :orgId)
              and p.punchTsUtc >= :fromTs and p.punchTsUtc < :toTs
            """)
    List<Long> findDistinctEmployeeIdsForRange(@Param("orgId") Long orgId,
                                                @Param("fromTs") Instant fromTs,
                                                @Param("toTs") Instant toTs);

    /**
     * Delete all punches for a specific import batch.
     */
    @Modifying
    @Query("DELETE FROM AttendancePunch p WHERE p.importBatchId = :batchId")
    void deleteByImportBatchId(@Param("batchId") Long batchId);

    /**
     * Find all punches for a specific import batch.
     */
    List<AttendancePunch> findByImportBatchId(Long batchId);

    /**
     * OPTIMIZED: Batch fetch punches for multiple employees in one query.
     * Critical for performance - reduces N queries to 1.
     */
    @Query("SELECT p FROM AttendancePunch p WHERE p.employeeId IN :employeeIds " +
           "AND p.punchTsUtc BETWEEN :start AND :end ORDER BY p.employeeId, p.punchTsUtc ASC")
    List<AttendancePunch> findByEmployeeIdInAndPunchTsUtcBetweenOrderByEmployeeIdAscPunchTsUtcAsc(
            @Param("employeeIds") List<Long> employeeIds,
            @Param("start") Instant start,
            @Param("end") Instant end);
}
