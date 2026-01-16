package com.example.hrms.leave.dto;

public record EmployeeLeaveRow(
    Long id, String durationKind, String startDate, String endDate,
    String status, String remarks, String totalDays, LeaveTypeSummary leaveType
) {}
