package com.example.hrms.attendance.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name="import_batch", uniqueConstraints = {
    // Changed: Now allows multiple batches per month if different devices
    @UniqueConstraint(columnNames = {"orgId", "month", "year", "deviceId"})
})
public class ImportBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long orgId;
    private Integer month;
    private Integer year;
    
    // New: Track which biometric device this batch is from
    @Column(name = "device_id")
    private Long deviceId;
    
    @Column(name = "device_code")
    private String deviceCode;
    
    private String templateVersion;
    private String uploadedBy;
    private Instant uploadedAt;
    private Integer totalRows;
    private Integer successRows;
    private Integer errorRows;
}
