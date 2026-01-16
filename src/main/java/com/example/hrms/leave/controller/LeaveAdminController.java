package com.example.hrms.leave.controller;

import com.example.hrms.leave.dto.EmployeeLeaveRow;
import com.example.hrms.leave.dto.MarkLeaveRequest;
import com.example.hrms.leave.dto.MarkLeavePreview;
import com.example.hrms.leave.domain.EmployeeLeave;
import com.example.hrms.leave.service.LeaveAdminService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leave/admin")
@CrossOrigin(origins = "*")
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

    // src/main/java/com/example/hrms/leave/controller/LeaveAdminController.java
    @GetMapping("/employee/{empId}/leaves")
    public List<EmployeeLeaveRow> listEmployeeLeaves(@PathVariable String empId, @RequestParam String orgId) {
        return service.listLeaves(empId, orgId);
    }

}
