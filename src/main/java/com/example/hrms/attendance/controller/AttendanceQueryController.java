package com.example.hrms.attendance.controller;

import com.example.hrms.attendance.domain.AttendanceDay;
import com.example.hrms.attendance.domain.ImportBatch;
import com.example.hrms.attendance.dto.DailyPunchLogDTO;
import com.example.hrms.attendance.dto.MonthlySummaryDTO;
import com.example.hrms.attendance.dto.SummaryRowDTO;
import com.example.hrms.attendance.repo.AttendanceDayRepository;
import com.example.hrms.attendance.repo.ImportBatchRepository;
import com.example.hrms.attendance.service.AttendanceQueryService;
import com.example.hrms.attendance.service.AttendanceSummaryService;
import com.example.hrms.domain.Employee;
import com.example.hrms.repo.EmployeeRepository;
import com.example.hrms.repo.ShiftRepository;
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
@RequiredArgsConstructor
public class AttendanceQueryController {

    private final AttendanceQueryService service;
    private final AttendanceSummaryService summaryService;
    private final EmployeeRepository employeeRepository;
    private final AttendanceDayRepository attendanceDayRepository;
    private final ImportBatchRepository importBatchRepository;
    private final ShiftRepository shiftRepository;
    
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

        String tenantId = TenantContext.getTenantId();
        Long orgId = getOrgIdFromTenant(tenantId);
        return ResponseEntity.ok(service.getEmployeeLogs(orgId, tenantId, empId, empCode, month, year));
    }

    /**
     * Get monthly attendance summary for all employees.
     * Returns: empCode, empName, present, absent, leave, halfDays, lateDays, totalWorkMinutes, etc.
     */
    @GetMapping("/summary")
    public ResponseEntity<List<MonthlySummaryDTO>> summary(
            @RequestParam int month,
            @RequestParam int year) {
        
        String tenantId = TenantContext.getTenantId();
        Long orgId = getOrgIdFromTenant(tenantId);
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

        String tenantId = TenantContext.getTenantId();
        Long orgId = getOrgIdFromTenant(tenantId);
        return ResponseEntity.ok(service.getMonthlySummary(orgId, month, year));
    }
    
    /**
     * Convert tenant ID (String) to org ID (Long) for multi-tenancy.
     */
    /**
     * Dashboard Statistics API - Returns attendance data for the latest uploaded month
     * This endpoint is designed for the main dashboard to show relevant monthly data
     */
    @GetMapping("/dashboard-stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        String tenantId = TenantContext.getTenantId();
        Long orgId = getOrgIdFromTenant(tenantId);
        
        Map<String, Object> stats = new LinkedHashMap<>();
        
        // Get latest import batch to determine which month to show
        Optional<ImportBatch> latestBatch = importBatchRepository.findFirstByOrgIdOrderByYearDescMonthDescUploadedAtDesc(orgId);
        
        int month, year;
        boolean hasAttendanceData = false;
        String monthName = "";
        
        if (latestBatch.isPresent()) {
            ImportBatch batch = latestBatch.get();
            month = batch.getMonth();
            year = batch.getYear();
            hasAttendanceData = true;
            monthName = java.time.Month.of(month).name().charAt(0) + 
                       java.time.Month.of(month).name().substring(1).toLowerCase() + " " + year;
        } else {
            // No attendance data - use current month as placeholder
            java.time.YearMonth now = java.time.YearMonth.now();
            month = now.getMonthValue();
            year = now.getYear();
            monthName = now.getMonth().name().charAt(0) + 
                       now.getMonth().name().substring(1).toLowerCase() + " " + year;
        }
        
        stats.put("month", month);
        stats.put("year", year);
        stats.put("monthName", monthName);
        stats.put("hasAttendanceData", hasAttendanceData);
        
        // Get employee counts
        List<Employee> employees = employeeRepository.findByTenantId(tenantId);
        long totalEmployees = employees.size();
        long activeEmployees = employees.stream()
                .filter(e -> e.getStatus() != null && e.getStatus().name().equals("ACTIVE"))
                .count();
        
        stats.put("totalEmployees", totalEmployees);
        stats.put("activeEmployees", activeEmployees);
        
        // Get shift count
        long totalShifts = shiftRepository.countByTenantId(tenantId);
        stats.put("totalShifts", totalShifts);
        
        if (hasAttendanceData) {
            // Get attendance summary for the latest month
            List<MonthlySummaryDTO> summary = summaryService.getSummary(year, month, orgId);
            
            int totalPresent = 0, totalAbsent = 0, totalLate = 0, totalHalfDaysCount = 0;
            int totalOtDays = 0, totalWorkMins = 0;
            
            for (MonthlySummaryDTO emp : summary) {
                totalPresent += emp.getPresent();
                totalAbsent += emp.getAbsent();
                totalLate += emp.getLateDays();
                totalHalfDaysCount += emp.getHalfDays();
                totalOtDays += emp.getOvertimeDays();
                totalWorkMins += emp.getTotalWorkMinutes();
            }
            
            stats.put("totalPresentDays", totalPresent);
            stats.put("totalAbsentDays", totalAbsent);
            stats.put("totalLateDays", totalLate);
            stats.put("totalHalfDays", totalHalfDaysCount);
            stats.put("totalOtDays", totalOtDays);
            stats.put("totalWorkHours", totalWorkMins / 60);
            stats.put("employeesWithData", summary.size());
            
            // Calculate averages
            int daysInMonth = java.time.YearMonth.of(year, month).lengthOfMonth();
            double avgAttendanceRate = 0;
            if (summary.size() > 0 && daysInMonth > 0) {
                avgAttendanceRate = (double) totalPresent / (summary.size() * daysInMonth) * 100;
            }
            stats.put("attendanceRate", Math.round(avgAttendanceRate * 10) / 10.0);
            
            // Per-employee averages for the month
            stats.put("avgPresentDays", summary.size() > 0 ? Math.round((double) totalPresent / summary.size() * 10) / 10.0 : 0);
            stats.put("avgAbsentDays", summary.size() > 0 ? Math.round((double) totalAbsent / summary.size() * 10) / 10.0 : 0);
            stats.put("avgLateDays", summary.size() > 0 ? Math.round((double) totalLate / summary.size() * 10) / 10.0 : 0);
            
        } else {
            stats.put("totalPresentDays", 0);
            stats.put("totalAbsentDays", 0);
            stats.put("totalLateDays", 0);
            stats.put("totalHalfDays", 0);
            stats.put("totalOtDays", 0);
            stats.put("totalWorkHours", 0);
            stats.put("employeesWithData", 0);
            stats.put("attendanceRate", 0);
            stats.put("avgPresentDays", 0);
            stats.put("avgAbsentDays", 0);
            stats.put("avgLateDays", 0);
        }
        
        return ResponseEntity.ok(stats);
    }

    private Long getOrgIdFromTenant(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) {
            return 1L;
        }
        return (long) Math.abs(tenantId.hashCode()) + 10000L;
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
     * Can also override the status directly (e.g., mark as ABSENT despite having punches).
     */
    @PutMapping("/day/{dayId}/manual-punch")
    public ResponseEntity<Map<String, Object>> updateManualPunch(
            @PathVariable Long dayId,
            @RequestParam(required = false) String manualIn,
            @RequestParam(required = false) String manualOut,
            @RequestParam(required = false) String remarks,
            @RequestParam(required = false) String statusOverride,
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
        
        // Check if admin is overriding status directly
        if (statusOverride != null && !statusOverride.isBlank()) {
            // Admin override - set status directly regardless of punch times
            day.setStatus(statusOverride.toUpperCase());
            // If marking as ABSENT, optionally reset work minutes
            if ("ABSENT".equalsIgnoreCase(statusOverride)) {
                day.setTotalWorkMin(0);
            }
        } else {
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

    /**
     * Missing Punch Dashboard - Get all attendance records with missing IN/OUT for a month.
     * Groups by employee and shows:
     * - Total missing IN count
     * - Total missing OUT count  
     * - List of dates with issues
     */
    @GetMapping("/missing-punch-dashboard")
    public ResponseEntity<Map<String, Object>> getMissingPunchDashboard(
            @RequestParam int month,
            @RequestParam int year) {
        
        String tenantId = TenantContext.getTenantId();
        
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        
        // Get all attendance days for the month
        List<AttendanceDay> allDays = attendanceDayRepository.findByTenantIdAndWorkDateBetween(tenantId, startDate, endDate);
        
        // Filter records with issues: missing punch, odd punch count, or needs review
        // BUT exclude records that have been manually fixed (have manual_in or manual_out set)
        List<AttendanceDay> problemDays = allDays.stream()
                .filter(d -> {
                    // Skip if already manually fixed
                    boolean hasManualIn = d.getManualIn() != null;
                    boolean hasManualOut = d.getManualOut() != null;
                    boolean hasFirstIn = d.getFirstIn() != null;
                    boolean hasLastOut = d.getLastOut() != null;
                    
                    // Effective IN/OUT (manual overrides actual)
                    boolean hasEffectiveIn = hasManualIn || hasFirstIn;
                    boolean hasEffectiveOut = hasManualOut || hasLastOut;
                    
                    // If both effective times exist, not a problem
                    if (hasEffectiveIn && hasEffectiveOut) {
                        return false;
                    }
                    
                    // Check for issues
                    return Boolean.TRUE.equals(d.getMissingPunch()) 
                            || Boolean.TRUE.equals(d.getNeedsReview())
                            || (d.getPunchCount() != null && d.getPunchCount() % 2 != 0)
                            || !hasEffectiveIn 
                            || !hasEffectiveOut;
                })
                .collect(Collectors.toList());
        
        // Get employee details
        Set<Long> empIds = problemDays.stream().map(AttendanceDay::getEmployeeId).collect(Collectors.toSet());
        List<Employee> employees = employeeRepository.findAllById(empIds);
        Map<Long, Employee> empMap = employees.stream()
                .collect(Collectors.toMap(Employee::getId, e -> e, (a, b) -> a));
        
        // Group by employee
        Map<Long, List<AttendanceDay>> byEmployee = problemDays.stream()
                .collect(Collectors.groupingBy(AttendanceDay::getEmployeeId));
        
        // Build employee summary list
        List<Map<String, Object>> employeeSummaries = new ArrayList<>();
        int totalMissingIn = 0;
        int totalMissingOut = 0;
        
        for (Map.Entry<Long, List<AttendanceDay>> entry : byEmployee.entrySet()) {
            Long empId = entry.getKey();
            List<AttendanceDay> empDays = entry.getValue();
            Employee emp = empMap.get(empId);
            
            int missingInCount = 0;
            int missingOutCount = 0;
            List<Map<String, Object>> dayDetails = new ArrayList<>();
            
            for (AttendanceDay d : empDays) {
                String issueType = determineIssueType(d);
                if (issueType.contains("IN")) missingInCount++;
                if (issueType.contains("OUT")) missingOutCount++;
                
                // Calculate effective times (manual overrides actual)
                LocalDateTime rawIn = d.getManualIn() != null ? d.getManualIn() : d.getFirstIn();
                LocalDateTime rawOut = d.getManualOut() != null ? d.getManualOut() : d.getLastOut();
                
                // Smart detection: If only one punch exists, determine if it's IN or OUT based on time
                // Shift: 9:00 AM - 5:30 PM, midpoint is ~1:15 PM (13:15)
                LocalDateTime effectiveIn = null;
                LocalDateTime effectiveOut = null;
                boolean punchDetectedAsOut = false;
                
                if (rawIn != null && rawOut != null) {
                    // Both exist - use as-is
                    effectiveIn = rawIn;
                    effectiveOut = rawOut;
                } else if (rawIn != null && rawOut == null) {
                    // Only "IN" exists - check if it's actually an OUT based on time
                    int hour = rawIn.getHour();
                    if (hour >= 13) { // After 1 PM - likely OUT time
                        effectiveOut = rawIn;
                        punchDetectedAsOut = true;
                    } else {
                        effectiveIn = rawIn;
                    }
                } else if (rawOut != null && rawIn == null) {
                    // Only "OUT" exists - check if it's actually an IN based on time
                    int hour = rawOut.getHour();
                    if (hour < 13) { // Before 1 PM - likely IN time
                        effectiveIn = rawOut;
                    } else {
                        effectiveOut = rawOut;
                    }
                }
                
                Map<String, Object> dayInfo = new HashMap<>();
                dayInfo.put("dayId", d.getId());
                dayInfo.put("date", d.getWorkDate().toString());
                dayInfo.put("dayOfWeek", d.getWorkDate().getDayOfWeek().toString().substring(0, 3));
                dayInfo.put("firstIn", effectiveIn != null ? effectiveIn.toLocalTime().format(TIME_FMT) : null);
                dayInfo.put("lastOut", effectiveOut != null ? effectiveOut.toLocalTime().format(TIME_FMT) : null);
                dayInfo.put("punchCount", d.getPunchCount());
                dayInfo.put("workMins", d.getTotalWorkMin());
                dayInfo.put("issue", issueType);
                dayInfo.put("status", d.getStatus());
                // Add flags to show if manual override was applied
                dayInfo.put("hasManualIn", d.getManualIn() != null);
                dayInfo.put("hasManualOut", d.getManualOut() != null);
                dayInfo.put("punchDetectedAsOut", punchDetectedAsOut);
                dayDetails.add(dayInfo);
            }
            
            totalMissingIn += missingInCount;
            totalMissingOut += missingOutCount;
            
            Map<String, Object> empSummary = new HashMap<>();
            empSummary.put("employeeId", empId);
            empSummary.put("empCode", emp != null ? emp.getEmpCode() : "");
            empSummary.put("empName", emp != null ? safeName(emp) : "Unknown");
            empSummary.put("department", emp != null ? emp.getDepartment() : "");
            empSummary.put("missingInCount", missingInCount);
            empSummary.put("missingOutCount", missingOutCount);
            empSummary.put("totalIssues", empDays.size());
            empSummary.put("days", dayDetails);
            employeeSummaries.add(empSummary);
        }
        
        // Sort by total issues descending
        employeeSummaries.sort((a, b) -> 
                Integer.compare((int) b.get("totalIssues"), (int) a.get("totalIssues")));
        
        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("month", month);
        response.put("year", year);
        response.put("totalEmployeesWithIssues", employeeSummaries.size());
        response.put("totalMissingIn", totalMissingIn);
        response.put("totalMissingOut", totalMissingOut);
        response.put("totalIssues", problemDays.size());
        response.put("employees", employeeSummaries);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Bulk fix missing punches - apply shift timing for missing IN/OUT
     */
    @PostMapping("/bulk-fix-missing-punch")
    public ResponseEntity<Map<String, Object>> bulkFixMissingPunch(
            @RequestBody Map<String, Object> request) {
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fixes = (List<Map<String, Object>>) request.get("fixes");
        String updatedBy = (String) request.getOrDefault("updatedBy", "admin");
        
        int successCount = 0;
        int errorCount = 0;
        List<String> errors = new ArrayList<>();
        
        for (Map<String, Object> fix : fixes) {
            try {
                Long dayId = Long.valueOf(fix.get("dayId").toString());
                String manualIn = (String) fix.get("manualIn");
                String manualOut = (String) fix.get("manualOut");
                String remarks = (String) fix.get("remarks");
                
                AttendanceDay day = attendanceDayRepository.findById(dayId).orElse(null);
                if (day == null) {
                    errors.add("Day ID " + dayId + " not found");
                    errorCount++;
                    continue;
                }
                
                // Apply manual times (times are in HH:mm format, local time)
                if (manualIn != null && !manualIn.isBlank()) {
                    LocalTime time = LocalTime.parse(manualIn, TIME_FMT);
                    LocalDateTime manualInTime = LocalDateTime.of(day.getWorkDate(), time);
                    day.setManualIn(manualInTime);
                    // Also set firstIn if it was null (so attendance shows correctly)
                    if (day.getFirstIn() == null) {
                        day.setFirstIn(manualInTime);
                    }
                }
                if (manualOut != null && !manualOut.isBlank()) {
                    LocalTime time = LocalTime.parse(manualOut, TIME_FMT);
                    LocalDate outDate = day.getWorkDate();
                    // If time is before 6 AM, it's likely next day (cross midnight)
                    if (time.getHour() < 6) {
                        outDate = outDate.plusDays(1);
                    }
                    LocalDateTime manualOutTime = LocalDateTime.of(outDate, time);
                    day.setManualOut(manualOutTime);
                    // Also set lastOut if it was null
                    if (day.getLastOut() == null) {
                        day.setLastOut(manualOutTime);
                    }
                }
                
                if (remarks != null && !remarks.isBlank()) {
                    day.setRemarks(remarks);
                }
                
                // Recalculate work minutes
                LocalDateTime effectiveIn = day.getManualIn() != null ? day.getManualIn() : day.getFirstIn();
                LocalDateTime effectiveOut = day.getManualOut() != null ? day.getManualOut() : day.getLastOut();
                
                if (effectiveIn != null && effectiveOut != null) {
                    long mins = java.time.Duration.between(effectiveIn, effectiveOut).toMinutes();
                    if (mins > 0) {
                        day.setTotalWorkMin((int) mins);
                        day.setStatus(mins >= 240 ? "PRESENT" : "HALF_DAY");
                    }
                }
                
                // Clear flags
                day.setMissingPunch(false);
                day.setNeedsReview(false);
                day.setManualUpdatedBy(updatedBy);
                day.setManualUpdatedAt(LocalDateTime.now());
                
                attendanceDayRepository.save(day);
                successCount++;
                
            } catch (Exception e) {
                errors.add("Error processing fix: " + e.getMessage());
                errorCount++;
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", errorCount == 0);
        response.put("successCount", successCount);
        response.put("errorCount", errorCount);
        response.put("errors", errors);
        response.put("message", String.format("Fixed %d records, %d errors", successCount, errorCount));
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get OT summary for a month - overtime days and hours per employee
     */
    @GetMapping("/ot-summary")
    public ResponseEntity<List<Map<String, Object>>> getOTSummary(
            @RequestParam int month,
            @RequestParam int year) {
        
        String tenantId = TenantContext.getTenantId();
        
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        
        List<AttendanceDay> allDays = attendanceDayRepository.findByTenantIdAndWorkDateBetween(tenantId, startDate, endDate);
        
        // Get employee details
        Set<Long> empIds = allDays.stream().map(AttendanceDay::getEmployeeId).collect(Collectors.toSet());
        List<Employee> employees = employeeRepository.findAllById(empIds);
        Map<Long, Employee> empMap = employees.stream()
                .collect(Collectors.toMap(Employee::getId, e -> e, (a, b) -> a));
        
        // Group by employee and calculate OT
        Map<Long, List<AttendanceDay>> byEmployee = allDays.stream()
                .collect(Collectors.groupingBy(AttendanceDay::getEmployeeId));
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Map.Entry<Long, List<AttendanceDay>> entry : byEmployee.entrySet()) {
            Long empId = entry.getKey();
            List<AttendanceDay> empDays = entry.getValue();
            Employee emp = empMap.get(empId);
            
            int otDays = 0;
            int otEligibleMins = 0;
            int otApprovedMins = 0;
            int presentDays = 0;
            int totalWorkMins = 0;
            
            for (AttendanceDay d : empDays) {
                if ("OT_DAY".equals(d.getStatus()) || Boolean.TRUE.equals(d.getIsOvertimeDay())) {
                    otDays++;
                }
                if ("PRESENT".equals(d.getStatus()) || "HALF_DAY".equals(d.getStatus())) {
                    presentDays++;
                }
                if (d.getTotalOTEligibleMin() != null) {
                    otEligibleMins += d.getTotalOTEligibleMin();
                }
                if (d.getTotalOTApprovedMin() != null) {
                    otApprovedMins += d.getTotalOTApprovedMin();
                }
                if (d.getTotalWorkMin() != null) {
                    totalWorkMins += d.getTotalWorkMin();
                }
            }
            
            Map<String, Object> empOT = new HashMap<>();
            empOT.put("employeeId", empId);
            empOT.put("empCode", emp != null ? emp.getEmpCode() : "");
            empOT.put("empName", emp != null ? safeName(emp) : "Unknown");
            empOT.put("presentDays", presentDays);
            empOT.put("otDays", otDays);
            empOT.put("otEligibleHours", String.format("%.1f", otEligibleMins / 60.0));
            empOT.put("otApprovedHours", String.format("%.1f", otApprovedMins / 60.0));
            empOT.put("totalWorkHours", String.format("%.1f", totalWorkMins / 60.0));
            
            result.add(empOT);
        }
        
        // Sort by OT hours descending
        result.sort((a, b) -> {
            double aOT = Double.parseDouble((String) a.get("otEligibleHours"));
            double bOT = Double.parseDouble((String) b.get("otEligibleHours"));
            return Double.compare(bOT, aOT);
        });
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Determine the issue type for an attendance day
     */
    private String determineIssueType(AttendanceDay d) {
        if (d.getFirstIn() == null && d.getLastOut() == null) {
            return "No Punch";
        }
        if (d.getFirstIn() == null) {
            return "Missing IN";
        }
        if (d.getLastOut() == null) {
            return "Missing OUT";
        }
        if (d.getPunchCount() != null && d.getPunchCount() % 2 != 0) {
            return "Odd Punches (" + d.getPunchCount() + ")";
        }
        if (Boolean.TRUE.equals(d.getMissingPunch())) {
            return "Missing " + (d.getMissingPunchType() != null ? d.getMissingPunchType() : "Punch");
        }
        return "Needs Review";
    }
    
    // ========= LATE/EARLY APPROVAL ENDPOINTS =========
    
    /**
     * Approve late arrival for a specific attendance day.
     * Once approved, late time won't be counted in payroll calculations.
     */
    @PostMapping("/approve-late/{dayId}")
    public ResponseEntity<Map<String, Object>> approveLate(
            @PathVariable Long dayId,
            @RequestBody(required = false) Map<String, String> body) {
        
        String tenantId = TenantContext.getTenantIdOrDefault("SASA001");
        String remarks = body != null ? body.get("remarks") : null;
        String approvedBy = body != null ? body.get("approvedBy") : "Admin";
        
        Optional<AttendanceDay> dayOpt = attendanceDayRepository.findById(dayId);
        if (dayOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        AttendanceDay day = dayOpt.get();
        
        // Security check - ensure it belongs to the same tenant
        if (!tenantId.equals(day.getTenantId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }
        
        day.setLateApproved(true);
        day.setApprovedBy(approvedBy);
        day.setApprovedAt(LocalDateTime.now());
        if (remarks != null && !remarks.isBlank()) {
            day.setApprovalRemarks(remarks);
        }
        
        attendanceDayRepository.save(day);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Late arrival approved successfully",
            "dayId", dayId,
            "date", day.getWorkDate().toString()
        ));
    }
    
    /**
     * Approve early departure for a specific attendance day.
     * Once approved, early out time won't be counted in payroll calculations.
     */
    @PostMapping("/approve-early-out/{dayId}")
    public ResponseEntity<Map<String, Object>> approveEarlyOut(
            @PathVariable Long dayId,
            @RequestBody(required = false) Map<String, String> body) {
        
        String tenantId = TenantContext.getTenantIdOrDefault("SASA001");
        String remarks = body != null ? body.get("remarks") : null;
        String approvedBy = body != null ? body.get("approvedBy") : "Admin";
        
        Optional<AttendanceDay> dayOpt = attendanceDayRepository.findById(dayId);
        if (dayOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        AttendanceDay day = dayOpt.get();
        
        // Security check
        if (!tenantId.equals(day.getTenantId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }
        
        day.setEarlyOutApproved(true);
        day.setApprovedBy(approvedBy);
        day.setApprovedAt(LocalDateTime.now());
        if (remarks != null && !remarks.isBlank()) {
            day.setApprovalRemarks(remarks);
        }
        
        attendanceDayRepository.save(day);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Early departure approved successfully",
            "dayId", dayId,
            "date", day.getWorkDate().toString()
        ));
    }
    
    /**
     * Approve both late arrival and early departure for a specific day.
     */
    @PostMapping("/approve-attendance/{dayId}")
    public ResponseEntity<Map<String, Object>> approveAttendance(
            @PathVariable Long dayId,
            @RequestBody(required = false) Map<String, Object> body) {
        
        String tenantId = TenantContext.getTenantIdOrDefault("SASA001");
        String remarks = body != null ? (String) body.get("remarks") : null;
        String approvedBy = body != null ? (String) body.get("approvedBy") : "Admin";
        Boolean approveLate = body != null ? (Boolean) body.get("approveLate") : false;
        Boolean approveEarly = body != null ? (Boolean) body.get("approveEarly") : false;
        
        Optional<AttendanceDay> dayOpt = attendanceDayRepository.findById(dayId);
        if (dayOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        AttendanceDay day = dayOpt.get();
        
        // Security check
        if (!tenantId.equals(day.getTenantId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }
        
        if (Boolean.TRUE.equals(approveLate)) {
            day.setLateApproved(true);
        }
        if (Boolean.TRUE.equals(approveEarly)) {
            day.setEarlyOutApproved(true);
        }
        
        day.setApprovedBy(approvedBy);
        day.setApprovedAt(LocalDateTime.now());
        if (remarks != null && !remarks.isBlank()) {
            day.setApprovalRemarks(remarks);
        }
        
        attendanceDayRepository.save(day);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Attendance approved successfully",
            "dayId", dayId,
            "date", day.getWorkDate().toString(),
            "lateApproved", Boolean.TRUE.equals(day.getLateApproved()),
            "earlyOutApproved", Boolean.TRUE.equals(day.getEarlyOutApproved())
        ));
    }
    
    /**
     * Revoke late/early approval for a specific day.
     */
    @PostMapping("/revoke-approval/{dayId}")
    public ResponseEntity<Map<String, Object>> revokeApproval(
            @PathVariable Long dayId,
            @RequestBody(required = false) Map<String, Object> body) {
        
        String tenantId = TenantContext.getTenantIdOrDefault("SASA001");
        Boolean revokeLate = body != null ? (Boolean) body.get("revokeLate") : true;
        Boolean revokeEarly = body != null ? (Boolean) body.get("revokeEarly") : true;
        
        Optional<AttendanceDay> dayOpt = attendanceDayRepository.findById(dayId);
        if (dayOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        AttendanceDay day = dayOpt.get();
        
        // Security check
        if (!tenantId.equals(day.getTenantId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }
        
        if (Boolean.TRUE.equals(revokeLate)) {
            day.setLateApproved(false);
        }
        if (Boolean.TRUE.equals(revokeEarly)) {
            day.setEarlyOutApproved(false);
        }
        
        // Clear approval info if both are revoked
        if (!Boolean.TRUE.equals(day.getLateApproved()) && !Boolean.TRUE.equals(day.getEarlyOutApproved())) {
            day.setApprovedBy(null);
            day.setApprovedAt(null);
            day.setApprovalRemarks(null);
        }
        
        attendanceDayRepository.save(day);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Approval revoked",
            "dayId", dayId
        ));
    }
}
