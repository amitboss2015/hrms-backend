package com.example.hrms.leave.dto;


import com.example.hrms.leave.domain.LeaveCalendar.Category;
import java.time.LocalDate;

public record LeaveCalendarDTO(
    Long id,
    String orgId,
    LocalDate date,
    String name,
    Category category,
    Long leaveTypeId,   // nullable
    Boolean paid,       // nullable -> default true on create
    String notes
) {}

