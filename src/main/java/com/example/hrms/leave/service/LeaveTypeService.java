package com.example.hrms.leave.service;

import com.example.hrms.leave.domain.LeaveType;
import java.util.List;

public interface LeaveTypeService {
  List<LeaveType> list(String orgId);
  LeaveType create(LeaveType dto);
  LeaveType update(Long id, LeaveType dto);
  void activate(Long id);
  void deactivate(Long id);
}
