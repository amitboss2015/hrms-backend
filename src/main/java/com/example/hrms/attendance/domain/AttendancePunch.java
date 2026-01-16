package com.example.hrms.attendance.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name = "attendance_punch")
public class AttendancePunch {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)           // keep org multi-tenant ready
    private Long orgId;

    @Column(nullable = false)
    private Long employeeId;

    @Column(nullable = false)           // store as UTC; convert with org tz when needed
    private Instant punchTsUtc;

    @Column(length = 64)
    private String timezone;            // optional source tz (IANA), e.g. "Asia/Kolkata"

    @Column(length = 16)
    private String source;              // BIO|MANUAL|WEB|API|IMPORT

    @Column(length = 8)
    private String punchTypeHint;       // IN|OUT|null (auto if null)

    @Column(length = 32)
    private String shiftHint;           // optional

    private Boolean prevDayCheckout;    // if true, attribute to previous day

    @Column(length = 64)
    private String deviceId;

    @Column(length = 256)
    private String notes;

    private Long importBatchId;
}

