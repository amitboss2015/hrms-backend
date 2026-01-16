package com.example.hrms.leave.dto;

import com.example.hrms.leave.domain.enums.*;
import java.math.BigDecimal;

public record LeaveTypeDto(Long id, String orgId, String code, String name, boolean isPaid,
                           AccrualMode accrualMode, BigDecimal monthlyQuotaDays,
                           MonthlyCfBehavior monthlyCfBehavior, BigDecimal monthlyCfCapDays,
                           BigDecimal annualAllocationDays, Boolean annualCfAllowed,
                           BigDecimal annualCfCapDays, ConsumeOrder consumeOrder,
                           Boolean excludeWeeklyOffs, Boolean excludeHolidays, MinUnit minUnit,
                           Boolean active) {}
