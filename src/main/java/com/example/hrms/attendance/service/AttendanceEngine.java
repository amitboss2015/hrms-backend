package com.example.hrms.attendance.service;

import java.time.YearMonth;

public interface AttendanceEngine {
    void rebuildEmployeeMonth(Long orgId, Long employeeId, YearMonth ym);
    void rebuildOrgMonth(Long orgId, YearMonth ym);
}
