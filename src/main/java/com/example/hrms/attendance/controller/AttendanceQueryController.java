package com.example.hrms.attendance.controller;

import com.example.hrms.attendance.domain.AttendanceDay;
import com.example.hrms.attendance.dto.DailyPunchLogDTO;
import com.example.hrms.attendance.dto.MonthlySummaryDTO;
import com.example.hrms.attendance.dto.SummaryRowDTO;
import com.example.hrms.attendance.repo.AttendanceDayRepository;
import com.example.hrms.attendance.service.AttendanceQueryService;
import com.example.hrms.attendance.service.AttendanceSummaryService;
import com.example.hrms.domain.Employee;
import com.example.hrms.repo.EmployeeRepository;
import com.example.hrms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/attendance")
@CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@RequiredArgsConstructor
public class AttendanceQueryController {

    private final AttendanceQueryService service;
    private final AttendanceSummaryService summaryService;
    private final EmployeeRepository employeeRepository;
    private final AttendanceDayRepository attendanceDayRepository;
    
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Get daily punch logs for a specific employee.
     * Returns all days of the month with punch times, work hours, status, etc.
     */
    @GetMapping("/logs")
    public ResponseEntity<List<DailyPunchLogDTO>> logs(
            @RequestParam int month, 
            @RequestParam int year,
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) String empCode) {

        // Use default orgId=1L for now (single tenant mode)
        Long orgId = 1L;
        return ResponseEntity.ok(service.getEmployeeLogs(orgId, empId, empCode, month, year));
    }

    /**
     * Get monthly attendance summary for all employees.
     * Returns: empCode, empName, present, absent, leave, halfDays, lateDays, totalWorkMinutes, etc.
     */
    @GetMapping("/summary")
    public ResponseEntity<List<MonthlySummaryDTO>> summary(
            @RequestParam int month,
            @RequestParam int year) {
        
        // Use default orgId=1L for now (single tenant mode)
        Long orgId = 1L;
        return ResponseEntity.ok(summaryService.getSummary(year, month, orgId));
    }

    /**
     * Get list of all employees for the dropdown.
     */
    @GetMapping("/employees")
    public ResponseEntity<List<Map<String, Object>>> getEmployees() {
        
        String tenantId = TenantContext.getTenantIdOrDefault("SASA001");
        List<Employee> employees = employeeRepository.findByTenantId(tenantId);
        List<Map<String, Object>> result = employees.stream().map(e -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", e.getId());
            map.put("empCode", e.getEmpCode());
            map.put("name", safeName(e));
            map.put("department", e.getDepartment());
            map.put("designation", e.getDesignation());
            return map;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }

    /**
     * Alternative summary endpoint for backward compatibility
     */
    @GetMapping("/summary1")
    public ResponseEntity<List<SummaryRowDTO>> summary1(
            @RequestParam int month, 
            @RequestParam int year) {

        // Use default orgId=1L for now (single tenant mode)
        Long orgId = 1L;
        return ResponseEntity.ok(service.getMonthlySummary(orgId, month, year));
    }

    private String safeName(Employee e) {
        try {
            String fn = e.getFirstName();
            String ln = e.getLastName();
            if (fn != null || ln != null) {
                return ((fn != null ? fn : "") + " " + (ln != null ? ln : "")).trim();
            }
        } catch (Exception ignore) {}
        
        try {
            String code = e.getEmpCode();
            if (code != null) return code;
        } catch (Exception ignore) {}
        
        return "—";
    }

    /**
     * Update manual punch time for an attendance day (admin override).
     * Used when an employee missed a punch and admin needs to add it manually.
     */
    @PutMapping("/day/{dayId}/manual-punch")
    public ResponseEntity<Map<String, Object>> updateManualPunch(
            @PathVariable Long dayId,
            @RequestParam(required = false) String manualIn,
            @RequestParam(required = false) String manualOut,
            @RequestParam(required = false) String remarks,
            @RequestHeader(value = "X-User", required = false) String updatedBy) {

        AttendanceDay day = attendanceDayRepository.findById(dayId).orElse(null);
        if (day == null) {
            return ResponseEntity.notFound().build();
        }

        // Parse and set manual times
        if (manualIn != null && !manualIn.isBlank()) {
            LocalTime time = LocalTime.parse(manualIn, TIME_FMT);
            day.setManualIn(LocalDateTime.of(day.getWorkDate(), time));
        }
        if (manualOut != null && !manualOut.isBlank()) {
            LocalTime time = LocalTime.parse(manualOut, TIME_FMT);
            // If the time is before the first IN, it's likely next day
            LocalDate outDate = day.getWorkDate();
            if (day.getFirstIn() != null && time.isBefore(day.getFirstIn().toLocalTime())) {
                outDate = outDate.plusDays(1);
            }
            day.setManualOut(LocalDateTime.of(outDate, time));
        }
        
        if (remarks != null) {
            day.setRemarks(remarks);
        }
        
        day.setManualUpdatedBy(updatedBy != null ? updatedBy : "admin");
        day.setManualUpdatedAt(LocalDateTime.now());
        
        // Recalculate work minutes if both IN and OUT are available
        LocalDateTime effectiveIn = day.getManualIn() != null ? day.getManualIn() : day.getFirstIn();
        LocalDateTime effectiveOut = day.getManualOut() != null ? day.getManualOut() : day.getLastOut();
        
        if (effectiveIn != null && effectiveOut != null) {
            long mins = java.time.Duration.between(effectiveIn, effectiveOut).toMinutes();
            if (mins > 0) {
                day.setTotalWorkMin((int) mins);
                day.setStatus(mins >= 240 ? "PRESENT" : "HALF_DAY");
            }
        }
        
        // Clear the needs review flag after admin update
        day.setNeedsReview(false);
        
        attendanceDayRepository.save(day);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Attendance updated successfully");
        result.put("dayId", day.getId());
        result.put("workMinutes", day.getTotalWorkMin());
        result.put("status", day.getStatus());
        return ResponseEntity.ok(result);
    }

    /**
     * Get all attendance days that need admin review (missing punches, dual shifts, etc.)
     */
    @GetMapping("/needs-review")
    public ResponseEntity<List<Map<String, Object>>> getAttendanceNeedingReview(
            @RequestParam int month,
            @RequestParam int year) {
        
        String tenantId = TenantContext.getTenantIdOrDefault("SASA001");
        
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        
        List<AttendanceDay> days = attendanceDayRepository.findByWorkDateBetween(startDate, endDate)
                .stream()
                .filter(d -> Boolean.TRUE.equals(d.getNeedsReview()))
                .collect(Collectors.toList());
        
        List<Employee> employees = employeeRepository.findAllById(
                days.stream().map(AttendanceDay::getEmployeeId).collect(Collectors.toSet()));
        Map<Long, Employee> empMap = employees.stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
        
        List<Map<String, Object>> result = days.stream().map(d -> {
            Map<String, Object> map = new HashMap<>();
            Employee emp = empMap.get(d.getEmployeeId());
            map.put("dayId", d.getId());
            map.put("date", d.getWorkDate().toString());
            map.put("empCode", emp != null ? emp.getEmpCode() : "");
            map.put("empName", emp != null ? safeName(emp) : "");
            map.put("firstIn", d.getFirstIn() != null ? d.getFirstIn().toLocalTime().format(TIME_FMT) : null);
            map.put("lastOut", d.getLastOut() != null ? d.getLastOut().toLocalTime().format(TIME_FMT) : null);
            map.put("punchCount", d.getPunchCount());
            map.put("missingPunch", d.getMissingPunch());
            map.put("missingPunchType", d.getMissingPunchType());
            map.put("dualShift", d.getDualShift());
            map.put("status", d.getStatus());
            map.put("remarks", d.getRemarks());
            
            // Determine issue type for display
            String issue = "";
            if (Boolean.TRUE.equals(d.getMissingPunch())) {
                issue = "Missing " + (d.getMissingPunchType() != null ? d.getMissingPunchType() : "punch");
            } else if (Boolean.TRUE.equals(d.getDualShift())) {
                issue = "Dual Shift";
            }
            map.put("issue", issue);
            
            return map;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }
}
