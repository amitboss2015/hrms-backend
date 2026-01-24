package com.example.hrms.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Result object for employee import operation
 */
public class EmployeeImportResult {
    private boolean success;
    private int totalRows;
    private int successCount;
    private int errorCount;
    private int skippedCount;
    private int duplicateCount; // Employees with same name in different device
    private String message;
    private List<RowError> errors = new ArrayList<>();
    private List<SkippedRow> skipped = new ArrayList<>(); // Separate list for skipped entries
    private List<DuplicateWarning> duplicateWarnings = new ArrayList<>(); // Same name in different device
    private List<EmployeeDTO> importedEmployees = new ArrayList<>();
    private boolean requiresConfirmation; // If duplicates found, ask user to confirm

    public static class RowError {
        private int rowNumber;
        private String empCode;
        private String field;
        private String errorMessage;

        public RowError() {}
        
        public RowError(int rowNumber, String empCode, String field, String errorMessage) {
            this.rowNumber = rowNumber;
            this.empCode = empCode;
            this.field = field;
            this.errorMessage = errorMessage;
        }

        public int getRowNumber() { return rowNumber; }
        public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }
        public String getEmpCode() { return empCode; }
        public void setEmpCode(String empCode) { this.empCode = empCode; }
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
    
    public static class SkippedRow {
        private int rowNumber;
        private String empCode;
        private String reason;

        public SkippedRow() {}
        
        public SkippedRow(int rowNumber, String empCode, String reason) {
            this.rowNumber = rowNumber;
            this.empCode = empCode;
            this.reason = reason;
        }

        public int getRowNumber() { return rowNumber; }
        public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }
        public String getEmpCode() { return empCode; }
        public void setEmpCode(String empCode) { this.empCode = empCode; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    
    public static class DuplicateWarning {
        private int rowNumber;
        private String empCode;
        private String firstName;
        private String lastName;
        private String existingDevice;
        private String targetDevice;

        public DuplicateWarning() {}
        
        public DuplicateWarning(int rowNumber, String empCode, String firstName, String lastName, 
                               String existingDevice, String targetDevice) {
            this.rowNumber = rowNumber;
            this.empCode = empCode;
            this.firstName = firstName;
            this.lastName = lastName;
            this.existingDevice = existingDevice;
            this.targetDevice = targetDevice;
        }

        public int getRowNumber() { return rowNumber; }
        public String getEmpCode() { return empCode; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getExistingDevice() { return existingDevice; }
        public String getTargetDevice() { return targetDevice; }
    }

    public EmployeeImportResult() {}

    public void addError(int rowNumber, String empCode, String field, String errorMessage) {
        errors.add(new RowError(rowNumber, empCode, field, errorMessage));
        errorCount++;
    }
    
    public void addSkipped(int rowNumber, String empCode, String reason) {
        skipped.add(new SkippedRow(rowNumber, empCode, reason));
        skippedCount++;
    }
    
    public void addDuplicateWarning(int rowNumber, String empCode, String firstName, String lastName,
                                    String existingDevice, String targetDevice) {
        duplicateWarnings.add(new DuplicateWarning(rowNumber, empCode, firstName, lastName, existingDevice, targetDevice));
        duplicateCount++;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    
    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }
    
    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int errorCount) { this.errorCount = errorCount; }
    
    public int getSkippedCount() { return skippedCount; }
    public void setSkippedCount(int skippedCount) { this.skippedCount = skippedCount; }
    
    public int getDuplicateCount() { return duplicateCount; }
    public void setDuplicateCount(int duplicateCount) { this.duplicateCount = duplicateCount; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public List<RowError> getErrors() { return errors; }
    public void setErrors(List<RowError> errors) { this.errors = errors; }
    
    public List<SkippedRow> getSkipped() { return skipped; }
    public void setSkipped(List<SkippedRow> skipped) { this.skipped = skipped; }
    
    public List<DuplicateWarning> getDuplicateWarnings() { return duplicateWarnings; }
    public void setDuplicateWarnings(List<DuplicateWarning> duplicateWarnings) { this.duplicateWarnings = duplicateWarnings; }
    
    public List<EmployeeDTO> getImportedEmployees() { return importedEmployees; }
    public void setImportedEmployees(List<EmployeeDTO> importedEmployees) { this.importedEmployees = importedEmployees; }
    
    public boolean isRequiresConfirmation() { return requiresConfirmation; }
    public void setRequiresConfirmation(boolean requiresConfirmation) { this.requiresConfirmation = requiresConfirmation; }
}
