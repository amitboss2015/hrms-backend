package com.example.hrms.attendance.service.impl;

import com.example.hrms.attendance.dto.DailyPunchLogDTO;
import com.example.hrms.attendance.dto.SummaryRowDTO;
import com.example.hrms.attendance.domain.AttendanceDay;
import com.example.hrms.attendance.repo.*;
import com.example.hrms.attendance.service.AttendanceQueryService;
import com.example.hrms.attendance.domain.AttendancePunch;
import com.example.hrms.domain.Employee;
import com.example.hrms.leave.domain.EmployeeLeave;
import com.example.hrms.leave.domain.enums.LeaveStatus;
import com.example.hrms.leave.repo.EmployeeLeaveRepository;
import com.example.hrms.repo.EmployeeRepository;
import com.example.hrms.repo.ShiftRepository;
import com.example.hrms.tenant.TenantContext;
import com.example.hrms.payroll.service.SalaryOvertimeConfigService;
import com.example.hrms.payroll.domain.SalaryOvertimeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceQueryServiceImpl implements AttendanceQueryService {

    private final AttendancePunchRepository punchRepo;
    private final AttendanceDayRepository dayRepo;
    private final EmployeeRepository employeeRepo;
    private final ShiftRepository shiftRepo;
    private final SalaryOvertimeConfigService configService;
    private final EmployeeLeaveRepository leaveRepo;
    
    // Expose leaveRepo for controller access
    public EmployeeLeaveRepository getLeaveRepo() {
        return leaveRepo;
    }

    private static final ZoneId ORG_TZ = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Legacy non-tenant-aware version - delegates to tenant-aware version
     */
    @Override
    public List<DailyPunchLogDTO> getEmployeeLogs(Long orgId, Long employeeId, String empCode, int month, int year) {
        return getEmployeeLogs(orgId, null, employeeId, empCode, month, year);
    }
    
    /**
     * Tenant-aware version - filters employees by tenant
     */
    @Override
    public List<DailyPunchLogDTO> getEmployeeLogs(Long orgId, String tenantId, Long employeeId, String empCode, int month, int year) {
        // Resolve employeeId from empCode if needed
        Long empId = employeeId;
        if (empId == null && empCode != null && !empCode.isBlank()) {
            // Use tenant-aware lookup if tenantId provided
            empId = (tenantId != null 
                    ? employeeRepo.findByTenantIdAndEmpCode(tenantId, empCode)
                    : employeeRepo.findByEmpCode(empCode))
                    .map(Employee::getId)
                    .orElse(null);
        }
        
        if (empId == null) {
            return List.of();
        }

        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        Instant start = from.atStartOfDay(ORG_TZ).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ORG_TZ).toInstant();

        // Get punch records
        List<AttendancePunch> punches =
                punchRepo.findByEmployeeIdAndPunchTsUtcBetweenOrderByPunchTsUtcAsc(empId, start, end);

        // Remove duplicate punches (same employee, same timestamp)
        punches = removeDuplicatePunches(punches);

        // Get computed day records
        List<AttendanceDay> days = dayRepo.findByEmployeeIdAndWorkDateBetween(empId, from, to);
        Map<LocalDate, AttendanceDay> dayMap = days.stream()
                .collect(Collectors.toMap(AttendanceDay::getWorkDate, d -> d));

        // Group punches by WORK date (not calendar date)
        // Work date runs from 6 AM to 6 AM next day
        // Punches before 6 AM belong to the previous work day
        Map<LocalDate, List<AttendancePunch>> punchMap = new TreeMap<>();
        for (AttendancePunch p : punches) {
            LocalDateTime punchLocal = p.getPunchTsUtc().atZone(ORG_TZ).toLocalDateTime();
            LocalDate workDate = punchLocal.toLocalDate();
            int hour = punchLocal.getHour();
            
            // If punch is before 6 AM, it belongs to the previous work day
            if (hour < 6) {
                workDate = workDate.minusDays(1);
            }
            
            punchMap.computeIfAbsent(workDate, k -> new ArrayList<>()).add(p);
        }

        // If no punch records and no day records exist for this period, return empty list
        // This prevents showing all days as "ABSENT" when no data has been imported
        if (punches.isEmpty() && days.isEmpty()) {
            return List.of();
        }

        // Build DTOs for each day
        List<DailyPunchLogDTO> result = new ArrayList<>();
        
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<AttendancePunch> dayPunches = new ArrayList<>(punchMap.getOrDefault(date, List.of()));
            AttendanceDay dayRecord = dayMap.get(date);

            DailyPunchLogDTO.DailyPunchLogDTOBuilder builder = DailyPunchLogDTO.builder()
                    .date(DATE_FMT.format(date));

            if (!dayPunches.isEmpty()) {
                // Sort punches by UTC timestamp to ensure correct IN/OUT pairing
                dayPunches.sort(Comparator.comparing(AttendancePunch::getPunchTsUtc));
                
                // Format punch times - calculate IN/OUT based on index (even=IN, odd=OUT)
                // Don't use punchTypeHint as it may be incorrectly set during import
                List<String> punchStrings = new ArrayList<>();
                for (int i = 0; i < dayPunches.size(); i++) {
                    AttendancePunch p = dayPunches.get(i);
                    String time = p.getPunchTsUtc().atZone(ORG_TZ).toLocalTime().format(TIME_FMT);
                    String type = (i % 2 == 0) ? "IN" : "OUT";
                    punchStrings.add(time + " " + type);
                }
                builder.punches(punchStrings);
                builder.punchCount(dayPunches.size());

                // First IN and Last OUT
                LocalTime firstIn = dayPunches.get(0).getPunchTsUtc().atZone(ORG_TZ).toLocalTime();
                builder.firstIn(firstIn.format(TIME_FMT));
                
                // Only set lastOut if there are at least 2 punches
                if (dayPunches.size() >= 2) {
                    LocalTime lastOut = dayPunches.get(dayPunches.size() - 1).getPunchTsUtc().atZone(ORG_TZ).toLocalTime();
                    builder.lastOut(lastOut.format(TIME_FMT));
                } else {
                    // Single punch - OUT is missing
                    builder.lastOut(null);
                }
            } else {
                builder.punches(List.of());
                builder.punchCount(0);
            }

            // Use computed day data if available
            if (dayRecord != null) {
                builder.dayId(dayRecord.getId());
                builder.workMinutes(dayRecord.getTotalWorkMin() != null ? dayRecord.getTotalWorkMin() : 0);
                
                // Missing punch and review fields
                boolean hasMissingPunch = Boolean.TRUE.equals(dayRecord.getMissingPunch());
                String missingPunchType = dayRecord.getMissingPunchType();
                
                // If OUT punch is missing but IN exists, status should be PRESENT (not ABSENT)
                String status = dayRecord.getStatus() != null ? dayRecord.getStatus() : "ABSENT";
                if (hasMissingPunch && "OUT".equals(missingPunchType) && dayRecord.getFirstIn() != null) {
                    // Employee came to work but missed OUT punch - show as PRESENT with missing punch indicator
                    status = "PRESENT";
                }
                
                builder.status(status);
                builder.dualShift(Boolean.TRUE.equals(dayRecord.getDualShift()));
                builder.crossedMidnight(Boolean.TRUE.equals(dayRecord.getCrossedMidnight()));
                
                builder.missingPunch(hasMissingPunch);
                builder.missingPunchType(missingPunchType);
                builder.needsReview(Boolean.TRUE.equals(dayRecord.getNeedsReview()));
                builder.remarks(dayRecord.getRemarks());
                
                // Manual override values
                if (dayRecord.getManualIn() != null) {
                    builder.manualIn(dayRecord.getManualIn().toLocalTime().format(TIME_FMT));
                }
                if (dayRecord.getManualOut() != null) {
                    builder.manualOut(dayRecord.getManualOut().toLocalTime().format(TIME_FMT));
                }
                
                // Weekly off and holiday fields
                builder.isWeeklyOff(Boolean.TRUE.equals(dayRecord.getIsWeeklyOff()));
                builder.isHoliday(Boolean.TRUE.equals(dayRecord.getIsHoliday()));
                builder.holidayName(dayRecord.getHolidayName());
                builder.isOvertimeDay(Boolean.TRUE.equals(dayRecord.getIsOvertimeDay()));
                builder.overtimeOnHolidayMins(dayRecord.getOvertimeOnHolidayMins() != null ? dayRecord.getOvertimeOnHolidayMins() : 0);
                
                // Parse shift codes
                if (dayRecord.getShiftCodes() != null && !dayRecord.getShiftCodes().isBlank()) {
                    String[] shifts = dayRecord.getShiftCodes().split(",");
                    builder.shifts(Arrays.asList(shifts));
                    builder.shiftCode(shifts[0]);
                }
                
                // Override firstIn/lastOut from computed data if available
                if (dayRecord.getFirstIn() != null) {
                    builder.firstIn(dayRecord.getFirstIn().toLocalTime().format(TIME_FMT));
                }
                if (dayRecord.getLastOut() != null) {
                    builder.lastOut(dayRecord.getLastOut().toLocalTime().format(TIME_FMT));
                }
                
                // Late/Early tracking with rounding
                builder.lateIn(Boolean.TRUE.equals(dayRecord.getIsLateIn()));
                builder.earlyOut(Boolean.TRUE.equals(dayRecord.getIsEarlyOut()));
                builder.lateByMins(dayRecord.getLateByMins() != null ? dayRecord.getLateByMins() : 0);
                builder.earlyByMins(dayRecord.getEarlyByMins() != null ? dayRecord.getEarlyByMins() : 0);
                
                // Late/Early approval status
                builder.lateApproved(Boolean.TRUE.equals(dayRecord.getLateApproved()));
                builder.earlyOutApproved(Boolean.TRUE.equals(dayRecord.getEarlyOutApproved()));
                builder.approvedBy(dayRecord.getApprovedBy());
                if (dayRecord.getApprovedAt() != null) {
                    builder.approvedAt(dayRecord.getApprovedAt().toString());
                }
                builder.approvalRemarks(dayRecord.getApprovalRemarks());
                
                if (dayRecord.getRoundedIn() != null) {
                    builder.roundedIn(dayRecord.getRoundedIn().toLocalTime().format(TIME_FMT));
                }
                if (dayRecord.getRoundedOut() != null) {
                    builder.roundedOut(dayRecord.getRoundedOut().toLocalTime().format(TIME_FMT));
                }
                
                // Get shift timings for reference (tenant-aware lookup)
                if (dayRecord.getShiftCodes() != null && !dayRecord.getShiftCodes().isBlank()) {
                    String primaryShiftCode = dayRecord.getShiftCodes().split(",")[0];
                    String currentTenantId = tenantId != null ? tenantId : TenantContext.getTenantId();
                    (currentTenantId != null 
                        ? shiftRepo.findByTenantIdAndCode(currentTenantId, primaryShiftCode)
                        : shiftRepo.findByTenantIdAndCode("ORG001", primaryShiftCode))
                        .ifPresent(shift -> {
                            builder.shiftStartTime(shift.getStartTime().format(TIME_FMT));
                            builder.shiftEndTime(shift.getEndTime().format(TIME_FMT));
                        });
                }
                
                // Update highlight reason to include late/early and approval status
                String hlReason = null;
                boolean lateApproved = Boolean.TRUE.equals(dayRecord.getLateApproved());
                boolean earlyApproved = Boolean.TRUE.equals(dayRecord.getEarlyOutApproved());
                
                if (Boolean.TRUE.equals(dayRecord.getIsOvertimeDay())) {
                    hlReason = "OT on " + (Boolean.TRUE.equals(dayRecord.getIsHoliday()) ? "Holiday" : "Weekly Off");
                } else if (Boolean.TRUE.equals(dayRecord.getIsLateIn()) && Boolean.TRUE.equals(dayRecord.getIsEarlyOut())) {
                    String lateText = lateApproved ? "Late ✓" : "Late IN +" + dayRecord.getLateByMins() + "m";
                    String earlyText = earlyApproved ? "Early ✓" : "Early OUT +" + dayRecord.getEarlyByMins() + "m";
                    hlReason = lateText + " & " + earlyText;
                } else if (Boolean.TRUE.equals(dayRecord.getIsLateIn())) {
                    hlReason = lateApproved 
                        ? "Late ✓ Approved" 
                        : "Late IN +" + dayRecord.getLateByMins() + "min";
                } else if (Boolean.TRUE.equals(dayRecord.getIsEarlyOut())) {
                    hlReason = earlyApproved 
                        ? "Early ✓ Approved" 
                        : "Early OUT +" + dayRecord.getEarlyByMins() + "min";
                } else if (Boolean.TRUE.equals(dayRecord.getMissingPunch())) {
                    hlReason = "Missing " + (dayRecord.getMissingPunchType() != null ? dayRecord.getMissingPunchType() : "punch");
                } else if (Boolean.TRUE.equals(dayRecord.getDualShift())) {
                    hlReason = "Dual Shift";
                } else if (Boolean.TRUE.equals(dayRecord.getIsWeeklyOff())) {
                    hlReason = "Weekly Off";
                } else if (Boolean.TRUE.equals(dayRecord.getIsHoliday())) {
                    hlReason = "Holiday: " + dayRecord.getHolidayName();
                }
                builder.highlightReason(hlReason);
                
                // Calculate OT/Late/Early deduction according to salary/OT rules
                if (empId != null) {
                    // earlyApproved is already defined above (line 241)
                    calculateDeductions(builder, dayRecord, empId, tenantId, lateApproved, earlyApproved);
                } else {
                    builder.otDeductionMins(0);
                    builder.lateDeductionMins(0);
                    builder.earlyDeductionMins(0);
                }
            } else if (dayPunches.isEmpty()) {
                builder.status("ABSENT");
                builder.workMinutes(0);
                builder.otDeductionMins(0);
                builder.lateDeductionMins(0);
                builder.earlyDeductionMins(0);
            } else {
                builder.status("PRESENT");
                // Check for odd number of punches (missing punch)
                if (dayPunches.size() % 2 != 0) {
                    builder.missingPunch(true);
                    builder.missingPunchType("OUT");
                    builder.needsReview(true);
                    builder.highlightReason("Missing OUT");
                }
                builder.otDeductionMins(0);
                builder.lateDeductionMins(0);
                builder.earlyDeductionMins(0);
            }

            result.add(builder.build());
        }

        return result;
    }
    
    /**
     * Calculate OT, Late, and Early deduction for a day according to salary/OT rules.
     * Uses shift IN/OUT times for calculation.
     * If approved, deduction is 0.
     * Early deduction respects grace period (graceOutMins).
     */
    private void calculateDeductions(DailyPunchLogDTO.DailyPunchLogDTOBuilder builder, 
                                      AttendanceDay dayRecord, Long empId, String tenantId, 
                                      boolean lateApproved, boolean earlyApproved) {
        // Get employee and config
        Optional<Employee> empOpt = employeeRepo.findById(empId);
        if (empOpt.isEmpty()) {
            builder.otDeductionMins(0);
            builder.lateDeductionMins(0);
            builder.earlyDeductionMins(0);
            return;
        }
        
        Employee emp = empOpt.get();
        SalaryOvertimeConfig config = configService.getConfig();
        
        // Calculate Late Deduction - based on shift IN time (respects grace period)
        int lateDeductionMins = 0;
        if (!lateApproved && dayRecord.getLateByMins() != null && dayRecord.getLateByMins() > 0) {
            // Late deduction = late minutes (already calculated based on shift IN time with rounding and grace period)
            lateDeductionMins = dayRecord.getLateByMins();
        }
        builder.lateDeductionMins(lateDeductionMins);
        
        // Calculate Early Checkout Deduction - based on shift OUT time (respects grace period)
        int earlyDeductionMins = 0;
        if (!earlyApproved && dayRecord.getEarlyByMins() != null && dayRecord.getEarlyByMins() > 0) {
            // Early deduction = early minutes (already calculated based on shift OUT time with rounding and grace period)
            // The earlyByMins already accounts for graceOutMins, so we use it directly
            earlyDeductionMins = dayRecord.getEarlyByMins();
        }
        builder.earlyDeductionMins(earlyDeductionMins);
        
        // Calculate OT Deduction - based on shift OUT time
        int otDeductionMins = 0;
        String status = dayRecord.getStatus() != null ? dayRecord.getStatus().toUpperCase() : "";
        
        // Only calculate OT for PRESENT days
        if ("PRESENT".equals(status) && emp.isOtAllowed() 
            && config.getOvertimeEnabled() != null && config.getOvertimeEnabled()) {
            
            // Get shift information from day record
            String shiftCodes = dayRecord.getShiftCodes();
            if (shiftCodes == null || shiftCodes.isBlank()) {
                // No shift assigned - cannot calculate OT/Late deduction
                builder.otDeductionMins(0);
                return;
            }
            
            // Get primary shift
            String primaryShiftCode = shiftCodes.split(",")[0];
            String currentTenantId = tenantId != null ? tenantId : TenantContext.getTenantId();
            Optional<com.example.hrms.domain.Shift> shiftOpt = (currentTenantId != null 
                ? shiftRepo.findByTenantIdAndCode(currentTenantId, primaryShiftCode)
                : shiftRepo.findByTenantIdAndCode("ORG001", primaryShiftCode));
            
            if (shiftOpt.isEmpty()) {
                // Shift not found - cannot calculate OT
                builder.otDeductionMins(0);
                return;
            }
            
            com.example.hrms.domain.Shift shift = shiftOpt.get();
            LocalTime shiftEnd = shift.getEndTime();
            
            // Check if worked on weekly off or holiday (full day OT)
            boolean isOvertimeDay = Boolean.TRUE.equals(dayRecord.getIsOvertimeDay());
            
            if (isOvertimeDay) {
                // OT on weekly off/holiday - count all work minutes as OT
                int workMins = dayRecord.getTotalWorkMin() != null ? dayRecord.getTotalWorkMin() : 0;
                otDeductionMins = workMins; // Positive = OT earned
            } else {
                // Regular working day - calculate OT based on shift OUT time vs actual OUT time
                LocalDateTime lastOut = dayRecord.getLastOut();
                LocalDateTime roundedOut = dayRecord.getRoundedOut();
                
                if (lastOut == null) {
                    // No OUT time - cannot calculate OT
                    builder.otDeductionMins(0);
                    return;
                }
                
                // Use rounded OUT time if available, otherwise use actual OUT time
                LocalDateTime effectiveOut = (roundedOut != null) ? roundedOut : lastOut;
                LocalDate workDate = dayRecord.getWorkDate();
                
                // Calculate expected OUT time (shift end)
                // Handle cross-midnight shifts
                boolean crossedMidnight = Boolean.TRUE.equals(dayRecord.getCrossedMidnight());
                LocalDateTime expectedOut = crossedMidnight 
                    ? LocalDateTime.of(workDate.plusDays(1), shiftEnd)
                    : LocalDateTime.of(workDate, shiftEnd);
                
                // Calculate OT minutes: actual OUT time - shift OUT time
                long otMinutes = java.time.Duration.between(expectedOut, effectiveOut).toMinutes();
                
                // Apply OT threshold
                int otMinThreshold = config.getOvertimeMinThresholdMins();
                
                if (otMinutes >= otMinThreshold) {
                    // Worked beyond shift end - OT earned (positive)
                    otDeductionMins = (int) otMinutes;
                } else if (otMinutes < 0) {
                    // Left before shift end - early departure (negative OT)
                    // This is already handled by earlyByMins, but we can show it as negative OT deduction
                    otDeductionMins = (int) otMinutes;
                } else {
                    // Within threshold - no OT
                    otDeductionMins = 0;
                }
            }
        }
        builder.otDeductionMins(otDeductionMins);
    }

    @Override
    public List<SummaryRowDTO> getMonthlySummary(Long orgId, int month, int year) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        List<AttendanceDay> days = dayRepo.findByWorkDateBetween(from, to);

        Map<Long, List<AttendanceDay>> byEmp = days.stream()
                .collect(Collectors.groupingBy(AttendanceDay::getEmployeeId));
        
        // Get employee details
        List<Employee> employees = employeeRepo.findAllById(byEmp.keySet());
        Map<Long, Employee> empMap = employees.stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        List<SummaryRowDTO> out = new ArrayList<>();
        byEmp.forEach((empId, list) -> {
            int present = (int) list.stream().filter(d -> "PRESENT".equals(d.getStatus())).count();
            int absent = (int) list.stream().filter(d -> "ABSENT".equals(d.getStatus())).count();
            int leave = (int) list.stream().filter(d -> "LEAVE".equals(d.getStatus())).count();
            
            Employee emp = empMap.get(empId);
            String code = emp != null && emp.getEmpCode() != null ? emp.getEmpCode() : String.valueOf(empId);
            String name = emp != null ? safeName(emp) : "Employee " + empId;
            
            out.add(SummaryRowDTO.builder()
                    .empCode(code)
                    .empName(name)
                    .present(present)
                    .absent(absent)
                    .leaveDays(leave)
                    .build());
        });
        return out;
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
     * Remove duplicate punches that have the same timestamp.
     * This handles cases where punches were accidentally imported twice.
     */
    private List<AttendancePunch> removeDuplicatePunches(List<AttendancePunch> punches) {
        if (punches == null || punches.isEmpty()) return punches;
        
        Set<String> seen = new HashSet<>();
        List<AttendancePunch> unique = new ArrayList<>();
        
        for (AttendancePunch p : punches) {
            // Create a unique key based on employee ID and timestamp
            String key = p.getEmployeeId() + "_" + p.getPunchTsUtc().toEpochMilli();
            if (!seen.contains(key)) {
                seen.add(key);
                unique.add(p);
            }
        }
        
        return unique;
    }
}
