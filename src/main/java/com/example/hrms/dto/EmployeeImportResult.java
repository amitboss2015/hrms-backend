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
    private String message;
    private List<RowError> errors = new ArrayList<>();
    private List<EmployeeDTO> importedEmployees = new ArrayList<>();

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

    public EmployeeImportResult() {}

    public void addError(int rowNumber, String empCode, String field, String errorMessage) {
        errors.add(new RowError(rowNumber, empCode, field, errorMessage));
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
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public List<RowError> getErrors() { return errors; }
    public void setErrors(List<RowError> errors) { this.errors = errors; }
    
    public List<EmployeeDTO> getImportedEmployees() { return importedEmployees; }
    public void setImportedEmployees(List<EmployeeDTO> importedEmployees) { this.importedEmployees = importedEmployees; }
}
