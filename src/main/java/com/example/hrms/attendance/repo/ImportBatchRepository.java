package com.example.hrms.attendance.repo;

import com.example.hrms.attendance.domain.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    
    /**
     * Find all import batches for a specific org, month, and year.
     * Used to list all batches for a period (may have multiple devices).
     */
    List<ImportBatch> findByOrgIdAndMonthAndYear(Long orgId, Integer month, Integer year);
    
    /**
     * Find import batch for a specific org, month, year, and device.
     * Used to check for duplicate uploads for a specific device.
     */
    Optional<ImportBatch> findByOrgIdAndMonthAndYearAndDeviceId(Long orgId, Integer month, Integer year, Long deviceId);
    
    /**
     * Find import batch without device (legacy or when device is null).
     */
    List<ImportBatch> findByOrgIdAndMonthAndYearAndDeviceIdIsNull(Long orgId, Integer month, Integer year);
    
    /**
     * Find all import batches for a specific org.
     */
    List<ImportBatch> findByOrgIdOrderByUploadedAtDesc(Long orgId);
    
    /**
     * Delete import batches by org, year and month.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM ImportBatch b WHERE b.orgId = :orgId AND b.year = :year AND b.month = :month")
    int deleteByOrgIdAndYearAndMonth(
            @org.springframework.data.repository.query.Param("orgId") Long orgId,
            @org.springframework.data.repository.query.Param("year") Integer year,
            @org.springframework.data.repository.query.Param("month") Integer month);
}
