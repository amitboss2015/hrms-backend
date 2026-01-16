package com.example.hrms.attendance.domain;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name="import_error", indexes = @Index(name="idx_imp_err_batch", columnList="batchId"))
public class ImportError {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long batchId;
    private Integer rowNum;
    private String columnName;
    private String errorCode;
    @Column(length=512)
    private String errorMessage;
    @Lob
    private String rawPayloadJson;
}

