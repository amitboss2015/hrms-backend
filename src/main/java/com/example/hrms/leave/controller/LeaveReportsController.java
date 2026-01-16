package com.example.hrms.leave.controller;

import com.example.hrms.domain.Employee;
import com.example.hrms.leave.domain.EmployeeLeave;
import com.example.hrms.leave.repo.EmployeeLeaveRepository;
import com.example.hrms.repo.EmployeeRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/leave/reports")
@CrossOrigin(origins = "*")
public class LeaveReportsController {

    private final EmployeeLeaveRepository leaveRepo;
    private final EmployeeRepository employeeRepo;

    public LeaveReportsController(EmployeeLeaveRepository leaveRepo, EmployeeRepository employeeRepo) {
        this.leaveRepo = leaveRepo;
        this.employeeRepo = employeeRepo;
    }

    /**
     * Get all leaves in a date range with employee details
     * Shows who is on leave during a specific period
     */
    @GetMapping("/date-range")
    public List<Map<String, Object>> getLeavesByDateRange(
            @RequestParam String orgId,
            @RequestParam String fromDate,
            @RequestParam String toDate) {

        LocalDate from = LocalDate.parse(fromDate);
        LocalDate to = LocalDate.parse(toDate);

        // Find all leaves that overlap with the date range
        List<EmployeeLeave> leaves = leaveRepo.findByOrgIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                orgId, to, from);

        // Get employee details
        Set<String> empIds = leaves.stream()
                .map(EmployeeLeave::getEmpId)
                .collect(Collectors.toSet());
        
        Map<String, Employee> empMap = new HashMap<>();
        for (String empId : empIds) {
            employeeRepo.findByEmpCode(empId).ifPresent(e -> empMap.put(empId, e));
        }

        return leaves.stream().map(leave -> {
            Map<String, Object> map = new LinkedHashMap<>();
            Employee emp = empMap.get(leave.getEmpId());
            
            map.put("id", leave.getId());
            map.put("empId", leave.getEmpId());
            map.put("empName", emp != null ? 
                    ((emp.getFirstName() != null ? emp.getFirstName() : "") + " " + 
                     (emp.getLastName() != null ? emp.getLastName() : "")).trim() : leave.getEmpId());
            map.put("leaveTypeId", leave.getLeaveType() != null ? leave.getLeaveType().getId() : null);
            map.put("leaveTypeCode", leave.getLeaveType() != null ? leave.getLeaveType().getCode() : null);
            map.put("leaveTypeName", leave.getLeaveType() != null ? leave.getLeaveType().getName() : null);
            map.put("startDate", leave.getStartDate().toString());
            map.put("endDate", leave.getEndDate().toString());
            map.put("totalDays", leave.getTotalDays().toPlainString());
            map.put("durationKind", leave.getDurationKind().name());
            map.put("status", leave.getStatus().name());
            map.put("remarks", leave.getRemarks());
            
            return map;
        }).sorted(Comparator.comparing(m -> (String) m.get("startDate")))
          .collect(Collectors.toList());
    }

    /**
     * Get leave history for a specific employee in a date range
     */
    @GetMapping("/employee")
    public List<Map<String, Object>> getEmployeeLeaves(
            @RequestParam String orgId,
            @RequestParam String empId,
            @RequestParam String fromDate,
            @RequestParam String toDate) {

        LocalDate from = LocalDate.parse(fromDate);
        LocalDate to = LocalDate.parse(toDate);

        List<EmployeeLeave> leaves = leaveRepo.findByOrgIdAndEmpIdAndStartDateBetween(
                orgId, empId, from, to);

        return leaves.stream().map(leave -> {
            Map<String, Object> map = new LinkedHashMap<>();
            
            map.put("id", leave.getId());
            map.put("leaveTypeId", leave.getLeaveType() != null ? leave.getLeaveType().getId() : null);
            map.put("leaveTypeCode", leave.getLeaveType() != null ? leave.getLeaveType().getCode() : null);
            map.put("leaveTypeName", leave.getLeaveType() != null ? leave.getLeaveType().getName() : null);
            map.put("startDate", leave.getStartDate().toString());
            map.put("endDate", leave.getEndDate().toString());
            map.put("totalDays", leave.getTotalDays().toPlainString());
            map.put("durationKind", leave.getDurationKind().name());
            map.put("status", leave.getStatus().name());
            map.put("payable", leave.getPayable());
            map.put("remarks", leave.getRemarks());
            map.put("consumedFrom", leave.getConsumedFrom() != null ? leave.getConsumedFrom().name() : null);
            
            return map;
        }).sorted(Comparator.comparing(m -> (String) m.get("startDate")))
          .collect(Collectors.toList());
    }

    /**
     * Get daily leave count for a date range
     * Shows how many employees are on leave each day
     */
    @GetMapping("/daily")
    public List<Map<String, Object>> getDailyLeaveSummary(
            @RequestParam String orgId,
            @RequestParam String fromDate,
            @RequestParam String toDate) {

        LocalDate from = LocalDate.parse(fromDate);
        LocalDate to = LocalDate.parse(toDate);

        // Find all leaves that overlap with the date range
        List<EmployeeLeave> leaves = leaveRepo.findByOrgIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                orgId, to, from);

        // Get employee details
        Set<String> empIds = leaves.stream()
                .map(EmployeeLeave::getEmpId)
                .collect(Collectors.toSet());
        
        Map<String, Employee> empMap = new HashMap<>();
        for (String empId : empIds) {
            employeeRepo.findByEmpCode(empId).ifPresent(e -> empMap.put(empId, e));
        }

        // Build daily summary
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            final LocalDate currentDate = date;
            
            // Find employees on leave on this date
            List<String> employeesOnLeave = leaves.stream()
                    .filter(l -> !currentDate.isBefore(l.getStartDate()) && !currentDate.isAfter(l.getEndDate()))
                    .map(l -> {
                        Employee emp = empMap.get(l.getEmpId());
                        if (emp != null) {
                            String name = ((emp.getFirstName() != null ? emp.getFirstName() : "") + " " + 
                                          (emp.getLastName() != null ? emp.getLastName() : "")).trim();
                            return l.getEmpId() + " - " + (name.isEmpty() ? l.getEmpId() : name);
                        }
                        return l.getEmpId();
                    })
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            Map<String, Object> dayData = new LinkedHashMap<>();
            dayData.put("date", currentDate.toString());
            dayData.put("count", employeesOnLeave.size());
            dayData.put("employees", employeesOnLeave);
            
            result.add(dayData);
        }

        return result;
    }

    /**
     * Get leave summary statistics for a period
     */
    @GetMapping("/summary")
    public Map<String, Object> getLeaveSummary(
            @RequestParam String orgId,
            @RequestParam String fromDate,
            @RequestParam String toDate) {

        LocalDate from = LocalDate.parse(fromDate);
        LocalDate to = LocalDate.parse(toDate);

        List<EmployeeLeave> leaves = leaveRepo.findByOrgIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                orgId, to, from);

        Map<String, Object> summary = new LinkedHashMap<>();
        
        summary.put("totalLeaveRequests", leaves.size());
        summary.put("uniqueEmployeesOnLeave", leaves.stream()
                .map(EmployeeLeave::getEmpId)
                .distinct()
                .count());
        summary.put("totalLeaveDays", leaves.stream()
                .map(l -> l.getTotalDays().doubleValue())
                .reduce(0.0, Double::sum));

        // Group by leave type
        Map<String, Long> byType = leaves.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getLeaveType() != null ? l.getLeaveType().getCode() : "UNKNOWN",
                        Collectors.counting()));
        summary.put("byLeaveType", byType);

        // Group by status
        Map<String, Long> byStatus = leaves.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getStatus().name(),
                        Collectors.counting()));
        summary.put("byStatus", byStatus);

        return summary;
    }
}
