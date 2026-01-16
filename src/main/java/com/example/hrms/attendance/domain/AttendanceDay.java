package com.example.hrms.attendance.domain;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name="attendance_day",
    indexes = {
        @Index(name="idx_day_emp_date", columnList="employeeId, workDate"),
        @Index(name="idx_day_tenant", columnList="tenantId"),
        @Index(name="idx_day_tenant_date", columnList="tenantId, workDate")
    })
public class AttendanceDay implements Serializable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Multi-tenancy support (replaces orgId)
    @Column(length = 50)
    private String tenantId;
    
    @Deprecated // Use tenantId instead
    private Long orgId;
    private Long employeeId;
    private LocalDate workDate;

    private Integer totalWorkMin;
    private Integer totalOTEligibleMin;
    private Integer totalOTApprovedMin;

    // First punch in and last punch out times
    private LocalDateTime firstIn;
    private LocalDateTime lastOut;

    // Late and early departure tracking
    private Integer lateByMins;
    private Integer earlyByMins;
    
    // Rounded times (based on shift rounding rules)
    private LocalDateTime roundedIn;   // Effective IN time after rounding (for late arrivals)
    private LocalDateTime roundedOut;  // Effective OUT time after rounding (for early departures)
    
    // Late/Early indicators
    private Boolean isLateIn;          // True if employee arrived after shift start + grace
    private Boolean isEarlyOut;        // True if employee left before shift end - grace
    
    // Punch count for the day
    private Integer punchCount;
    
    // Shift tracking - supports dual shifts
    @Column(length = 64)
    private String shiftCodes; // Comma-separated if multiple shifts (e.g., "MORNING,EVENING")
    
    // Flag to indicate if employee worked multiple shifts on this day
    private Boolean dualShift;
    
    // Cross-midnight indicator (if OUT time is on the next calendar day)
    private Boolean crossedMidnight;

    @Column(length=16)
    private String status; // PRESENT|ABSENT|LEAVE|HALF_DAY|PARTIAL|EXCEPTION
    
    // Missing punch indicator - when only IN or only OUT is present
    private Boolean missingPunch;
    
    // Type of missing punch: "IN" or "OUT"
    @Column(length=8)
    private String missingPunchType;
    
    // Manual override for missing punch - admin can set this
    private LocalDateTime manualIn;
    private LocalDateTime manualOut;
    
    // Who updated the manual entry and when
    @Column(length=64)
    private String manualUpdatedBy;
    private LocalDateTime manualUpdatedAt;
    
    // Remarks/notes for any exceptions or admin notes
    @Column(length=256)
    private String remarks;
    
    // Flag to indicate this day needs admin review
    private Boolean needsReview;
    
    // Weekly off indicator - if this day is configured as weekly off for the employee
    private Boolean isWeeklyOff;
    
    // Holiday indicator - if this day is a calendar holiday for the employee
    private Boolean isHoliday;
    
    // Holiday name if applicable
    @Column(length=128)
    private String holidayName;
    
    // Overtime on holiday/weekly off - worked on a day that was supposed to be off
    private Boolean isOvertimeDay;
    
    // Overtime minutes worked on holiday/weekly off
    private Integer overtimeOnHolidayMins;
}
