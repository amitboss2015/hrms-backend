package com.example.hrms.attendance.service;

import com.example.hrms.attendance.dto.MonthlySummaryDTO;

import java.util.List;

public interface AttendanceSummaryService {
    List<MonthlySummaryDTO> getSummary(int year, int month, Long orgId);
}

