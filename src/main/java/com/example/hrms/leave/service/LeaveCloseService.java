package com.example.hrms.leave.service;

public interface LeaveCloseService {
  void closeMonth(String orgId, int year, int month);
  void closeYear(String orgId, int year);
}
