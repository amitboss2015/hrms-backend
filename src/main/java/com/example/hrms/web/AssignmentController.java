
package com.example.hrms.web;

import com.example.hrms.domain.EmployeeShiftAssignment;
import com.example.hrms.service.AssignmentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employee-shifts")
public class AssignmentController {
  private final AssignmentService service;
  public AssignmentController(AssignmentService service) { this.service = service; }

  @GetMapping
  public List<EmployeeShiftAssignment> listByEmp(@RequestParam String empCode) {
    return service.listByEmpCode(empCode);
  }

  @PostMapping("/bulk")
  public List<EmployeeShiftAssignment> bulkAssign(@RequestBody List<Map<String,Object>> payload){
    return service.bulkAssign(payload);
  }

    @GetMapping("/all")
    public List<EmployeeShiftAssignment> listAll2() {
        return service.getAll();
    }

    @GetMapping("/by-shift/{shiftCode}")
    public List<EmployeeShiftAssignment> listByShift(@PathVariable String shiftCode) {
        List<EmployeeShiftAssignment> result = service.listByShiftCode(shiftCode);
        return result;
    }
}

