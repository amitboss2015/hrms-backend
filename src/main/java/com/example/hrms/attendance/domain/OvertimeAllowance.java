package com.example.hrms.attendance.domain;


import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name="overtime_allowance",
    indexes = @Index(name="idx_ot_allow_emp", columnList="employeeId"))
public class OvertimeAllowance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long orgId;
    private Long employeeId;
    private Boolean allow;       // if false → OT not payable
    private Integer dailyCapMin; // nullable
    private Integer monthlyCapMin;
    private LocalDate validFrom;
    private LocalDate validTo;
}

