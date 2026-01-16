package com.example.hrms.attendance.repo;

import com.example.hrms.attendance.domain.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    
    /**
     * Find all import batches for a specific org, month, and year.
     * Used to check for duplicate uploads.
     */
    List<ImportBatch> findByOrgIdAndMonthAndYear(Long orgId, Integer month, Integer year);
    
    /**
     * Find all import batches for a specific org.
     */
    List<ImportBatch> findByOrgIdOrderByUploadedAtDesc(Long orgId);
}
