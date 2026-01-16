
package com.example.hrms.service;

import com.example.hrms.domain.*;
import com.example.hrms.domain.enums.PatternType;
import com.example.hrms.repo.EmployeeShiftAssignmentRepository;
import com.example.hrms.repo.EmployeeRepository;
import com.example.hrms.repo.ShiftRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class AssignmentService {
  private final EmployeeShiftAssignmentRepository repo;
  private final EmployeeRepository empRepo;
  private final ShiftRepository shiftRepo;

  public AssignmentService(EmployeeShiftAssignmentRepository repo, EmployeeRepository empRepo, ShiftRepository shiftRepo) {
    this.repo = repo; this.empRepo = empRepo; this.shiftRepo = shiftRepo;
  }

  public List<EmployeeShiftAssignment> listByEmpCode(String empCode) {
    return repo.findByEmployee_EmpCode(empCode);
  }

  @Transactional
  public List<EmployeeShiftAssignment> bulkAssign(List<Map<String,Object>> payload) {
    List<EmployeeShiftAssignment> result = new ArrayList<>();
    for (Map<String,Object> m : payload) {
      String empCode = (String) m.get("empCode");
      String shiftCode = (String) m.get("shiftCode");
      String patternType = (String) m.getOrDefault("patternType","NONE");
      String patternJson = (String) m.getOrDefault("patternJson", null);
      LocalDate start = LocalDate.parse((String) m.get("startDate"));
      LocalDate end = LocalDate.parse((String) m.get("endDate"));
      boolean primary = (Boolean) m.getOrDefault("primary", Boolean.TRUE);
      String remarks = (String) m.getOrDefault("remarks", null);

      Employee emp = empRepo.findByEmpCode(empCode).orElseThrow(() -> new RuntimeException("Emp not found " + empCode));
      Shift shift = null;
      if (shiftCode != null) {
        shift = shiftRepo.findByCode(shiftCode).orElseThrow(() -> new RuntimeException("Shift not found " + shiftCode));
      }

      EmployeeShiftAssignment a = new EmployeeShiftAssignment();
      a.setEmployee(emp);
      a.setShift(shift);
      a.setPatternType(PatternType.valueOf(patternType));
      a.setPatternJson(patternJson);
      a.setStartDate(start);
      a.setEndDate(end);
      a.setPrimaryAssignment(primary);
      a.setRemarks(remarks);
      result.add(a);
    }
    return repo.saveAll(result);
  }

    public List<EmployeeShiftAssignment> getAll() {
        return repo.findAll();
    }

    public List<EmployeeShiftAssignment> listByShiftCode(String shiftCode) {
        List<EmployeeShiftAssignment> result = repo.findByShift_Code(shiftCode);
        return result;

    }
}
