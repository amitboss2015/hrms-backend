-- V4: Add imported_files table for storing Excel files for audit
-- Run this script to create the table for file storage

CREATE TABLE IF NOT EXISTS imported_files (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL,
    file_type ENUM('ATTENDANCE_LOG', 'EMPLOYEE_MASTER', 'SALARY_UPDATE', 'BIOMETRIC_ASSOCIATION', 'SHIFT_ASSIGNMENT', 'LOAN_DATA', 'OTHER') NOT NULL,
    uploaded_at DATETIME NOT NULL,
    uploaded_by VARCHAR(100),
    month INT,
    year INT,
    file_size_bytes BIGINT,
    content_type VARCHAR(100),
    total_rows INT,
    success_count INT,
    error_count INT,
    processing_notes VARCHAR(1000),
    file_content LONGBLOB,
    file_path VARCHAR(500),
    
    INDEX idx_imported_file_tenant (tenant_id),
    INDEX idx_imported_file_type (file_type),
    INDEX idx_imported_file_date (uploaded_at),
    INDEX idx_imported_file_month_year (tenant_id, year, month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add comment
ALTER TABLE imported_files COMMENT = 'Stores imported Excel files for audit and re-download';
