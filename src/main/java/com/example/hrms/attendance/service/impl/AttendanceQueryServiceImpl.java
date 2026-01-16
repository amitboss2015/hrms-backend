package com.example.hrms.attendance.service.impl;

import com.example.hrms.attendance.dto.DailyPunchLogDTO;
import com.example.hrms.attendance.dto.SummaryRowDTO;
import com.example.hrms.attendance.domain.AttendanceDay;
import com.example.hrms.attendance.repo.*;
import com.example.hrms.attendance.service.AttendanceQueryService;
import com.example.hrms.attendance.domain.AttendancePunch;
import com.example.hrms.domain.Employee;
import com.example.hrms.repo.EmployeeRepository;
import com.example.hrms.repo.ShiftRepository;
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

    private static final ZoneId ORG_TZ = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public List<DailyPunchLogDTO> getEmployeeLogs(Long orgId, Long employeeId, String empCode, int month, int year) {
        // Resolve employeeId from empCode if needed
        Long empId = employeeId;
        if (empId == null && empCode != null && !empCode.isBlank()) {
            empId = employeeRepo.findByEmpCode(empCode)
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
                builder.status(dayRecord.getStatus() != null ? dayRecord.getStatus() : "ABSENT");
                builder.dualShift(Boolean.TRUE.equals(dayRecord.getDualShift()));
                builder.crossedMidnight(Boolean.TRUE.equals(dayRecord.getCrossedMidnight()));
                
                // Missing punch and review fields
                builder.missingPunch(Boolean.TRUE.equals(dayRecord.getMissingPunch()));
                builder.missingPunchType(dayRecord.getMissingPunchType());
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
                builder.isLateIn(Boolean.TRUE.equals(dayRecord.getIsLateIn()));
                builder.isEarlyOut(Boolean.TRUE.equals(dayRecord.getIsEarlyOut()));
                builder.lateByMins(dayRecord.getLateByMins() != null ? dayRecord.getLateByMins() : 0);
                builder.earlyByMins(dayRecord.getEarlyByMins() != null ? dayRecord.getEarlyByMins() : 0);
                
                if (dayRecord.getRoundedIn() != null) {
                    builder.roundedIn(dayRecord.getRoundedIn().toLocalTime().format(TIME_FMT));
                }
                if (dayRecord.getRoundedOut() != null) {
                    builder.roundedOut(dayRecord.getRoundedOut().toLocalTime().format(TIME_FMT));
                }
                
                // Get shift timings for reference
                if (dayRecord.getShiftCodes() != null && !dayRecord.getShiftCodes().isBlank()) {
                    String primaryShiftCode = dayRecord.getShiftCodes().split(",")[0];
                    shiftRepo.findByCode(primaryShiftCode).ifPresent(shift -> {
                        builder.shiftStartTime(shift.getStartTime().format(TIME_FMT));
                        builder.shiftEndTime(shift.getEndTime().format(TIME_FMT));
                    });
                }
                
                // Update highlight reason to include late/early
                String hlReason = null;
                if (Boolean.TRUE.equals(dayRecord.getIsOvertimeDay())) {
                    hlReason = "OT on " + (Boolean.TRUE.equals(dayRecord.getIsHoliday()) ? "Holiday" : "Weekly Off");
                } else if (Boolean.TRUE.equals(dayRecord.getIsLateIn()) && Boolean.TRUE.equals(dayRecord.getIsEarlyOut())) {
                    hlReason = "Late IN +" + dayRecord.getLateByMins() + "m & Early OUT +" + dayRecord.getEarlyByMins() + "m";
                } else if (Boolean.TRUE.equals(dayRecord.getIsLateIn())) {
                    hlReason = "Late IN +" + dayRecord.getLateByMins() + "min";
                } else if (Boolean.TRUE.equals(dayRecord.getIsEarlyOut())) {
                    hlReason = "Early OUT +" + dayRecord.getEarlyByMins() + "min";
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
            } else if (dayPunches.isEmpty()) {
                builder.status("ABSENT");
                builder.workMinutes(0);
            } else {
                builder.status("PRESENT");
                // Check for odd number of punches (missing punch)
                if (dayPunches.size() % 2 != 0) {
                    builder.missingPunch(true);
                    builder.missingPunchType("OUT");
                    builder.needsReview(true);
                    builder.highlightReason("Missing OUT");
                }
            }

            result.add(builder.build());
        }

        return result;
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
