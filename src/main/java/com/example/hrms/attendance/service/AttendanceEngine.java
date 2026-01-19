package com.example.hrms.attendance.service;

import java.time.YearMonth;
import java.util.List;

public interface AttendanceEngine {
    void rebuildEmployeeMonth(Long orgId, Long employeeId, YearMonth ym);
    void rebuildOrgMonth(Long orgId, YearMonth ym);
    
    /**
     * Optimized batch rebuild for multiple employees at once.
     * Pre-fetches all data to minimize database calls.
     */
    void rebuildEmployeesMonthBatch(Long orgId, String tenantId, List<Long> employeeIds, YearMonth ym);
}
