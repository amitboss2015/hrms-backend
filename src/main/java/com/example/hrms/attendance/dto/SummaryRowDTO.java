package com.example.hrms.attendance.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SummaryRowDTO {
    private String empCode;
    private String empName;
    private int present;
    private int absent;
    private int leaveDays;
}
