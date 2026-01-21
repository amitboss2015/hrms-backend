package com.example.hrms.admin.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity to store imported Excel files for audit and re-download.
 */
@Entity
@Table(name = "imported_files", indexes = {
    @Index(name = "idx_imported_file_tenant", columnList = "tenantId"),
    @Index(name = "idx_imported_file_type", columnList = "fileType"),
    @Index(name = "idx_imported_file_date", columnList = "uploadedAt")
})
public class ImportedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String tenantId;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false)
    private String storedFileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileType fileType;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    private String uploadedBy;

    // For attendance imports - link to month/year
    private Integer month;
    private Integer year;

    // File metadata
    private Long fileSizeBytes;
    private String contentType;

    // Processing result summary
    private Integer totalRows;
    private Integer successCount;
    private Integer errorCount;

    @Column(length = 1000)
    private String processingNotes;

    // Store file content as BLOB (for small files) or path (for large files)
    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] fileContent;

    // Alternative: store file path if using file system storage
    private String filePath;

    public ImportedFile() {
        this.uploadedAt = LocalDateTime.now();
    }

    public enum FileType {
        ATTENDANCE_LOG,
        EMPLOYEE_MASTER,
        SALARY_UPDATE,
        BIOMETRIC_ASSOCIATION,
        SHIFT_ASSIGNMENT,
        LOAN_DATA,
        OTHER
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public String getStoredFileName() { return storedFileName; }
    public void setStoredFileName(String storedFileName) { this.storedFileName = storedFileName; }

    public FileType getFileType() { return fileType; }
    public void setFileType(FileType fileType) { this.fileType = fileType; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }

    public Integer getMonth() { return month; }
    public void setMonth(Integer month) { this.month = month; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Integer getTotalRows() { return totalRows; }
    public void setTotalRows(Integer totalRows) { this.totalRows = totalRows; }

    public Integer getSuccessCount() { return successCount; }
    public void setSuccessCount(Integer successCount) { this.successCount = successCount; }

    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }

    public String getProcessingNotes() { return processingNotes; }
    public void setProcessingNotes(String processingNotes) { this.processingNotes = processingNotes; }

    public byte[] getFileContent() { return fileContent; }
    public void setFileContent(byte[] fileContent) { this.fileContent = fileContent; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
}
