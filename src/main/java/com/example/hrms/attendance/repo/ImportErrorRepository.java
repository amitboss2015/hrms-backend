package com.example.hrms.attendance.repo;

import com.example.hrms.attendance.domain.ImportError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportErrorRepository extends JpaRepository<ImportError, Long> {
    
    List<ImportError> findByBatchId(Long batchId);

    /**
     * Delete all errors for a specific import batch.
     */
    @Modifying
    @Query("DELETE FROM ImportError e WHERE e.batchId = :batchId")
    void deleteByBatchId(@Param("batchId") Long batchId);
}
