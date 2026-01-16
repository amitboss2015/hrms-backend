package com.example.hrms.leave.dto;

import java.math.BigDecimal;

public record MarkLeavePreview(BigDecimal willConsumeMonthly, BigDecimal willConsumeAnnual,
                               BigDecimal monthlyBalanceAfter, BigDecimal annualBalanceAfter,
                               boolean wouldConvertToUnpaid, BigDecimal unpaidDays) {}
