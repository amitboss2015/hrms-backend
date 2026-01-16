package com.example.hrms.attendance.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name="import_batch", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"orgId", "month", "year"})
})
public class ImportBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long orgId;
    private Integer month;
    private Integer year;
    private String templateVersion;
    private String uploadedBy;
    private Instant uploadedAt;
    private Integer totalRows;
    private Integer successRows;
    private Integer errorRows;
}
