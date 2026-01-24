
package com.example.hrms.web;

import com.example.hrms.domain.EmployeeShiftAssignment;
import com.example.hrms.service.AssignmentService;
import org.springframework.http.ResponseEntity;
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
    
    /**
     * Get shift assignment status for all employees.
     * Returns count of employees with/without shift assignments and list of unassigned employees.
     */
    @GetMapping("/assignment-status")
    public ResponseEntity<Map<String, Object>> getShiftAssignmentStatus() {
        return ResponseEntity.ok(service.getShiftAssignmentStatus());
    }
    
    /**
     * Assign default shift to all employees without shift assignments.
     * @param shiftCode The shift code to assign (e.g., "GENERAL")
     */
    @PostMapping("/assign-default/{shiftCode}")
    public ResponseEntity<Map<String, Object>> assignDefaultShiftToAll(@PathVariable String shiftCode) {
        return ResponseEntity.ok(service.assignDefaultShiftToAll(shiftCode));
    }
}

