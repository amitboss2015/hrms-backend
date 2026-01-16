package com.example.hrms.attendance.service;

import com.example.hrms.attendance.dto.ImportResultDTO;
import org.springframework.web.multipart.MultipartFile;

public interface AttendanceImportService {
    ImportResultDTO importLogsExcel(Long orgId, MultipartFile file, int month, int year, String uploadedBy);
}
