package com.example.hrms.leave.service;

import com.example.hrms.leave.dto.EmployeeLeaveRow;
import com.example.hrms.leave.dto.MarkLeaveRequest;
import com.example.hrms.leave.dto.MarkLeavePreview;
import com.example.hrms.leave.domain.EmployeeLeave;
import java.util.List;
import java.util.Map;

public interface LeaveAdminService {
  MarkLeavePreview preview(MarkLeaveRequest req);
  EmployeeLeave mark(MarkLeaveRequest req);
  void cancel(Long leaveId, String reason);
  EmployeeLeave approveLeave(Long leaveId, String remarks);
  EmployeeLeave rejectLeave(Long leaveId, String remarks);
  EmployeeLeave updatePayableFlag(Long leaveId, Boolean payable);
  Map<String, Boolean> checkLeaveActions(Long leaveId);
  Map<String, Object> deleteAllLeaves(String orgId, String empId);
    List<EmployeeLeave> listLeaves(String empId, String orgId,
        Integer year, Integer month,
        String from, String to);
    List<EmployeeLeaveRow> listLeaves(String empId, String orgId);
}
