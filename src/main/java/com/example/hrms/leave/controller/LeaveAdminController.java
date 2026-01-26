package com.example.hrms.leave.controller;

import com.example.hrms.leave.dto.EmployeeLeaveRow;
import com.example.hrms.leave.dto.MarkLeaveRequest;
import com.example.hrms.leave.dto.MarkLeavePreview;
import com.example.hrms.leave.domain.EmployeeLeave;
import com.example.hrms.leave.service.LeaveAdminService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leave/admin")
public class LeaveAdminController {
  private final LeaveAdminService service;
  public LeaveAdminController(LeaveAdminService service){ this.service = service; }

  @PostMapping("/mark")
  public Object mark(@RequestBody MarkLeaveRequest req){
    if (req.previewOnly()) return service.preview(req);
    return service.mark(req);
  }

  @DeleteMapping("/{leaveId}")
  public void cancel(@PathVariable Long leaveId, @RequestParam(required=false) String reason){
    service.cancel(leaveId, reason);
  }

  @PutMapping("/{leaveId}/approve")
  public EmployeeLeave approveLeave(@PathVariable Long leaveId, @RequestParam(required=false) String remarks) {
    return service.approveLeave(leaveId, remarks);
  }

  @PutMapping("/{leaveId}/reject")
  public EmployeeLeave rejectLeave(@PathVariable Long leaveId, @RequestParam(required=false) String remarks) {
    return service.rejectLeave(leaveId, remarks);
  }

  @PutMapping("/{leaveId}/payable")
  public EmployeeLeave updatePayableFlag(@PathVariable Long leaveId, @RequestParam Boolean payable) {
    return service.updatePayableFlag(leaveId, payable);
  }

  @GetMapping("/{leaveId}/actions")
  public Map<String, Boolean> checkLeaveActions(@PathVariable Long leaveId) {
    return service.checkLeaveActions(leaveId);
  }

  @DeleteMapping("/bulk")
  public Map<String, Object> deleteAllLeaves(@RequestParam String orgId, @RequestParam(required = false) String empId) {
    return service.deleteAllLeaves(orgId, empId);
  }

    // src/main/java/com/example/hrms/leave/controller/LeaveAdminController.java
    @GetMapping("/employee/{empId}/leaves")
    public List<EmployeeLeaveRow> listEmployeeLeaves(@PathVariable String empId, @RequestParam String orgId) {
        return service.listLeaves(empId, orgId);
    }

}
