package com.example.hrms.attendance.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name = "attendance_session",
    indexes = {
        @Index(name="idx_sess_emp_date", columnList="employeeId, workDate"),
        @Index(name="idx_sess_emp_date_shift", columnList="employeeId, workDate, shiftCode")
    })
public class AttendanceSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long orgId;
    private Long employeeId;

    private LocalDate workDate;     // after attribution
    @Column(length=32)
    private String shiftCode;

    private Instant inTsUtc;
    private Instant outTsUtc;

    private Integer workMin;        // (out-in) – breaks – rounding
    private Integer lateMin;
    private Integer earlyMin;

    private Integer otEligibleMin;  // computed by engine
    private Integer otApprovedMin;  // manager approves; payroll uses this

    @Column(length=16)
    private String status;          // OK|MISSING_IN|MISSING_OUT|OVERLAP|MANUAL
}

