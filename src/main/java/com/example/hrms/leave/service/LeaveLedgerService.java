package com.example.hrms.leave.service;

import com.example.hrms.leave.dto.LedgerView;
import com.example.hrms.leave.domain.enums.ConsumedFrom;
import java.math.BigDecimal;
import java.util.Map;

public interface LeaveLedgerService {
  LedgerView getMonthlyLedger(String orgId, String empId, Long leaveTypeId, int year, int month);
  Map<String,Object> getBalances(String orgId, String empId, int year);
  void accrueAtMonthStart(String orgId, String empId, Long leaveTypeId, int year, int month);
  void applyConsumption(String orgId, String empId, Long leaveTypeId, int year, int month, BigDecimal days, ConsumedFrom from);
}
