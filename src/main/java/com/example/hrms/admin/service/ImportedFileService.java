package com.example.hrms.admin.service;

import com.example.hrms.admin.domain.ImportedFile;
import com.example.hrms.admin.domain.ImportedFile.FileType;
import com.example.hrms.admin.repo.ImportedFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service to store and retrieve imported Excel files for audit purposes.
 */
@Service
@Slf4j
public class ImportedFileService {

    private final ImportedFileRepository fileRepo;

    public ImportedFileService(ImportedFileRepository fileRepo) {
        this.fileRepo = fileRepo;
    }

    /**
     * Store an imported file for audit.
     */
    @Transactional
    public ImportedFile storeFile(String tenantId, MultipartFile file, FileType fileType,
                                   Integer year, Integer month, String uploadedBy,
                                   Integer totalRows, Integer successCount, Integer errorCount,
                                   String processingNotes) throws IOException {
        
        ImportedFile importedFile = new ImportedFile();
        importedFile.setTenantId(tenantId);
        importedFile.setOriginalFileName(file.getOriginalFilename());
        importedFile.setStoredFileName(generateStoredFileName(file.getOriginalFilename(), fileType));
        importedFile.setFileType(fileType);
        importedFile.setUploadedAt(LocalDateTime.now());
        importedFile.setUploadedBy(uploadedBy);
        importedFile.setMonth(month);
        importedFile.setYear(year);
        importedFile.setFileSizeBytes(file.getSize());
        importedFile.setContentType(file.getContentType());
        importedFile.setTotalRows(totalRows);
        importedFile.setSuccessCount(successCount);
        importedFile.setErrorCount(errorCount);
        importedFile.setProcessingNotes(processingNotes);
        
        // Store file content (for files < 10MB, store in DB; for larger files, use file system)
        if (file.getSize() < 10 * 1024 * 1024) { // 10MB
            importedFile.setFileContent(file.getBytes());
        } else {
            // For large files, you would store to file system and save path
            // For now, we'll store in DB anyway (can be optimized later)
            importedFile.setFileContent(file.getBytes());
            log.warn("Large file stored in DB: {} bytes", file.getSize());
        }
        
        log.info("Storing imported file: {} ({}) for tenant={}", 
                file.getOriginalFilename(), fileType, tenantId);
        
        return fileRepo.save(importedFile);
    }

    /**
     * Get list of imported files for a tenant.
     */
    public List<Map<String, Object>> getImportedFiles(String tenantId, FileType fileType, 
                                                       Integer year, Integer month, int limit) {
        List<ImportedFile> files;
        
        if (fileType != null && year != null && month != null) {
            files = fileRepo.findByTenantIdAndFileTypeAndYearAndMonthOrderByUploadedAtDesc(
                    tenantId, fileType, year, month);
        } else if (fileType != null) {
            files = fileRepo.findByTenantIdAndFileTypeOrderByUploadedAtDesc(tenantId, fileType);
        } else if (year != null && month != null) {
            files = fileRepo.findByTenantIdAndYearAndMonthOrderByUploadedAtDesc(tenantId, year, month);
        } else {
            files = fileRepo.findByTenantIdOrderByUploadedAtDesc(tenantId);
        }
        
        // Limit results
        if (limit > 0 && files.size() > limit) {
            files = files.subList(0, limit);
        }
        
        // Convert to DTOs (without file content for list view)
        List<Map<String, Object>> result = new ArrayList<>();
        for (ImportedFile f : files) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", f.getId());
            dto.put("originalFileName", f.getOriginalFileName());
            dto.put("fileType", f.getFileType().name());
            dto.put("uploadedAt", f.getUploadedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            dto.put("uploadedBy", f.getUploadedBy());
            dto.put("month", f.getMonth());
            dto.put("year", f.getYear());
            dto.put("fileSizeBytes", f.getFileSizeBytes());
            dto.put("fileSizeFormatted", formatFileSize(f.getFileSizeBytes()));
            dto.put("totalRows", f.getTotalRows());
            dto.put("successCount", f.getSuccessCount());
            dto.put("errorCount", f.getErrorCount());
            dto.put("processingNotes", f.getProcessingNotes());
            result.add(dto);
        }
        
        return result;
    }

    /**
     * Get file content for download.
     */
    public Optional<ImportedFile> getFileForDownload(Long fileId, String tenantId) {
        Optional<ImportedFile> file = fileRepo.findById(fileId);
        
        // Security check - ensure file belongs to tenant
        if (file.isPresent() && !file.get().getTenantId().equals(tenantId)) {
            log.warn("Attempted to access file {} from different tenant", fileId);
            return Optional.empty();
        }
        
        return file;
    }

    /**
     * Delete old files (for cleanup).
     */
    @Transactional
    public int deleteOldFiles(String tenantId, int daysOld) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysOld);
        List<ImportedFile> oldFiles = fileRepo.findByTenantIdOrderByUploadedAtDesc(tenantId)
                .stream()
                .filter(f -> f.getUploadedAt().isBefore(cutoff))
                .toList();
        
        fileRepo.deleteAll(oldFiles);
        log.info("Deleted {} old files for tenant={}", oldFiles.size(), tenantId);
        return oldFiles.size();
    }
    
    /**
     * Delete a specific file by ID (with tenant security check).
     */
    @Transactional
    public boolean deleteFile(String tenantId, Long fileId) {
        Optional<ImportedFile> fileOpt = fileRepo.findById(fileId);
        
        if (fileOpt.isEmpty()) {
            log.warn("File not found for deletion: {}", fileId);
            return false;
        }
        
        ImportedFile file = fileOpt.get();
        
        // Security check - ensure file belongs to tenant
        if (!file.getTenantId().equals(tenantId)) {
            log.warn("Attempted to delete file {} from different tenant", fileId);
            return false;
        }
        
        fileRepo.delete(file);
        log.info("Deleted file: {} ({})", file.getOriginalFileName(), fileId);
        return true;
    }

    /**
     * Get import statistics for dashboard.
     */
    public Map<String, Object> getImportStatistics(String tenantId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        
        for (FileType type : FileType.values()) {
            long count = fileRepo.countByTenantIdAndFileType(tenantId, type);
            stats.put(type.name().toLowerCase() + "Count", count);
        }
        
        // Recent imports (last 7 days)
        List<ImportedFile> recentFiles = fileRepo.findByTenantIdAndUploadedAtAfterOrderByUploadedAtDesc(
                tenantId, LocalDateTime.now().minusDays(7));
        stats.put("recentImportsCount", recentFiles.size());
        
        return stats;
    }

    private String generateStoredFileName(String originalFileName, FileType fileType) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String extension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        return fileType.name().toLowerCase() + "_" + timestamp + extension;
    }

    private String formatFileSize(Long bytes) {
        if (bytes == null) return "Unknown";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
