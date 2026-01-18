package com.example.hrms.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of an attendance import operation.
 * Contains success/failure counts and detailed error information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceImportResult {
    
    /**
     * Total number of rows processed (excluding header)
     */
    private int totalRows;
    
    /**
     * Number of rows successfully imported
     */
    private int successCount;
    
    /**
     * Number of rows that failed validation or import
     */
    private int failedCount;
    
    /**
     * Number of rows skipped (empty rows, already exists, etc.)
     */
    private int skippedCount;
    
    /**
     * Number of duplicate entries that were updated instead of inserted
     */
    private int updatedCount;
    
    /**
     * List of row-level errors
     */
    @Builder.Default
    private List<RowError> errors = new ArrayList<>();
    
    /**
     * List of warnings (non-critical issues)
     */
    @Builder.Default
    private List<RowWarning> warnings = new ArrayList<>();
    
    /**
     * Overall status message
     */
    private String message;
    
    /**
     * Whether the import was successful overall
     */
    private boolean success;
    
    /**
     * Represents an error for a specific row
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowError {
        private int rowNumber;
        private String empCode;
        private String date;
        private String field;
        private String error;
        private String value;
    }
    
    /**
     * Represents a warning for a specific row
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowWarning {
        private int rowNumber;
        private String empCode;
        private String warning;
    }
    
    /**
     * Add an error to the result
     */
    public void addError(int rowNumber, String empCode, String date, String field, String error, String value) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(RowError.builder()
                .rowNumber(rowNumber)
                .empCode(empCode)
                .date(date)
                .field(field)
                .error(error)
                .value(value)
                .build());
    }
    
    /**
     * Add a warning to the result
     */
    public void addWarning(int rowNumber, String empCode, String warning) {
        if (warnings == null) {
            warnings = new ArrayList<>();
        }
        warnings.add(RowWarning.builder()
                .rowNumber(rowNumber)
                .empCode(empCode)
                .warning(warning)
                .build());
    }
    
    /**
     * Create a successful result
     */
    public static AttendanceImportResult success(int totalRows, int successCount, int updatedCount, int skippedCount) {
        return AttendanceImportResult.builder()
                .totalRows(totalRows)
                .successCount(successCount)
                .updatedCount(updatedCount)
                .skippedCount(skippedCount)
                .failedCount(0)
                .success(true)
                .message(String.format("Import completed: %d new, %d updated, %d skipped out of %d rows", 
                        successCount, updatedCount, skippedCount, totalRows))
                .build();
    }
    
    /**
     * Create a partial success result (some errors)
     */
    public static AttendanceImportResult partial(int totalRows, int successCount, int failedCount, 
                                                  int updatedCount, int skippedCount, List<RowError> errors) {
        return AttendanceImportResult.builder()
                .totalRows(totalRows)
                .successCount(successCount)
                .failedCount(failedCount)
                .updatedCount(updatedCount)
                .skippedCount(skippedCount)
                .errors(errors)
                .success(successCount > 0)
                .message(String.format("Import completed with errors: %d new, %d updated, %d failed, %d skipped", 
                        successCount, updatedCount, failedCount, skippedCount))
                .build();
    }
    
    /**
     * Create a failed result
     */
    public static AttendanceImportResult failure(String message) {
        return AttendanceImportResult.builder()
                .success(false)
                .message(message)
                .build();
    }
}
