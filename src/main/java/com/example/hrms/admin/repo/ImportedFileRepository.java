package com.example.hrms.admin.repo;

import com.example.hrms.admin.domain.ImportedFile;
import com.example.hrms.admin.domain.ImportedFile.FileType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ImportedFileRepository extends JpaRepository<ImportedFile, Long> {
    
    /**
     * Find all imported files for a tenant, ordered by upload date
     */
    List<ImportedFile> findByTenantIdOrderByUploadedAtDesc(String tenantId);
    
    /**
     * Find imported files by type for a tenant
     */
    List<ImportedFile> findByTenantIdAndFileTypeOrderByUploadedAtDesc(String tenantId, FileType fileType);
    
    /**
     * Find imported files by month/year for a tenant (e.g., attendance logs)
     */
    List<ImportedFile> findByTenantIdAndYearAndMonthOrderByUploadedAtDesc(
            String tenantId, Integer year, Integer month);
    
    /**
     * Find imported files by type and month/year
     */
    List<ImportedFile> findByTenantIdAndFileTypeAndYearAndMonthOrderByUploadedAtDesc(
            String tenantId, FileType fileType, Integer year, Integer month);
    
    /**
     * Find recent imports (last N days)
     */
    List<ImportedFile> findByTenantIdAndUploadedAtAfterOrderByUploadedAtDesc(
            String tenantId, LocalDateTime after);
    
    /**
     * Count files by type for a tenant
     */
    long countByTenantIdAndFileType(String tenantId, FileType fileType);
    
    /**
     * Delete old files (for cleanup)
     */
    void deleteByTenantIdAndUploadedAtBefore(String tenantId, LocalDateTime before);
}
