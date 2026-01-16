package com.example.hrms.attendance.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ImportResultDTO {
    private String batchId;
    private int total;
    private int success;
    private int failed;
    private String errorsCsvUrl;
    private String message;
    private boolean duplicate;
    private Long existingBatchId; // Used when duplicate=true to allow deletion
}
