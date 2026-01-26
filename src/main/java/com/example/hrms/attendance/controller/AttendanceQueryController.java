package com.example.hrms.attendance.controller;

import com.example.hrms.attendance.domain.AttendanceDay;
import com.example.hrms.attendance.domain.ImportBatch;
import com.example.hrms.attendance.dto.AttendanceLogsResponse;
import com.example.hrms.attendance.dto.DailyPunchLogDTO;
import com.example.hrms.attendance.dto.MonthlySummaryDTO;
import com.example.hrms.attendance.dto.SummaryRowDTO;
import com.example.hrms.attendance.repo.AttendanceDayRepository;
import com.example.hrms.attendance.repo.AttendancePunchRepository;
import com.example.hrms.attendance.repo.ImportBatchRepository;
import com.example.hrms.attendance.service.AttendanceQueryService;
import com.example.hrms.attendance.service.AttendanceSummaryService;
import com.example.hrms.attendance.service.AttendanceExcelService;
import com.example.hrms.domain.enums.RoundingRule;
import com.example.hrms.domain.Employee;
import com.example.hrms.domain.Holiday;
import com.example.hrms.payroll.domain.Payroll;
import com.example.hrms.payroll.repo.PayrollRepository;
import com.example.hrms.repo.EmployeeRepository;
import com.example.hrms.repo.HolidayRepository;
import com.example.hrms.repo.ShiftRepository;
import com.example.hrms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@Slf4j
public class AttendanceQueryController {

    private final AttendanceQueryService service;
    private final AttendanceSummaryService summaryService;
    private final AttendanceExcelService excelService;
    private final EmployeeRepository employeeRepository;
    private final AttendanceDayRepository attendanceDayRepository;
    private final AttendancePunchRepository punchRepository;
    private final ImportBatchRepository importBatchRepository;
    private final ShiftRepository shiftRepository;
    private final HolidayRepository holidayRepository;
    private final PayrollRepository payrollRepository;
    
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    
    /**
     * Helper method to convert RoundingRule enum to minutes.
     */
    private int getRoundingMinutes(RoundingRule rule) {
        if (rule == null) return 0;
        return switch (rule) {
            case NEAREST_5, UP_5, DOWN_5 -> 5;
            case NEAREST_15 -> 15;
            case NEAREST_30 -> 30;
            case NONE -> 0;
        };
    }

    /**
     * Get daily punch logs for a specific employee.
     * Returns all days of the month with punch times, work hours, status, etc.
     * Includes OT/Late deduction totals in the response.
     */
    @GetMapping("/logs")
    public ResponseEntity<AttendanceLogsResponse> logs(
            @RequestParam int month, 
            @RequestParam int year,
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) String empCode) {

        String tenantId = TenantContext.getTenantId();
        Long orgId = getOrgIdFromTenant(tenantId);
        List<DailyPunchLogDTO> logs = service.getEmployeeLogs(orgId, tenantId, empId, empCode, month, year);
        
        // Calculate totals
        int totalOtDeductionMins = logs.stream()
                .mapToInt(DailyPunchLogDTO::getOtDeductionMins)
                .sum();
        int totalLateDeductionMins = logs.stream()
                .mapToInt(DailyPunchLogDTO::getLateDeductionMins)
                .sum();
        int totalEarlyDeductionMins = logs.stream()
                .mapToInt(DailyPunchLogDTO::getEarlyDeductionMins)
                .sum();
        
        // Calculate paid/unpaid leave days
        Long empIdForLeave = null;
        String empCodeForLeave = empCode;
        if (empId != null) {
            empIdForLeave = empId;
            Employee emp = employeeRepository.findById(empId).orElse(null);
            if (emp != null) {
                empCodeForLeave = emp.getEmpCode();
            }
        }
        
        int paidLeaveDays = 0;
        int unpaidLeaveDays = 0;
        if (empCodeForLeave != null && tenantId != null) {
            LocalDate startDate = LocalDate.of(year, month, 1);
            LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
            
            // Query APPROVED leave records for this employee in this month
            com.example.hrms.leave.repo.EmployeeLeaveRepository leaveRepo = 
                    ((com.example.hrms.attendance.service.impl.AttendanceQueryServiceImpl) service).getLeaveRepo();
            List<com.example.hrms.leave.domain.EmployeeLeave> approvedLeaves = leaveRepo
                    .findByTenantIdAndEmpIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                            tenantId, empCodeForLeave, endDate, startDate);
            
            log.info("AttendanceQueryController: Found {} leave records for employee {} in {} {}", 
                    approvedLeaves.size(), empCodeForLeave, year, month);
            
            // Count paid vs unpaid leave days
            for (com.example.hrms.leave.domain.EmployeeLeave leave : approvedLeaves) {
                log.debug("Leave {} - Status: {}, Payable: {}, LeaveType: {}, IsPaid: {}", 
                        leave.getId(), leave.getStatus(), leave.getPayable(), 
                        leave.getLeaveType() != null ? leave.getLeaveType().getName() : "null",
                        leave.getLeaveType() != null ? leave.getLeaveType().getIsPaid() : "null");
                
                if (leave.getStatus() == com.example.hrms.leave.domain.enums.LeaveStatus.APPROVED) {
                    LocalDate leaveDate = leave.getStartDate();
                    while (!leaveDate.isAfter(leave.getEndDate()) && !leaveDate.isAfter(endDate)) {
                        if (!leaveDate.isBefore(startDate)) {
                            // Check if leave type is paid - paid leave types always count as paid leave
                            // The payable flag is used for balance tracking, but if leave type is paid, it should always be counted as paid
                            boolean isPaidLeave = leave.getLeaveType() != null && 
                                                  Boolean.TRUE.equals(leave.getLeaveType().getIsPaid());
                            
                            log.debug("Date {} - isPaidLeave: {} (leaveType.isPaid: {})", 
                                    leaveDate, isPaidLeave,
                                    leave.getLeaveType() != null ? leave.getLeaveType().getIsPaid() : null);
                            
                            if (isPaidLeave) {
                                paidLeaveDays++;
                            } else {
                                unpaidLeaveDays++;
                            }
                        }
                        leaveDate = leaveDate.plusDays(1);
                    }
                } else {
                    log.debug("Leave {} not APPROVED (status: {})", leave.getId(), leave.getStatus());
                }
            }
            
            log.info("AttendanceQueryController: Calculated paidLeaveDays: {}, unpaidLeaveDays: {} for employee {} in {} {}", 
                    paidLeaveDays, unpaidLeaveDays, empCodeForLeave, year, month);
        }
        
        AttendanceLogsResponse response = AttendanceLogsResponse.builder()
                .logs(logs)
                .totalOtDeductionMins(totalOtDeductionMins)
                .totalLateDeductionMins(totalLateDeductionMins)
                .totalEarlyDeductionMins(totalEarlyDeductionMins)
                .paidLeaveDays(paidLeaveDays)
                .unpaidLeaveDays(unpaidLeaveDays)
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Export attendance logs for a specific employee to Excel.
     */
    @GetMapping("/logs/export")
    public ResponseEntity<byte[]> exportLogs(
            @RequestParam int month, 
            @RequestParam int year,
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) String empCode) throws IOException {
        
        String tenantId = TenantContext.getTenantId();
        Long orgId = getOrgIdFromTenant(tenantId);
        List<DailyPunchLogDTO> logs = service.getEmployeeLogs(orgId, tenantId, empId, empCode, month, year);
        
        if (logs.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        
        String exportEmpCode = empCode != null ? empCode : 
            (empId != null ? employeeRepository.findById(empId)
                .map(Employee::getEmpCode)
                .orElse("Employee") : "Employee");
        
        byte[] excelData = excelService.exportAttendanceLogs(tenantId, exportEmpCode, month, year, logs);
        
        String filename = "Attendance_" + exportEmpCode + "_" + 
            String.format("%02d", month) + "_" + year + ".xlsx";
        
        return ResponseEntity.ok()
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(excelData);
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
     * Dashboard Statistics API - Returns comprehensive insights for the latest attendance month
     * Optimized for single query with caching support on frontend
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
        
        // ===== EMPLOYEE COUNTS =====
        List<Employee> employees = employeeRepository.findByTenantId(tenantId);
        long totalEmployees = employees.size();
        long activeEmployees = employees.stream()
                .filter(e -> e.getStatus() != null && e.getStatus().name().equals("ACTIVE"))
                .count();
        
        stats.put("totalEmployees", totalEmployees);
        stats.put("activeEmployees", activeEmployees);
        
        // ===== SHIFTS COUNT =====
        long totalShifts = shiftRepository.countByTenantId(tenantId);
        stats.put("totalShifts", totalShifts);
        
        // ===== HOLIDAYS THIS MONTH =====
        java.time.YearMonth ym = java.time.YearMonth.of(year, month);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        List<Holiday> holidays = holidayRepository.findByTenantIdAndHolidayDateBetweenAndActiveTrue(tenantId, monthStart, monthEnd);
        
        List<Map<String, Object>> holidayList = holidays.stream()
            .map(h -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("date", h.getHolidayDate().toString());
                m.put("name", h.getName());
                m.put("isPaid", h.getIsPaid());
                m.put("isOptional", h.getIsOptional());
                return m;
            })
            .collect(Collectors.toList());
        stats.put("holidays", holidayList);
        
        if (hasAttendanceData) {
            // Get attendance summary for the latest month
            List<MonthlySummaryDTO> summary = summaryService.getSummary(year, month, orgId);
            int daysInMonth = ym.lengthOfMonth();
            
            // ===== ATTENDANCE METRICS =====
            int totalPresent = 0, totalAbsent = 0, totalLate = 0;
            for (MonthlySummaryDTO emp : summary) {
                totalPresent += emp.getPresent();
                totalAbsent += emp.getAbsent();
                totalLate += emp.getLateDays();
            }
            
            double avgAttendanceRate = summary.size() > 0 && daysInMonth > 0 
                ? (double) totalPresent / (summary.size() * daysInMonth) * 100 : 0;
            stats.put("attendanceRate", Math.round(avgAttendanceRate * 10) / 10.0);
            stats.put("employeesWithData", summary.size());
            
            // ===== TOP 5 BEST ATTENDANCE =====
            List<Map<String, Object>> topAttendance = summary.stream()
                .filter(e -> e.getPresent() > 0)
                .sorted((a, b) -> {
                    double rateA = (double) a.getPresent() / daysInMonth * 100;
                    double rateB = (double) b.getPresent() / daysInMonth * 100;
                    return Double.compare(rateB, rateA);
                })
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("empCode", e.getEmpCode());
                    m.put("name", e.getEmpName() != null ? e.getEmpName() : e.getName());
                    m.put("presentDays", e.getPresent());
                    m.put("rate", Math.round((double) e.getPresent() / daysInMonth * 1000) / 10.0);
                    return m;
                })
                .collect(Collectors.toList());
            stats.put("topAttendance", topAttendance);
            
            // ===== TOP 5 MOST LATE =====
            List<Map<String, Object>> topLate = summary.stream()
                .filter(e -> e.getLateDays() > 0)
                .sorted((a, b) -> Integer.compare(b.getLateDays(), a.getLateDays()))
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("empCode", e.getEmpCode());
                    m.put("name", e.getEmpName() != null ? e.getEmpName() : e.getName());
                    m.put("lateDays", e.getLateDays());
                    m.put("lateMinutes", e.getTotalLateMinutes());
                    return m;
                })
                .collect(Collectors.toList());
            stats.put("topLate", topLate);
            
            // ===== PEAK ABSENT DAY =====
            List<AttendanceDay> allDays = attendanceDayRepository.findByWorkDateBetween(monthStart, monthEnd)
                .stream()
                .filter(d -> tenantId.equals(d.getTenantId()))
                .collect(Collectors.toList());
            
            Map<LocalDate, Long> absentByDate = allDays.stream()
                .filter(d -> "ABSENT".equals(d.getStatus()))
                .collect(Collectors.groupingBy(AttendanceDay::getWorkDate, Collectors.counting()));
            
            if (!absentByDate.isEmpty()) {
                Map.Entry<LocalDate, Long> peakAbsent = absentByDate.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);
                if (peakAbsent != null) {
                    Map<String, Object> peakDay = new LinkedHashMap<>();
                    peakDay.put("date", peakAbsent.getKey().toString());
                    peakDay.put("dayName", peakAbsent.getKey().getDayOfWeek().name());
                    peakDay.put("absentCount", peakAbsent.getValue());
                    stats.put("peakAbsentDay", peakDay);
                }
            }
            
            // ===== DEVICE-WISE PUNCH STATS =====
            List<ImportBatch> batches = importBatchRepository.findByOrgIdAndMonthAndYear(orgId, month, year);
            List<Map<String, Object>> deviceStats = batches.stream()
                .map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("deviceCode", b.getDeviceCode() != null ? b.getDeviceCode() : "DEFAULT");
                    m.put("totalRows", b.getTotalRows());
                    m.put("successRows", b.getSuccessRows());
                    m.put("errorRows", b.getErrorRows());
                    m.put("uploadedAt", b.getUploadedAt() != null ? b.getUploadedAt().toString() : null);
                    return m;
                })
                .collect(Collectors.toList());
            stats.put("deviceStats", deviceStats);
            
            // ===== EMPLOYEES PUNCHING AT MULTIPLE DEVICES =====
            // This could indicate: buddy punching, dual-location work, or device issues
            java.time.Instant monthStartInstant = monthStart.atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toInstant();
            java.time.Instant monthEndInstant = monthEnd.plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toInstant();
            
            List<Object[]> multiDeviceResults = punchRepository.findEmployeesWithMultipleDevices(orgId, monthStartInstant, monthEndInstant);
            
            List<Map<String, Object>> multiDeviceEmployees = new ArrayList<>();
            for (Object[] row : multiDeviceResults) {
                Long empId = (Long) row[0];
                Long deviceCount = (Long) row[1];
                
                // Get employee name
                Employee emp = employees.stream()
                    .filter(e -> e.getId().equals(empId))
                    .findFirst()
                    .orElse(null);
                
                // Get list of devices this employee punched at
                List<String> deviceList = punchRepository.findDistinctDevicesByEmployee(empId, monthStartInstant, monthEndInstant);
                
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("empId", empId);
                m.put("empCode", emp != null ? emp.getEmpCode() : "N/A");
                m.put("name", emp != null ? (emp.getFirstName() + " " + (emp.getLastName() != null ? emp.getLastName() : "")).trim() : "Unknown");
                m.put("deviceCount", deviceCount);
                m.put("devices", deviceList);
                multiDeviceEmployees.add(m);
                
                // Limit to top 10
                if (multiDeviceEmployees.size() >= 10) break;
            }
            stats.put("multiDeviceEmployees", multiDeviceEmployees);
            
        } else {
            stats.put("attendanceRate", 0);
            stats.put("employeesWithData", 0);
            stats.put("topAttendance", List.of());
            stats.put("topLate", List.of());
            stats.put("deviceStats", List.of());
            stats.put("multiDeviceEmployees", List.of());
        }
        
        // ===== PAYROLL SUMMARY =====
        List<Payroll> payrolls = payrollRepository.findByTenantIdAndYearAndMonth(tenantId, year, month);
        
        if (!payrolls.isEmpty()) {
            java.math.BigDecimal totalNet = payrolls.stream()
                .map(p -> p.getNetSalary() != null ? p.getNetSalary() : java.math.BigDecimal.ZERO)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            
            stats.put("payrollGenerated", true);
            stats.put("payrollCount", payrolls.size());
            stats.put("totalPayrollAmount", totalNet);
            
            // Top 5 highest earners
            List<Map<String, Object>> topEarners = payrolls.stream()
                .filter(p -> p.getNetSalary() != null && p.getNetSalary().compareTo(BigDecimal.ZERO) > 0)
                .sorted((a, b) -> b.getNetSalary().compareTo(a.getNetSalary()))
                .limit(5)
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("empId", p.getEmpId());
                    m.put("name", p.getEmpName());
                    m.put("netSalary", p.getNetSalary());
                    m.put("grossSalary", p.getGrossSalary());
                    return m;
                })
                .collect(Collectors.toList());
            stats.put("topEarners", topEarners);
        } else {
            stats.put("payrollGenerated", false);
            stats.put("payrollCount", 0);
            stats.put("totalPayrollAmount", 0);
            stats.put("topEarners", List.of());
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
        
        // Clear missing punch flag if manual OUT was set
        if (manualOut != null && !manualOut.isBlank()) {
            day.setMissingPunch(false);
            day.setMissingPunchType(null);
        }
        
        // Recalculate late/early with shift rules and rounding if both IN and OUT are now available
        LocalDateTime effectiveIn = day.getManualIn() != null ? day.getManualIn() : day.getFirstIn();
        LocalDateTime effectiveOut = day.getManualOut() != null ? day.getManualOut() : day.getLastOut();
        
        if (effectiveIn != null && effectiveOut != null && day.getShiftCodes() != null && !day.getShiftCodes().isBlank()) {
            // Get shift information
            String primaryShiftCode = day.getShiftCodes().split(",")[0];
            String tenantId = day.getTenantId();
            Optional<com.example.hrms.domain.Shift> shiftOpt = (tenantId != null 
                ? shiftRepository.findByTenantIdAndCode(tenantId, primaryShiftCode)
                : shiftRepository.findByTenantIdAndCode("ORG001", primaryShiftCode));
            
            if (shiftOpt.isPresent()) {
                com.example.hrms.domain.Shift shift = shiftOpt.get();
                LocalTime shiftStart = shift.getStartTime();
                LocalTime shiftEnd = shift.getEndTime();
                LocalDate workDate = day.getWorkDate();
                boolean crossedMidnight = Boolean.TRUE.equals(shift.getCrossesMidnight());
                
                // Apply rounding rules if configured
                RoundingRule roundingRule = shift.getRounding() != null ? shift.getRounding() : RoundingRule.NONE;
                int roundingMins = getRoundingMinutes(roundingRule);
                int graceInMins = shift.getGraceInMins() != null ? shift.getGraceInMins() : 0;
                int graceOutMins = shift.getGraceOutMins() != null ? shift.getGraceOutMins() : 0;
                
                // Calculate rounded IN time (for late calculation)
                LocalDateTime roundedIn = effectiveIn;
                if (roundingMins > 0) {
                    LocalTime inTime = effectiveIn.toLocalTime();
                    int minuteOfDay = inTime.getHour() * 60 + inTime.getMinute();
                    int shiftStartMinute = shiftStart.getHour() * 60 + shiftStart.getMinute();
                    
                    // Round UP for IN time (favor employee)
                    if (minuteOfDay > shiftStartMinute) {
                        int roundedMinute = ((minuteOfDay + roundingMins - 1) / roundingMins) * roundingMins;
                        roundedIn = LocalDateTime.of(workDate, LocalTime.of(roundedMinute / 60, roundedMinute % 60));
                    } else {
                        roundedIn = LocalDateTime.of(workDate, shiftStart);
                    }
                }
                
                // Calculate late IN based on rounded time
                LocalTime roundedInTime = roundedIn.toLocalTime();
                if (roundedInTime.isAfter(shiftStart.plusMinutes(graceInMins))) {
                    day.setIsLateIn(true);
                    long lateMins = java.time.Duration.between(
                        LocalDateTime.of(workDate, shiftStart.plusMinutes(graceInMins)),
                        roundedIn
                    ).toMinutes();
                    day.setLateByMins((int) lateMins);
                    day.setRoundedIn(roundedIn);
                } else {
                    day.setIsLateIn(false);
                    day.setLateByMins(0);
                    day.setRoundedIn(LocalDateTime.of(workDate, shiftStart));
                }
                
                // Calculate rounded OUT time (for early calculation)
                LocalDateTime roundedOut = effectiveOut;
                LocalDateTime expectedEnd = crossedMidnight 
                    ? LocalDateTime.of(workDate.plusDays(1), shiftEnd)
                    : LocalDateTime.of(workDate, shiftEnd);
                
                if (roundingMins > 0) {
                    LocalTime outTime = effectiveOut.toLocalTime();
                    LocalDate outDate = effectiveOut.toLocalDate();
                    int minuteOfDay = outTime.getHour() * 60 + outTime.getMinute();
                    int shiftEndMinute = shiftEnd.getHour() * 60 + shiftEnd.getMinute();
                    
                    // Round DOWN for OUT time (penalize early departure)
                    if (minuteOfDay < shiftEndMinute) {
                        int roundedMinute = (minuteOfDay / roundingMins) * roundingMins;
                        roundedOut = LocalDateTime.of(outDate, LocalTime.of(roundedMinute / 60, roundedMinute % 60));
                    } else {
                        roundedOut = expectedEnd;
                    }
                }
                
                // Calculate early OUT based on rounded time vs shift end (with grace period)
                LocalTime roundedOutTime = roundedOut.toLocalTime();
                LocalTime shiftEndWithGrace = shiftEnd.minusMinutes(graceOutMins);
                LocalDateTime expectedEndWithGrace = crossedMidnight 
                    ? LocalDateTime.of(workDate.plusDays(1), shiftEndWithGrace)
                    : LocalDateTime.of(workDate, shiftEndWithGrace);
                
                if (roundedOut.isBefore(expectedEndWithGrace)) {
                    day.setIsEarlyOut(true);
                    long earlyMins = java.time.Duration.between(
                        roundedOut,
                        expectedEndWithGrace
                    ).toMinutes();
                    day.setEarlyByMins((int) earlyMins);
                    day.setRoundedOut(roundedOut);
                } else {
                    day.setIsEarlyOut(false);
                    day.setEarlyByMins(0);
                    day.setRoundedOut(roundedOut.isAfter(expectedEnd) ? roundedOut : expectedEnd);
                }
                
                // Adjust work time: deduct late + early minutes from total work time
                // This ensures work time reflects actual productive time
                if (day.getTotalWorkMin() != null && day.getTotalWorkMin() > 0) {
                    int adjustedWorkMins = day.getTotalWorkMin();
                    if (day.getLateByMins() != null && day.getLateByMins() > 0) {
                        adjustedWorkMins -= day.getLateByMins();
                    }
                    if (day.getEarlyByMins() != null && day.getEarlyByMins() > 0) {
                        adjustedWorkMins -= day.getEarlyByMins();
                    }
                    // Don't let it go below 0
                    if (adjustedWorkMins < 0) {
                        adjustedWorkMins = 0;
                    }
                    // Update work minutes with adjusted value
                    day.setTotalWorkMin(adjustedWorkMins);
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
        result.put("lateByMins", day.getLateByMins() != null ? day.getLateByMins() : 0);
        result.put("earlyByMins", day.getEarlyByMins() != null ? day.getEarlyByMins() : 0);
        result.put("isLateIn", Boolean.TRUE.equals(day.getIsLateIn()));
        result.put("isEarlyOut", Boolean.TRUE.equals(day.getIsEarlyOut()));
        if (day.getRoundedIn() != null) {
            result.put("roundedIn", day.getRoundedIn().toLocalTime().format(TIME_FMT));
        }
        if (day.getRoundedOut() != null) {
            result.put("roundedOut", day.getRoundedOut().toLocalTime().format(TIME_FMT));
        }
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
