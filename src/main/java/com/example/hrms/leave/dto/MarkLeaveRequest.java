package com.example.hrms.leave.dto;

import com.example.hrms.leave.domain.enums.DurationKind;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MarkLeaveRequest(String orgId, String empId, Long leaveTypeId,
                               LocalDate startDate, LocalDate endDate,
                               DurationKind durationKind, BigDecimal totalDays,
                               String remarks, boolean previewOnly) {}
