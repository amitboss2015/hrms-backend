package com.example.hrms.leave.dto;

import com.example.hrms.leave.domain.enums.LockState;
import java.math.BigDecimal;

public record LedgerView(Integer year, Integer month,
                         BigDecimal openingMonthly, BigDecimal accruedMonthly,
                         BigDecimal usedFromMonthly, BigDecimal movedToAnnual,
                         BigDecimal expiredMonthly, BigDecimal closingMonthly,
                         LockState lockState) {}
