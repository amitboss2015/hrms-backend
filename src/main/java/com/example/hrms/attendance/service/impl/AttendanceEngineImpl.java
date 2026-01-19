package com.example.hrms.attendance.service.impl;

import com.example.hrms.attendance.domain.AttendanceDay;
import com.example.hrms.attendance.domain.AttendanceSession;
import com.example.hrms.attendance.domain.OvertimeAllowance;
import com.example.hrms.attendance.repo.AttendanceDayRepository;
import com.example.hrms.attendance.repo.AttendancePunchRepository;
import com.example.hrms.attendance.repo.AttendanceSessionRepository;
import com.example.hrms.attendance.repo.OvertimeAllowanceRepository;
import com.example.hrms.attendance.service.AttendanceEngine;

import com.example.hrms.attendance.domain.AttendancePunch;

import com.example.hrms.domain.Employee;
import com.example.hrms.domain.EmployeeShiftAssignment;
import com.example.hrms.domain.Holiday;
import com.example.hrms.domain.Shift;
import com.example.hrms.domain.WeeklyOffConfig;
import com.example.hrms.domain.enums.EmploymentType;
import com.example.hrms.domain.enums.PatternType;
import com.example.hrms.domain.enums.RoundingRule;
import com.example.hrms.repo.EmployeeRepository;
import com.example.hrms.repo.EmployeeShiftAssignmentRepository;
import com.example.hrms.repo.HolidayRepository;
import com.example.hrms.repo.WeeklyOffConfigRepository;
import com.example.hrms.payroll.domain.SalaryOvertimeConfig;
import com.example.hrms.payroll.service.SalaryOvertimeConfigService;
import com.example.hrms.leave.repo.EmployeeLeaveRepository;
import com.example.hrms.leave.domain.EmployeeLeave;
import com.example.hrms.leave.domain.enums.LeaveStatus;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceEngineImpl implements AttendanceEngine {

    private final AttendancePunchRepository punchRepo;
    private final AttendanceSessionRepository sessionRepo;
    private final AttendanceDayRepository dayRepo;
    private final OvertimeAllowanceRepository otRepo;
    private final EmployeeShiftAssignmentRepository empShiftRepo;
    private final EmployeeRepository employeeRepo;
    private final WeeklyOffConfigRepository weeklyOffConfigRepo;
    private final HolidayRepository holidayRepo;
    private final SalaryOvertimeConfigService configService;
    private final EmployeeLeaveRepository leaveRepo;

    private static final ZoneId ORG_TZ = ZoneId.of("Asia/Kolkata");
    private static final int BOUNDARY_FLOOR_MIN = 120; // at least 2h cross-midnight fetch
    private static final Duration SEGMENT_GAP = Duration.ofMinutes(60); // merge punches up to 60m apart
    private static final Duration MIN_SESSION = Duration.ofMinutes(5); // ignore <5m slices

    @Override
    @Transactional
    public void rebuildEmployeeMonth(Long orgId, Long employeeId, YearMonth ym) {
        LocalDate d = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        while (!d.isAfter(end)) {
            rebuildEmployeeDate(orgId, employeeId, d);
            d = d.plusDays(1);
        }
    }

    @Override
    public void rebuildOrgMonth(Long orgId, YearMonth ym) {
        // iterate employees and call rebuildEmployeeMonth(orgId, empId, ym) if needed
    }

    /**
     * OPTIMIZED: Batch rebuild for multiple employees at once.
     * Pre-fetches all required data to minimize database round-trips.
     * 
     * Performance improvements:
     * 1. Batch fetch all punches for all employees in one query
     * 2. Batch fetch all employees data
     * 3. Batch fetch all shift assignments
     * 4. Batch delete old sessions/days
     * 5. Batch save new sessions/days
     */
    @Override
    @Transactional
    public void rebuildEmployeesMonthBatch(Long orgId, String tenantId, List<Long> employeeIds, YearMonth ym) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return;
        }
        
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();
        int totalDays = ym.lengthOfMonth();
        
        // Pre-fetch ALL employees in one query
        Map<Long, Employee> employeeMap = new HashMap<>();
        for (Employee e : employeeRepo.findAllById(employeeIds)) {
            employeeMap.put(e.getId(), e);
        }
        
        // Pre-fetch ALL punches for all employees for the entire month in ONE query
        // Window: 6 AM on day 1 to 6 AM on day after end of month
        Instant fromUtc = startDate.atTime(6, 0).atZone(ORG_TZ).toInstant();
        Instant toUtc = endDate.plusDays(1).atTime(6, 0).atZone(ORG_TZ).toInstant();
        
        Map<Long, List<AttendancePunch>> punchesByEmployee = new HashMap<>();
        List<AttendancePunch> allPunches = punchRepo.findByEmployeeIdInAndPunchTsUtcBetweenOrderByEmployeeIdAscPunchTsUtcAsc(
                employeeIds, fromUtc, toUtc);
        
        for (AttendancePunch p : allPunches) {
            punchesByEmployee.computeIfAbsent(p.getEmployeeId(), k -> new ArrayList<>()).add(p);
        }
        
        // Pre-fetch ALL shift assignments for all employees
        Map<Long, List<EmployeeShiftAssignment>> shiftAssignmentsByEmployee = new HashMap<>();
        for (Long empId : employeeIds) {
            Employee stub = new Employee();
            try {
                Method m = findMethod(Employee.class, "setId", Long.class);
                if (m != null) m.invoke(stub, empId);
            } catch (Exception ignore) {}
            
            List<EmployeeShiftAssignment> assignments = empShiftRepo.findByEmployee(stub);
            shiftAssignmentsByEmployee.put(empId, assignments != null ? assignments : List.of());
        }
        
        // Pre-fetch approved leaves for all employees for the month
        Map<String, Set<LocalDate>> leavesByEmpCode = new HashMap<>();
        if (tenantId != null) {
            List<EmployeeLeave> allLeaves = leaveRepo.findByTenantIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                    tenantId, endDate, startDate);
            for (EmployeeLeave leave : allLeaves) {
                if (leave.getStatus() == LeaveStatus.APPROVED) {
                    Set<LocalDate> dates = leavesByEmpCode.computeIfAbsent(leave.getEmpId(), k -> new HashSet<>());
                    LocalDate d = leave.getStartDate();
                    while (!d.isAfter(leave.getEndDate())) {
                        dates.add(d);
                        d = d.plusDays(1);
                    }
                }
            }
        }
        
        // Pre-fetch holidays for the month
        List<Holiday> holidays = holidayRepo.findByOrgIdAndHolidayDateBetweenAndActiveTrue("ORG001", startDate, endDate);
        Set<LocalDate> holidayDates = new HashSet<>();
        Map<LocalDate, String> holidayNames = new HashMap<>();
        for (Holiday h : holidays) {
            holidayDates.add(h.getHolidayDate());
            holidayNames.put(h.getHolidayDate(), h.getName());
        }
        
        // Batch delete old sessions and days for all employees
        sessionRepo.deleteByEmployeeIdInAndWorkDateBetween(employeeIds, startDate, endDate);
        dayRepo.deleteByEmployeeIdInAndWorkDateBetween(employeeIds, startDate, endDate);
        
        // Process each employee and build sessions/days in memory
        List<AttendanceSession> allSessions = new ArrayList<>();
        List<AttendanceDay> allDays = new ArrayList<>();
        
        SalaryOvertimeConfig config = configService.getConfig();
        int halfDayThresholdMins = config.getHalfDayMinHours() * 60;
        
        for (Long employeeId : employeeIds) {
            Employee employee = employeeMap.get(employeeId);
            if (employee == null) continue;
            
            String empTenantId = employee.getTenantId();
            String empCode = employee.getEmpCode();
            Set<LocalDate> empLeaveDates = leavesByEmpCode.getOrDefault(empCode, Collections.emptySet());
            List<AttendancePunch> empPunches = punchesByEmployee.getOrDefault(employeeId, Collections.emptyList());
            List<EmployeeShiftAssignment> empAssignments = shiftAssignmentsByEmployee.getOrDefault(employeeId, Collections.emptyList());
            
            // Group punches by work date (6 AM to 6 AM window)
            Map<LocalDate, List<AttendancePunch>> punchesByDate = new LinkedHashMap<>();
            for (AttendancePunch p : empPunches) {
                LocalDateTime punchLocal = p.getPunchTsUtc().atZone(ORG_TZ).toLocalDateTime();
                LocalDate workDate;
                // If punch is before 6 AM, it belongs to previous day
                if (punchLocal.getHour() < 6) {
                    workDate = punchLocal.toLocalDate().minusDays(1);
                } else {
                    workDate = punchLocal.toLocalDate();
                }
                // Only include if within our month
                if (!workDate.isBefore(startDate) && !workDate.isAfter(endDate)) {
                    punchesByDate.computeIfAbsent(workDate, k -> new ArrayList<>()).add(p);
                }
            }
            
            // Process each day
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                List<AttendancePunch> dayPunches = punchesByDate.getOrDefault(date, Collections.emptyList());
                
                boolean isHoliday = holidayDates.contains(date);
                String holidayName = holidayNames.get(date);
                boolean isWeeklyOff = isWeeklyOffForEmployee(employee, date);
                boolean isOnLeave = empLeaveDates.contains(date);
                
                if (dayPunches.isEmpty()) {
                    // No punches - create day record based on leave/holiday/weekly off
                    if (isWeeklyOff || isHoliday || isOnLeave) {
                        String status = isOnLeave ? "LEAVE" : (isWeeklyOff ? "WEEKLY_OFF" : "HOLIDAY");
                        allDays.add(AttendanceDay.builder()
                                .tenantId(empTenantId)
                                .orgId(orgId)
                                .employeeId(employeeId)
                                .workDate(date)
                                .totalWorkMin(0)
                                .totalOTEligibleMin(0)
                                .totalOTApprovedMin(0)
                                .punchCount(0)
                                .status(status)
                                .isWeeklyOff(isWeeklyOff)
                                .isHoliday(isHoliday)
                                .holidayName(holidayName)
                                .isOvertimeDay(false)
                                .build());
                    }
                    continue;
                }
                
                // Remove duplicates and sort
                dayPunches = removeDuplicatePunches(dayPunches);
                
                // Calculate work time
                List<Instant> times = dayPunches.stream().map(this::punchInstant).collect(Collectors.toList());
                List<Span> segments = toSegments(times, SEGMENT_GAP);
                
                int dayWorkTotal = 0;
                for (Span seg : segments) {
                    dayWorkTotal += (int) ChronoUnit.MINUTES.between(seg.start, seg.end);
                }
                
                // Apply break deduction if shift is assigned
                Shift primaryShift = getPrimaryShift(empAssignments, date);
                if (primaryShift != null && primaryShift.getBreakMins() != null) {
                    dayWorkTotal = Math.max(0, dayWorkTotal - primaryShift.getBreakMins());
                }
                
                // Determine first IN and last OUT
                LocalDateTime firstInLocal = dayPunches.isEmpty() ? null : 
                        punchInstant(dayPunches.get(0)).atZone(ORG_TZ).toLocalDateTime();
                LocalDateTime lastOutLocal = dayPunches.size() >= 2 ? 
                        punchInstant(dayPunches.get(dayPunches.size() - 1)).atZone(ORG_TZ).toLocalDateTime() : null;
                
                boolean crossedMidnight = lastOutLocal != null && !lastOutLocal.toLocalDate().equals(date);
                boolean missingPunch = dayPunches.size() % 2 != 0;
                String missingPunchType = null;
                if (missingPunch && firstInLocal != null) {
                    missingPunchType = (firstInLocal.getHour() >= 6 && firstInLocal.getHour() < 14) ? "OUT" : "IN";
                }
                
                // Determine status
                String status;
                boolean isOvertimeDay = (isWeeklyOff || isHoliday) && dayWorkTotal > 0;
                if (isOvertimeDay) {
                    status = "OT_DAY";
                } else if (missingPunch && "OUT".equals(missingPunchType)) {
                    status = "PRESENT";
                } else if (dayWorkTotal == 0) {
                    status = isOnLeave ? "LEAVE" : "ABSENT";
                } else if (dayWorkTotal < halfDayThresholdMins) {
                    status = "HALF_DAY";
                } else {
                    status = "PRESENT";
                }
                
                allDays.add(AttendanceDay.builder()
                        .tenantId(empTenantId)
                        .orgId(orgId)
                        .employeeId(employeeId)
                        .workDate(date)
                        .totalWorkMin(dayWorkTotal)
                        .totalOTEligibleMin(0)
                        .totalOTApprovedMin(0)
                        .firstIn(firstInLocal)
                        .lastOut(lastOutLocal)
                        .punchCount(dayPunches.size())
                        .status(status)
                        .missingPunch(missingPunch)
                        .missingPunchType(missingPunchType)
                        .needsReview(missingPunch)
                        .crossedMidnight(crossedMidnight)
                        .isWeeklyOff(isWeeklyOff)
                        .isHoliday(isHoliday)
                        .holidayName(holidayName)
                        .isOvertimeDay(isOvertimeDay)
                        .overtimeOnHolidayMins(isOvertimeDay ? dayWorkTotal : 0)
                        .build());
            }
        }
        
        // Batch save all days at once
        if (!allDays.isEmpty()) {
            dayRepo.saveAll(allDays);
        }
    }
    
    /**
     * Helper: Check if date is a weekly off for the employee
     */
    private boolean isWeeklyOffForEmployee(Employee employee, LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        
        // Check employee-level weekly off
        String empWeeklyOff = employee.getWeeklyOffDays();
        if (empWeeklyOff != null && !empWeeklyOff.isBlank()) {
            return empWeeklyOff.contains(dayOfWeek.name());
        }
        
        // Check org-level weekly off config
        Optional<WeeklyOffConfig> config = weeklyOffConfigRepo.findByOrgIdAndEmploymentTypeAndActiveTrue(
                "ORG001", employee.getEmploymentType() != null ? employee.getEmploymentType() : EmploymentType.FULL_TIME);
        
        if (config.isPresent()) {
            if (config.get().isWeeklyOff(dayOfWeek)) {
                return true;
            }
            
            // Check alternate Saturday rule
            if (dayOfWeek == DayOfWeek.SATURDAY) {
                String satRule = config.get().getAlternateSaturdayRule();
                if ("ALL_SATURDAYS_OFF".equals(satRule)) {
                    return true;
                } else if ("SECOND_AND_FOURTH_OFF".equals(satRule)) {
                    int weekOfMonth = (date.getDayOfMonth() - 1) / 7 + 1;
                    return weekOfMonth == 2 || weekOfMonth == 4;
                } else if ("FIRST_AND_THIRD_OFF".equals(satRule)) {
                    int weekOfMonth = (date.getDayOfMonth() - 1) / 7 + 1;
                    return weekOfMonth == 1 || weekOfMonth == 3;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Helper: Get primary shift for an employee on a given date
     */
    private Shift getPrimaryShift(List<EmployeeShiftAssignment> assignments, LocalDate date) {
        for (EmployeeShiftAssignment a : assignments) {
            LocalDate start = a.getStartDate();
            LocalDate end = a.getEndDate();
            if ((start != null && date.isBefore(start)) || (end != null && date.isAfter(end))) continue;
            if (a.getPatternType() != null && a.getPatternType() != PatternType.NONE) continue;
            
            Shift s = a.getShift();
            if (s != null && appliesToDate(s, date)) {
                return s;
            }
        }
        return null;
    }

    @Transactional
    public void rebuildEmployeeDate(Long orgId, Long employeeId, LocalDate date) {
        // Get tenant ID from employee record for proper multi-tenancy
        String tenantId = employeeRepo.findById(employeeId)
                .map(Employee::getTenantId)
                .orElse(null);
        
        // Figure out how far past midnight we should still attribute to 'date'
        int boundaryMins = Math.max(BOUNDARY_FLOOR_MIN, maxBoundaryAfterMidnight(employeeId, date));

        // Fetch punches for this date: from 6 AM of current date to 6 AM of next date
        // This ensures cross-midnight shifts are captured correctly
        Instant fromUtc = date.atTime(6, 0).atZone(ORG_TZ).toInstant();
        Instant toUtc = date.plusDays(1).atTime(6, 0).atZone(ORG_TZ).toInstant();

        // 1) Pull punches for the window
        List<AttendancePunch> punches =
                punchRepo.findByEmployeeIdAndPunchTsUtcBetweenOrderByPunchTsUtcAsc(employeeId, fromUtc, toUtc);

        // Remove duplicate punches (same timestamp)
        punches = removeDuplicatePunches(punches);
        
        // All punches in this window belong to this date (6 AM to 6 AM next day window)
        // No filtering needed since the window is correctly defined

        // Check if this date is a weekly off or holiday for the employee
        HolidayInfo holidayInfo = getHolidayInfo(orgId, employeeId, date);
        
        if (punches.isEmpty()) {
            // Clear any computed rows for this date
            sessionRepo.deleteAll(sessionRepo.findByEmployeeIdAndWorkDateBetween(employeeId, date, date));
            dayRepo.deleteAll(dayRepo.findByEmployeeIdAndWorkDateBetween(employeeId, date, date));
            
            // If it's a weekly off or holiday, still create an AttendanceDay record
            if (holidayInfo.isWeeklyOff || holidayInfo.isHoliday) {
                AttendanceDay ad = AttendanceDay.builder()
                        .tenantId(tenantId)
                        .orgId(orgId)
                        .employeeId(employeeId)
                        .workDate(date)
                        .totalWorkMin(0)
                        .totalOTEligibleMin(0)
                        .totalOTApprovedMin(0)
                        .punchCount(0)
                        .status(holidayInfo.isWeeklyOff ? "WEEKLY_OFF" : "HOLIDAY")
                        .isWeeklyOff(holidayInfo.isWeeklyOff)
                        .isHoliday(holidayInfo.isHoliday)
                        .holidayName(holidayInfo.holidayName)
                        .isOvertimeDay(false)
                        .build();
                dayRepo.save(ad);
            }
            return;
        }

        // Convert to Instants and build continuous segments
        List<Instant> times = punches.stream().map(this::punchInstant).collect(Collectors.toList());
        List<Span> segments = toSegments(times, SEGMENT_GAP);

        // 2) Determine shift windows (supports two shifts)
        List<ShiftWindow> windows = shiftWindowsFor(employeeId, date);
        
        // Check if we should use all punches directly without shift window clipping
        // This happens when: no shifts assigned OR employee works extended hours
        boolean useFullPunchRange = windows.isEmpty();
        
        if (!useFullPunchRange && !segments.isEmpty()) {
            // Check if any segment extends beyond all shift windows
            // If so, we should use full punch range to capture extended/dual shift work
            for (Span seg : segments) {
                boolean coveredByAnyShift = false;
                for (ShiftWindow w : windows) {
                    if (seg.start.isBefore(w.utcEnd) && seg.end.isAfter(w.utcStart)) {
                        coveredByAnyShift = true;
                        break;
                    }
                }
                // If segment extends significantly beyond shift windows, use full range
                if (coveredByAnyShift) {
                    for (ShiftWindow w : windows) {
                        // Check if segment extends more than 2 hours past shift end
                        if (seg.end.isAfter(w.utcEnd.plus(Duration.ofHours(2)))) {
                            useFullPunchRange = true;
                            break;
                        }
                    }
                }
                if (useFullPunchRange) break;
            }
        }
        
        if (useFullPunchRange) {
            // Use a single window covering the full work period from first punch to last punch
            Instant firstPunch = segments.stream().map(s -> s.start).min(Instant::compareTo).orElse(null);
            Instant lastPunch = segments.stream().map(s -> s.end).max(Instant::compareTo).orElse(null);
            
            if (firstPunch != null && lastPunch != null) {
                // Determine shift code based on time of day
                String shiftCode = "AUTO";
                LocalTime firstPunchTime = firstPunch.atZone(ORG_TZ).toLocalTime();
                if (firstPunchTime.getHour() >= 6 && firstPunchTime.getHour() < 14) {
                    shiftCode = "GENERAL"; // Morning start
                } else if (firstPunchTime.getHour() >= 14) {
                    shiftCode = "EVENING"; // Evening start
                }
                windows = List.of(new ShiftWindow(shiftCode, firstPunch.minus(Duration.ofMinutes(1)), lastPunch.plus(Duration.ofMinutes(1)), null));
            }
        }

        // 3) Delete old sessions for the date and rebuild from segments∩windows
        sessionRepo.deleteAll(sessionRepo.findByEmployeeIdAndWorkDateBetween(employeeId, date, date));
        List<AttendanceSession> sessions = new ArrayList<>();
        Set<String> shiftCodesUsed = new LinkedHashSet<>();

        for (ShiftWindow w : windows) {
            for (Span seg : segments) {
                Span x = intersect(seg, w.utcStart, w.utcEnd);
                if (x == null) continue;
                long mins = ChronoUnit.MINUTES.between(x.start, x.end);
                if (mins < MIN_SESSION.toMinutes()) continue;

                shiftCodesUsed.add(w.shiftCode);

                AttendanceSession s = newSession();
                setIf(s, new String[]{"setOrgId", "orgId"}, orgId);
                setIf(s, new String[]{"setEmployeeId", "employeeId"}, employeeId);
                setIf(s, new String[]{"setWorkDate", "setDate", "workDate"}, date);
                setIf(s, new String[]{"setShiftCode", "setShift", "shiftCode"}, w.shiftCode);
                setIf(s, new String[]{"setInTsUtc", "setInTimeUtc", "setInTime", "setInAt"}, x.start);
                setIf(s, new String[]{"setOutTsUtc", "setOutTimeUtc", "setOutTime", "setOutAt"}, x.end);
                setIf(s, new String[]{"setStatus", "setState", "status"}, "OK");

                int workMin = (int) Math.max(0, mins - (w.shift != null ? nz(w.shift.getBreakMins()) : 0));

                boolean allowed = otRepo
                        .findTopByEmployeeIdAndValidFromLessThanEqualAndValidToGreaterThanEqual(employeeId, date, date)
                        .map(OvertimeAllowance::getAllow).orElse(Boolean.FALSE);

                setIf(s, new String[]{"setWorkMin", "workMin"}, workMin);
                setIf(s, new String[]{"setLateMin", "lateMin"}, 0);
                setIf(s, new String[]{"setEarlyMin", "earlyMin"}, 0);
                setIf(s, new String[]{"setOtEligibleMin", "otEligibleMin"}, allowed ? 0 : 0);
                setIf(s, new String[]{"setOtApprovedMin", "otApprovedMin"}, 0);

                sessions.add(s);
            }
        }

        // small merge: adjacent same-shift slices within 1 minute
        sessions = mergeAdjacentSameShift(sessions, Duration.ofMinutes(1));
        sessionRepo.saveAll(sessions);

        // 4) Roll-up for the day
        int dayWorkTotal = sessions.stream().mapToInt(x -> (Integer) (getIf(x, new String[]{"getWorkMin", "workMin"}) == null ? 0 : (Integer) getIf(x, new String[]{"getWorkMin", "workMin"}))).sum();
        int dayOTEligible = sessions.stream().mapToInt(x -> (Integer) (getIf(x, new String[]{"getOtEligibleMin", "otEligibleMin"}) == null ? 0 : (Integer) getIf(x, new String[]{"getOtEligibleMin", "otEligibleMin"}))).sum();
        int dayOTApproved = sessions.stream().mapToInt(x -> (Integer) (getIf(x, new String[]{"getOtApprovedMin", "otApprovedMin"}) == null ? 0 : (Integer) getIf(x, new String[]{"getOtApprovedMin", "otApprovedMin"}))).sum();

        // Determine first IN and last OUT properly
        // First IN = first punch of the day (should be an IN punch)
        // Last OUT = last punch of the day (should be an OUT punch if even number of punches)
        LocalDateTime firstInLocal = null;
        LocalDateTime lastOutLocal = null;
        
        if (!punches.isEmpty()) {
            // First punch is the IN time
            firstInLocal = punchInstant(punches.get(0)).atZone(ORG_TZ).toLocalDateTime();
            
            // If there are at least 2 punches, last punch is the OUT time
            if (punches.size() >= 2) {
                lastOutLocal = punchInstant(punches.get(punches.size() - 1)).atZone(ORG_TZ).toLocalDateTime();
            } else {
                // Only one punch - OUT is missing, don't set lastOut to same as firstIn
                lastOutLocal = null;
            }
        }

        // Check if crossed midnight (only if we have a valid lastOut)
        boolean crossedMidnight = lastOutLocal != null && !lastOutLocal.toLocalDate().equals(date);

        // Detect missing punches FIRST (before determining status)
        boolean missingPunch = false;
        String missingPunchType = null;
        boolean needsReview = false;
        
        // Odd number of punches indicates missing IN or OUT
        if (punches.size() % 2 != 0) {
            missingPunch = true;
            needsReview = true;
            
            // Determine if it's missing IN or OUT based on first punch time
            if (firstInLocal != null) {
                int firstPunchHour = firstInLocal.getHour();
                if (firstPunchHour >= 6 && firstPunchHour < 14) {
                    // Morning punch - likely IN, so OUT is missing
                    missingPunchType = "OUT";
                } else if (firstPunchHour >= 14 || firstPunchHour < 6) {
                    // Evening/night punch - could be missing IN
                    missingPunchType = "IN";
                }
            } else {
                missingPunchType = "UNKNOWN";
            }
        }

        // Check for dual shift (more than one unique shift code)
        boolean isDualShift = shiftCodesUsed.size() > 1;
        String shiftCodesStr = String.join(",", shiftCodesUsed);
        
        // Also flag for review if it's a dual shift day
        if (isDualShift) {
            needsReview = true;
        }

        // Determine if this is overtime work on a holiday/weekly off
        boolean isOvertimeDay = false;
        int overtimeOnHolidayMins = 0;
        
        if (holidayInfo.isWeeklyOff || holidayInfo.isHoliday) {
            // Employee worked on a day that was supposed to be off
            isOvertimeDay = true;
            overtimeOnHolidayMins = dayWorkTotal;
        }
        
        // ========= Late/Early calculation with Rounding =========
        boolean isLateIn = false;
        boolean isEarlyOut = false;
        int lateByMins = 0;
        int earlyByMins = 0;
        LocalDateTime roundedIn = null;
        LocalDateTime roundedOut = null;
        
        // Get the first assigned shift for late/early calculation
        Shift primaryShift = null;
        if (!windows.isEmpty() && windows.get(0).shift != null) {
            primaryShift = windows.get(0).shift;
        }
        
        if (primaryShift != null && firstInLocal != null && !isOvertimeDay) {
            LocalTime shiftStart = primaryShift.getStartTime();
            LocalTime shiftEnd = primaryShift.getEndTime();
            int graceIn = primaryShift.getGraceInMins() != null ? primaryShift.getGraceInMins() : 0;
            int graceOut = primaryShift.getGraceOutMins() != null ? primaryShift.getGraceOutMins() : 0;
            RoundingRule rounding = primaryShift.getRounding() != null ? primaryShift.getRounding() : RoundingRule.NONE;
            int roundingMins = getRoundingMinutes(rounding);
            
            LocalTime actualIn = firstInLocal.toLocalTime();
            LocalTime graceEndTime = shiftStart.plusMinutes(graceIn);
            
            // Check if late (arrived after shift start + grace period)
            if (actualIn.isAfter(graceEndTime)) {
                isLateIn = true;
                
                // Calculate late minutes and apply rounding
                // Rounding UP for late arrivals: 9:01-9:14 -> 9:15 (with 15 min rounding)
                if (roundingMins > 0) {
                    // Round UP to next rounding interval
                    int minuteOfDay = actualIn.getHour() * 60 + actualIn.getMinute();
                    int roundedMinute = ((minuteOfDay + roundingMins - 1) / roundingMins) * roundingMins;
                    roundedIn = LocalDateTime.of(date, LocalTime.of(roundedMinute / 60, roundedMinute % 60));
                    
                    // Late minutes = rounded time - shift start
                    lateByMins = (int) Duration.between(
                        LocalDateTime.of(date, shiftStart), 
                        roundedIn
                    ).toMinutes();
                } else {
                    roundedIn = firstInLocal;
                    lateByMins = (int) Duration.between(
                        LocalDateTime.of(date, shiftStart), 
                        firstInLocal
                    ).toMinutes();
                }
            } else {
                roundedIn = firstInLocal; // No rounding needed if on time
            }
            
            // Check if early out (left before shift end - grace period)
            if (lastOutLocal != null) {
                LocalTime actualOut = lastOutLocal.toLocalTime();
                LocalDate outDate = lastOutLocal.toLocalDate();
                LocalTime graceStartTime = shiftEnd.minusMinutes(graceOut);
                
                // Handle cross-midnight shifts
                LocalDateTime expectedEnd = crossedMidnight 
                    ? LocalDateTime.of(date.plusDays(1), shiftEnd)
                    : LocalDateTime.of(date, shiftEnd);
                LocalDateTime graceStart = expectedEnd.minusMinutes(graceOut);
                
                if (lastOutLocal.isBefore(graceStart)) {
                    isEarlyOut = true;
                    
                    // Calculate early minutes and apply rounding
                    // Rounding DOWN for early departures: 5:16-5:29 -> 5:15 (with 15 min rounding)
                    if (roundingMins > 0) {
                        // Round DOWN to previous rounding interval
                        int minuteOfDay = actualOut.getHour() * 60 + actualOut.getMinute();
                        int roundedMinute = (minuteOfDay / roundingMins) * roundingMins;
                        roundedOut = LocalDateTime.of(outDate, LocalTime.of(roundedMinute / 60, roundedMinute % 60));
                        
                        // Early minutes = shift end - rounded time
                        earlyByMins = (int) Duration.between(roundedOut, expectedEnd).toMinutes();
                    } else {
                        roundedOut = lastOutLocal;
                        earlyByMins = (int) Duration.between(lastOutLocal, expectedEnd).toMinutes();
                    }
                } else {
                    roundedOut = lastOutLocal; // No rounding needed if on time
                }
            }
            
            // Recalculate work minutes using rounded times if rounding is applied
            if (roundingMins > 0 && roundedIn != null && roundedOut != null) {
                long roundedWorkMins = Duration.between(roundedIn, roundedOut).toMinutes();
                int breakMins = primaryShift.getBreakMins() != null ? primaryShift.getBreakMins() : 0;
                dayWorkTotal = (int) Math.max(0, roundedWorkMins - breakMins);
            }
        }
        
        // Get configurable thresholds
        SalaryOvertimeConfig config = configService.getConfig();
        int halfDayThresholdMins = config.getHalfDayMinHours() * 60; // Convert hours to minutes
        int fullDayThresholdMins = config.getFullDayMinHours() * 60;
        int otMinThresholdMins = config.getOvertimeMinThresholdMins();
        
        // Check if employee is on leave for this date
        boolean isOnLeave = false;
        if (tenantId != null) {
            // Get employee code for leave lookup
            String empCode = employeeRepo.findById(employeeId)
                    .map(Employee::getEmpCode)
                    .orElse(null);
            if (empCode != null) {
                // Check for approved leaves that cover this date
                List<EmployeeLeave> leaves = leaveRepo.findByTenantIdAndEmpIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        tenantId, empCode, date, date);
                isOnLeave = leaves.stream()
                        .anyMatch(l -> l.getStatus() == LeaveStatus.APPROVED);
            }
        }
        
        // Determine status - if missing punch and has morning IN, treat as PRESENT (pending review)
        String status;
        if (isOvertimeDay) {
            // Worked on weekly off or holiday = OVERTIME
            status = "OT_DAY";
        } else if (missingPunch && "OUT".equals(missingPunchType)) {
            // Employee came to work but missed OUT punch - mark as PRESENT but needs review
            status = "PRESENT";
            // Estimate work time based on shift (assume 8 hours if morning punch)
            if (dayWorkTotal == 0 && firstInLocal != null) {
                // No calculated work time because OUT is missing
                // Don't override dayWorkTotal here, let admin fix it
            }
        } else if (dayWorkTotal == 0) {
            // No work done - check if on leave or absent
            status = isOnLeave ? "LEAVE" : "ABSENT";
        } else if (dayWorkTotal < halfDayThresholdMins) {
            status = "HALF_DAY";
        } else {
            status = "PRESENT";
        }

        // replace the AttendanceDay for this date
        dayRepo.deleteAll(dayRepo.findByEmployeeIdAndWorkDateBetween(employeeId, date, date));
        AttendanceDay ad = AttendanceDay.builder()
                .tenantId(tenantId)
                .orgId(orgId)
                .employeeId(employeeId)
                .workDate(date)
                .totalWorkMin(dayWorkTotal)
                .totalOTEligibleMin(dayOTEligible)
                .totalOTApprovedMin(dayOTApproved)
                .firstIn(firstInLocal)
                .lastOut(lastOutLocal)
                .roundedIn(roundedIn)
                .roundedOut(roundedOut)
                .isLateIn(isLateIn)
                .isEarlyOut(isEarlyOut)
                .lateByMins(lateByMins)
                .earlyByMins(earlyByMins)
                .punchCount(punches.size())
                .shiftCodes(shiftCodesStr)
                .dualShift(isDualShift)
                .crossedMidnight(crossedMidnight)
                .status(status)
                .missingPunch(missingPunch)
                .missingPunchType(missingPunchType)
                .needsReview(needsReview)
                .isWeeklyOff(holidayInfo.isWeeklyOff)
                .isHoliday(holidayInfo.isHoliday)
                .holidayName(holidayInfo.holidayName)
                .isOvertimeDay(isOvertimeDay)
                .overtimeOnHolidayMins(overtimeOnHolidayMins)
                .build();
        dayRepo.save(ad);
    }

    /* ================= helpers: shifts & segments ================= */

    private List<ShiftWindow> shiftWindowsFor(Long employeeId, LocalDate date) {
        Employee stub = new Employee();
        try {
            Method m = findMethod(Employee.class, "setId", Long.class);
            if (m != null) m.invoke(stub, employeeId);
        } catch (Exception ignore) {
        }

        List<EmployeeShiftAssignment> all = Collections.emptyList();
        try {
            all = empShiftRepo.findByEmployee(stub);
        } catch (Exception ex) {
            all = List.of();
        }

        if (all.isEmpty()) return List.of();

        List<ShiftWindow> out = new ArrayList<>();
        for (EmployeeShiftAssignment a : all) {
            LocalDate start = a.getStartDate();
            LocalDate end = a.getEndDate();
            if ((start != null && date.isBefore(start)) || (end != null && date.isAfter(end))) continue;

            if (a.getPatternType() != null && a.getPatternType() != PatternType.NONE) continue;

            Shift s = a.getShift();
            if (s == null) continue;
            if (!appliesToDate(s, date)) continue;

            boolean crossesMidnight = s.getEndTime().isBefore(s.getStartTime());
            LocalDate endDate = crossesMidnight ? date.plusDays(1) : date;

            Instant st = ZonedDateTime.of(date, s.getStartTime(), ORG_TZ).toInstant();
            Instant en = ZonedDateTime.of(endDate, s.getEndTime(), ORG_TZ).toInstant();

            out.add(new ShiftWindow(s.getCode(), st, en, s));
        }
        out.sort(Comparator.comparing(w -> w.utcStart));
        return out;
    }

    private int maxBoundaryAfterMidnight(Long employeeId, LocalDate date) {
        int max = 0;
        for (ShiftWindow w : shiftWindowsFor(employeeId, date)) {
            Shift s = w.shift;
            if (s == null) continue;
            Integer b = s.getBoundaryAfterMidnightMins();
            if (b != null) max = Math.max(max, b);
        }
        return max;
    }

    private boolean appliesToDate(Shift s, LocalDate d) {
        if (s.getEffectiveFrom() != null && d.isBefore(s.getEffectiveFrom())) return false;
        if (s.getEffectiveTo() != null && d.isAfter(s.getEffectiveTo())) return false;
        if (!s.isActive()) return false;
        return switch (d.getDayOfWeek()) {
            case MONDAY -> s.isMon();
            case TUESDAY -> s.isTue();
            case WEDNESDAY -> s.isWed();
            case THURSDAY -> s.isThu();
            case FRIDAY -> s.isFri();
            case SATURDAY -> s.isSat();
            case SUNDAY -> s.isSun();
        };
    }

    private List<Span> toSegments(List<Instant> pts, Duration gap) {
        List<Span> out = new ArrayList<>();
        if (pts.isEmpty()) return out;
        
        // For proper IN/OUT pairing, process punches in pairs
        // Each pair represents a work session (IN to OUT)
        for (int i = 0; i < pts.size() - 1; i += 2) {
            Instant inTime = pts.get(i);
            Instant outTime = pts.get(i + 1);
            // Only add if out is after in
            if (outTime.isAfter(inTime)) {
                out.add(new Span(inTime, outTime));
            }
        }
        
        // If odd number of punches, the last one is orphan (IN without OUT)
        // Don't create a span for it as it has no duration
        
        return out;
    }

    private Span intersect(Span seg, Instant wStart, Instant wEnd) {
        Instant s = seg.start.isAfter(wStart) ? seg.start : wStart;
        Instant e = seg.end.isBefore(wEnd) ? seg.end : wEnd;
        return s.isBefore(e) ? new Span(s, e) : null;
    }

    private List<AttendanceSession> mergeAdjacentSameShift(List<AttendanceSession> in, Duration tinyGap) {
        if (in.isEmpty()) return in;
        in.sort(Comparator.comparing(x -> (String) getIf(x, new String[]{"getShiftCode", "getShift", "shiftCode"})));
        List<AttendanceSession> out = new ArrayList<>();
        AttendanceSession cur = in.get(0);
        for (int i = 1; i < in.size(); i++) {
            AttendanceSession nx = in.get(i);

            Instant curOut = (Instant) getIf(cur, new String[]{"getOutTsUtc", "getOutTimeUtc", "getOutTime", "getOutAt"});
            Instant nxIn = (Instant) getIf(nx, new String[]{"getInTsUtc", "getInTimeUtc", "getInTime", "getInAt"});
            String curShift = (String) getIf(cur, new String[]{"getShiftCode", "getShift", "shiftCode"});
            String nxShift = (String) getIf(nx, new String[]{"getShiftCode", "getShift", "shiftCode"});

            if (Objects.equals(curShift, nxShift)
                    && curOut != null && nxIn != null
                    && Duration.between(curOut, nxIn).compareTo(tinyGap) <= 0) {
                setIf(cur, new String[]{"setOutTsUtc", "setOutTimeUtc", "setOutTime", "setOutAt"}, getIf(nx, new String[]{"getOutTsUtc", "getOutTimeUtc", "getOutTime", "getOutAt"}));
                int curWork = (Integer) (getIf(cur, new String[]{"getWorkMin", "workMin"}) == null ? 0 : (Integer) getIf(cur, new String[]{"getWorkMin", "workMin"}));
                int nxWork = (Integer) (getIf(nx, new String[]{"getWorkMin", "workMin"}) == null ? 0 : (Integer) getIf(nx, new String[]{"getWorkMin", "workMin"}));
                setIf(cur, new String[]{"setWorkMin", "workMin"}, curWork + nxWork);
            } else {
                out.add(cur);
                cur = nx;
            }
        }
        out.add(cur);
        return out;
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * Remove duplicate punches that have the same timestamp.
     */
    private List<AttendancePunch> removeDuplicatePunches(List<AttendancePunch> punches) {
        if (punches == null || punches.isEmpty()) return punches;
        
        Set<Long> seenTimestamps = new HashSet<>();
        List<AttendancePunch> unique = new ArrayList<>();
        
        for (AttendancePunch p : punches) {
            long ts = punchInstant(p).toEpochMilli();
            if (!seenTimestamps.contains(ts)) {
                seenTimestamps.add(ts);
                unique.add(p);
            }
        }
        
        return unique;
    }

    /* ================= tolerant reflection ================= */

    private AttendanceSession newSession() {
        try {
            return AttendanceSession.class.getDeclaredConstructor().newInstance();
        } catch (Exception ignore) {
            Object b = callStatic(AttendanceSession.class, "builder");
            if (b != null) {
                Object built = call(b, "build");
                if (built instanceof AttendanceSession s) return s;
            }
            throw new IllegalStateException("AttendanceSession needs a no-arg ctor or Lombok @Builder.");
        }
    }

    private Instant punchInstant(AttendancePunch p) {
        Object v = getIf(p, new String[]{"getPunchTsUtc", "getPunchTimestamp", "getTimestamp", "getTs"});
        if (v instanceof Instant i) return i;
        v = getIf(p, new String[]{"getPunchTime", "getTime", "getLocalDateTime"});
        if (v instanceof LocalDateTime ldt) return ldt.atZone(ORG_TZ).toInstant();
        throw new IllegalStateException("AttendancePunch must expose timestamp (Instant or LocalDateTime).");
    }

    private Object getIf(Object obj, String[] getters) {
        for (String g : getters) {
            Method m = findMethod(obj.getClass(), g);
            if (m != null) return safeInvoke(obj, m);
        }
        return null;
    }

    private void setIf(Object obj, String[] setters, Object value) {
        for (String s : setters) {
            Method m = findMethod(obj.getClass(), s, value == null ? Object.class : value.getClass());
            if (m != null) {
                safeInvoke(obj, m, value);
                return;
            }
        }
        Object tb = call(obj, "toBuilder");
        if (tb != null) {
            for (String s : setters) {
                Method m = findMethod(tb.getClass(), s, value == null ? Object.class : value.getClass());
                if (m != null) {
                    tb = call(tb, s, value);
                    call(tb, "build");
                    return;
                }
            }
        }
    }

    private Method findMethod(Class<?> cls, String name, Class<?>... paramTypes) {
        try {
            return cls.getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            for (Method m : cls.getMethods()) {
                if (!m.getName().equals(name)) continue;
                if (paramTypes.length == 0 && m.getParameterCount() == 0) return m;
                if (paramTypes.length == 1 && m.getParameterCount() == 1) return m;
            }
            return null;
        }
    }

    private Object call(Object target, String name, Object... args) {
        try {
            Method m = findCompatibleMethod(target.getClass(), name, args);
            if (m == null) return null;
            return m.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    private Object callStatic(Class<?> type, String name, Object... args) {
        try {
            Method m = findCompatibleMethod(type, name, args);
            if (m == null) return null;
            return m.invoke(null, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    private Method findCompatibleMethod(Class<?> type, String name, Object... args) {
        for (Method m : type.getMethods()) {
            if (!m.getName().equals(name)) continue;
            if (args == null || args.length == 0) {
                if (m.getParameterCount() == 0) return m;
            } else if (m.getParameterCount() == args.length) {
                return m;
            }
        }
        return null;
    }

    private Object safeInvoke(Object target, Method m, Object... args) {
        try {
            return m.invoke(target, args);
        } catch (Exception e) {
            return null;
        }
    }

    /* ================= Holiday and Weekly Off Logic ================= */
    
    /**
     * Checks if a given date is a weekly off or holiday for the employee
     */
    private HolidayInfo getHolidayInfo(Long orgId, Long employeeId, LocalDate date) {
        HolidayInfo info = new HolidayInfo();
        
        // Get employee to determine employment type
        Employee employee = employeeRepo.findById(employeeId).orElse(null);
        if (employee == null) {
            return info;
        }
        
        EmploymentType empType = employee.getEmploymentType();
        if (empType == null) {
            empType = EmploymentType.FULL_TIME;
        }
        
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        
        // 1. Check employee-level weekly off first (if configured)
        String empWeeklyOff = employee.getWeeklyOffDays();
        if (empWeeklyOff != null && !empWeeklyOff.isBlank()) {
            if (empWeeklyOff.contains(dayOfWeek.name())) {
                info.isWeeklyOff = true;
            }
        } else {
            // 2. Check organization-level weekly off config
            // Note: org_id in weekly_off_config is a string like "ORG001"
            String orgIdStr = "ORG001"; // Default org ID for this installation
            Optional<WeeklyOffConfig> weeklyOffConfig = 
                weeklyOffConfigRepo.findByOrgIdAndEmploymentTypeAndActiveTrue(orgIdStr, empType);
            
            if (weeklyOffConfig.isPresent()) {
                WeeklyOffConfig config = weeklyOffConfig.get();
                
                // Check if this day of week is a weekly off
                if (config.isWeeklyOff(dayOfWeek)) {
                    info.isWeeklyOff = true;
                }
                
                // Check alternate Saturday rule
                if (dayOfWeek == DayOfWeek.SATURDAY && !info.isWeeklyOff) {
                    String satRule = config.getAlternateSaturdayRule();
                    if ("ALL_SATURDAYS_OFF".equals(satRule)) {
                        info.isWeeklyOff = true;
                    } else if ("SECOND_AND_FOURTH_OFF".equals(satRule)) {
                        int weekOfMonth = (date.getDayOfMonth() - 1) / 7 + 1;
                        if (weekOfMonth == 2 || weekOfMonth == 4) {
                            info.isWeeklyOff = true;
                        }
                    } else if ("FIRST_AND_THIRD_OFF".equals(satRule)) {
                        int weekOfMonth = (date.getDayOfMonth() - 1) / 7 + 1;
                        if (weekOfMonth == 1 || weekOfMonth == 3) {
                            info.isWeeklyOff = true;
                        }
                    }
                }
            }
        }
        
        // 3. Check calendar holidays
        // Note: org_id in holidays table is a string like "ORG001"
        List<Holiday> holidays = holidayRepo.findByOrgIdAndHolidayDateBetweenAndActiveTrue(
            "ORG001", date, date);
        
        for (Holiday h : holidays) {
            // Check if this holiday applies to the employee's employment type
            if (h.appliesToEmploymentType(empType)) {
                info.isHoliday = true;
                info.holidayName = h.getName();
                break;
            }
        }
        
        return info;
    }
    
    /* simple holders */
    private record Span(Instant start, Instant end) {
    }

    private static class ShiftWindow {
        final String shiftCode;
        final Instant utcStart;
        final Instant utcEnd;
        final Shift shift;

        ShiftWindow(String shiftCode, Instant utcStart, Instant utcEnd, Shift shift) {
            this.shiftCode = shiftCode;
            this.utcStart = utcStart;
            this.utcEnd = utcEnd;
            this.shift = shift;
        }
    }
    
    private static class HolidayInfo {
        boolean isWeeklyOff = false;
        boolean isHoliday = false;
        String holidayName = null;
    }
    
    /**
     * Get the rounding interval in minutes based on the rounding rule.
     * Returns 0 if no rounding should be applied.
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
}
