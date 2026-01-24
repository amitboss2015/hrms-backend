package com.example.hrms.attendance.service.impl;

import com.example.hrms.attendance.domain.AttendanceDay;
import com.example.hrms.attendance.dto.MonthlySummaryDTO;
import com.example.hrms.attendance.repo.AttendanceDayRepository;
import com.example.hrms.attendance.repo.AttendancePunchRepository;
import com.example.hrms.attendance.service.AttendanceSummaryService;
import com.example.hrms.attendance.service.AttendanceEngine;
import com.example.hrms.domain.Employee;
import com.example.hrms.domain.EmployeeShiftAssignment;
import com.example.hrms.domain.Shift;
import com.example.hrms.payroll.domain.Payroll;
import com.example.hrms.payroll.repo.PayrollRepository;
import com.example.hrms.repo.EmployeeRepository;
import com.example.hrms.repo.EmployeeShiftAssignmentRepository;
import com.example.hrms.repo.ShiftRepository;
import com.example.hrms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceSummaryServiceImpl implements AttendanceSummaryService {

    private final AttendanceDayRepository dayRepo;
    private final EmployeeRepository employeeRepo;
    private final AttendancePunchRepository punchRepo;
    private final AttendanceEngine attendanceEngine;
    private final ShiftRepository shiftRepo;
    private final EmployeeShiftAssignmentRepository shiftAssignmentRepo;
    private final PayrollRepository payrollRepo;

    private static final ZoneId ORG_TZ = ZoneId.of("Asia/Kolkata");
    private static final int LATE_THRESHOLD_MINS = 10;
    private static final int LATES_PER_ABSENT = 3;

    @Override
    public List<MonthlySummaryDTO> getSummary(int year, int month, Long orgId) {
        // Get tenant ID from context - this is the key for data isolation
        String tenantId = TenantContext.getTenantId();
        log.info("📊 Getting attendance summary for tenant: {}, year: {}, month: {}", tenantId, year, month);
        
        if (tenantId == null || tenantId.isEmpty()) {
            log.warn("⚠️ No tenant context set, returning empty summary");
            return List.of();
        }
        
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        int totalDaysInMonth = ym.lengthOfMonth();

        // Get attendance day records for the month - FILTERED BY TENANT
        List<AttendanceDay> days = dayRepo.findByWorkDateBetween(from, to).stream()
                .filter(d -> tenantId.equals(d.getTenantId()) || 
                             (d.getTenantId() == null && orgId != null && orgId.toString().equals(String.valueOf(d.getOrgId()))))
                .collect(Collectors.toList());

        // If no computed day records, rebuild from punches
        if (days.isEmpty()) {
            Instant fromUtc = from.atStartOfDay(ORG_TZ).toInstant();
            Instant toUtc = to.plusDays(1).atStartOfDay(ORG_TZ).toInstant();

            List<Long> empIds = punchRepo.findDistinctEmployeeIdsForRange(
                    orgId == null ? null : orgId, fromUtc, toUtc);

            for (Long empId : empIds) {
                attendanceEngine.rebuildEmployeeMonth(orgId == null ? 1L : orgId, empId, ym);
            }
            days = dayRepo.findByWorkDateBetween(from, to).stream()
                    .filter(d -> tenantId.equals(d.getTenantId()) || 
                                 (d.getTenantId() == null && orgId != null && orgId.toString().equals(String.valueOf(d.getOrgId()))))
                    .collect(Collectors.toList());
            if (days.isEmpty()) {
                log.info("📊 No attendance data found for tenant: {}", tenantId);
                return List.of();
            }
        }

        // Get employees for THIS TENANT ONLY
        List<Employee> allEmployees = employeeRepo.findByTenantId(tenantId);
        log.info("📊 Found {} employees for tenant: {}", allEmployees.size(), tenantId);
        Map<Long, Employee> empMap = allEmployees.stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        // Get shift assignments - FILTERED BY TENANT (via employee)
        List<EmployeeShiftAssignment> shiftAssignments = shiftAssignmentRepo.findAll().stream()
                .filter(sa -> sa.getEmployee() != null && tenantId.equals(sa.getEmployee().getTenantId()))
                .collect(Collectors.toList());
        Map<Long, Long> empToShiftMap = shiftAssignments.stream()
                .filter(sa -> sa.getEmployee() != null && sa.getShift() != null)
                .collect(Collectors.toMap(
                        sa -> sa.getEmployee().getId(),
                        sa -> sa.getShift().getId(),
                        (a, b) -> b // Keep latest
                ));

        // Get shifts for THIS TENANT ONLY
        List<Shift> allShifts = shiftRepo.findByTenantId(tenantId);
        Map<Long, Shift> shiftMap = allShifts.stream()
                .collect(Collectors.toMap(Shift::getId, s -> s));

        // Get payroll data for the month - use tenant ID
        List<Payroll> payrolls = payrollRepo.findByOrgIdAndYearAndMonthOrderByEmpIdAsc(tenantId, year, month);
        Map<String, Payroll> payrollMap = payrolls.stream()
                .collect(Collectors.toMap(Payroll::getEmpId, p -> p, (a, b) -> b));

        // Aggregate by employee
        Map<Long, SummaryCounter> agg = new HashMap<>();
        for (AttendanceDay d : days) {
            SummaryCounter c = agg.computeIfAbsent(d.getEmployeeId(), k -> new SummaryCounter());
            String status = (d.getStatus() == null ? "" : d.getStatus()).toUpperCase(Locale.ROOT);
            
            switch (status) {
                case "LEAVE" -> c.leave++;
                case "PRESENT" -> c.present++;
                case "HALF_DAY", "PARTIAL" -> {
                    c.halfDays++;
                }
                case "ABSENT" -> c.absent++;
                case "WEEKLY_OFF" -> c.weeklyOff++;
                case "HOLIDAY" -> c.holidays++;
                case "OT_DAY" -> {
                    // Worked on weekly off or holiday - counts as overtime
                    c.overtimeDays++;
                    c.present++; // Also counts as present since they worked
                    // Track the original off type
                    if (Boolean.TRUE.equals(d.getIsWeeklyOff())) {
                        c.weeklyOff++;
                    }
                    if (Boolean.TRUE.equals(d.getIsHoliday())) {
                        c.holidays++;
                    }
                }
                default -> {
                    if (nz(d.getTotalWorkMin()) > 0) c.present++;
                    else c.absent++;
                }
            }
            
            // Track late IN days
            if (Boolean.TRUE.equals(d.getIsLateIn())) {
                c.lateDays++;
                c.totalLateMinutes += nz(d.getLateByMins());
            }
            
            // Track early OUT days
            if (Boolean.TRUE.equals(d.getIsEarlyOut())) {
                c.earlyOutDays++;
                c.totalEarlyOutMinutes += nz(d.getEarlyByMins());
            }
            
            // Track dual shift days
            if (Boolean.TRUE.equals(d.getDualShift())) {
                c.dualShiftDays++;
            }
            
            // Also track OT days based on isOvertimeDay flag (fallback)
            if (Boolean.TRUE.equals(d.getIsOvertimeDay()) && !"OT_DAY".equals(status)) {
                c.overtimeDays++;
            }
            
            // Sum total work and OT minutes
            c.totalWorkMinutes += nz(d.getTotalWorkMin());
            c.otMinutes += nz(d.getTotalOTApprovedMin());
        }

        // Build result
        List<MonthlySummaryDTO> out = new ArrayList<>();
        
        for (Map.Entry<Long, SummaryCounter> e : agg.entrySet()) {
            Employee emp = empMap.get(e.getKey());
            if (emp == null) continue;
            
            SummaryCounter c = e.getValue();
            String name = safeName(emp);
            String empCode = safeCode(emp);
            
            // Get shift info
            Long shiftId = empToShiftMap.get(emp.getId());
            Shift shift = shiftId != null ? shiftMap.get(shiftId) : null;
            String shiftName = shift != null ? shift.getName() : "Default";
            
            // Calculate late deduction days
            int lateDeductionDays = c.lateDays / LATES_PER_ABSENT;
            
            // Calculate work hours
            BigDecimal totalWorkHours = BigDecimal.valueOf(c.totalWorkMinutes)
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            BigDecimal avgWorkHours = c.present > 0 
                    ? totalWorkHours.divide(BigDecimal.valueOf(c.present), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal otHours = BigDecimal.valueOf(c.otMinutes)
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            
            // Calculate total working days (excluding weekly off and holidays)
            int totalWorkingDays = totalDaysInMonth - c.weeklyOff - c.holidays;
            
            // Calculate absent days properly
            // Absent = Total working days - (present + leave + halfDays counted as partial)
            // Note: halfDays count as 0.5 present, so they contribute 0.5 to absent
            int calculatedDaysAccounted = c.present + c.leave + c.halfDays + c.absent;
            int calculatedAbsent = totalWorkingDays - calculatedDaysAccounted;
            // Use the higher of recorded absent or calculated absent
            int effectiveAbsent = Math.max(c.absent, calculatedAbsent);
            // Make sure absent is not negative
            if (effectiveAbsent < 0) effectiveAbsent = 0;
            
            // Get payroll info
            Payroll payroll = payrollMap.get(empCode);
            
            MonthlySummaryDTO dto = MonthlySummaryDTO.builder()
                    .empCode(empCode)
                    .empName(name)
                    .name(name)
                    .department(emp.getDepartment())
                    .designation(emp.getDesignation())
                    .shiftName(shiftName)
                    .totalWorkingDays(totalWorkingDays)
                    .present(c.present)
                    .absent(effectiveAbsent)
                    .leaveDays(c.leave)
                    .leave(c.leave)
                    .halfDays(c.halfDays)
                    .weeklyOff(c.weeklyOff)
                    .holidays(c.holidays)
                    .lateDays(c.lateDays)
                    .earlyOutDays(c.earlyOutDays)
                    .totalLateMinutes(c.totalLateMinutes)
                    .totalEarlyOutMinutes(c.totalEarlyOutMinutes)
                    .lateDeductionDays(lateDeductionDays)
                    .overtimeDays(c.overtimeDays)
                    .otMinutes(c.otMinutes)
                    .overtimeHours(otHours)
                    .totalWorkMinutes(c.totalWorkMinutes)
                    .totalWorkHours(totalWorkHours)
                    .avgWorkHoursPerDay(avgWorkHours)
                    .dualShiftDays(c.dualShiftDays)
                    .build();
            
            // Add payroll info if available
            if (payroll != null) {
                dto.setBasicSalary(payroll.getBasicSalary());
                dto.setFinalPayment(payroll.getFinalPayment());
                dto.setWorkingDayAmount(payroll.getWorkingDayAmount());
                dto.setGrossSalary(payroll.getGrossSalary());
                dto.setNetSalary(payroll.getNetSalary());
                dto.setPayrollStatus(payroll.getStatus() != null ? payroll.getStatus().name() : null);
            }
            
            out.add(dto);
        }
        
        out.sort(Comparator.comparing(MonthlySummaryDTO::getEmpCode));
        return out;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static class SummaryCounter {
        int present, absent, leave, halfDays, lateDays, earlyOutDays, dualShiftDays;
        int weeklyOff, holidays, overtimeDays;
        int totalWorkMinutes, otMinutes;
        int totalLateMinutes, totalEarlyOutMinutes;
    }

    private static String safeCode(Employee e) {
        try {
            String c = e.getEmpCode();
            if (c != null) return c;
        } catch (Exception ignore) {}
        try {
            return String.valueOf(e.getId());
        } catch (Exception ignore) {}
        return "";
    }

    private static String safeName(Employee e) {
        try {
            String fn = e.getFirstName();
            String ln = e.getLastName();
            if (fn != null || ln != null) {
                String name = ((fn != null ? fn : "") + " " + (ln != null ? ln : "")).trim();
                if (!name.isBlank()) return name;
            }
        } catch (Exception ignore) {}
        
        try {
            String code = e.getEmpCode();
            if (code != null && !code.isBlank()) return code;
        } catch (Exception ignore) {}
        
        return "—";
    }
}
