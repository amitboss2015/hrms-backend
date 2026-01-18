package com.example.hrms.attendance.service;

import com.example.hrms.attendance.dto.DailyPunchLogDTO;
import com.example.hrms.attendance.dto.SummaryRowDTO;
import java.util.*;

public interface AttendanceQueryService {
    /**
     * Get employee logs (legacy non-tenant-aware version)
     */
    List<DailyPunchLogDTO> getEmployeeLogs(Long orgId, Long employeeId, String empCode, int month, int year);
    
    /**
     * Get employee logs (tenant-aware version)
     */
    List<DailyPunchLogDTO> getEmployeeLogs(Long orgId, String tenantId, Long employeeId, String empCode, int month, int year);
    
    List<SummaryRowDTO> getMonthlySummary(Long orgId, int month, int year);
}
