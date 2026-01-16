package com.example.hrms.leave.service;

import com.example.hrms.leave.dto.EmployeeLeaveRow;
import com.example.hrms.leave.dto.MarkLeaveRequest;
import com.example.hrms.leave.dto.MarkLeavePreview;
import com.example.hrms.leave.domain.EmployeeLeave;
import java.util.List;

public interface LeaveAdminService {
  MarkLeavePreview preview(MarkLeaveRequest req);
  EmployeeLeave mark(MarkLeaveRequest req);
  void cancel(Long leaveId, String reason);
    List<EmployeeLeave> listLeaves(String empId, String orgId,
        Integer year, Integer month,
        String from, String to);
    List<EmployeeLeaveRow> listLeaves(String empId, String orgId);
}
