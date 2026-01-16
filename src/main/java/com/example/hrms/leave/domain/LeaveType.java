package com.example.hrms.leave.domain;

import com.example.hrms.leave.domain.enums.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.math.BigDecimal;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "leave_type", uniqueConstraints = @UniqueConstraint(columnNames = {"orgId", "code"}))
public class LeaveType {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false) private String orgId;
  @Column(nullable = false) private String code; // EL, CL, SL, UCL, WEEKLY_OFF
  @Column(nullable = false) private String name;
  @Column(nullable = false) private Boolean isPaid = Boolean.TRUE;

  @Enumerated(EnumType.STRING) @Column(nullable = false)
  private AccrualMode accrualMode = AccrualMode.ANNUAL;

  // Monthly knobs
  private BigDecimal monthlyQuotaDays;
  @Enumerated(EnumType.STRING) private MonthlyCfBehavior monthlyCfBehavior;
  private BigDecimal monthlyCfCapDays;

  // Annual knobs
  private BigDecimal annualAllocationDays;
  private Boolean annualCfAllowed;
  private BigDecimal annualCfCapDays;

  @Enumerated(EnumType.STRING)
  private ConsumeOrder consumeOrder = ConsumeOrder.MONTHLY_THEN_ANNUAL;

  private Boolean excludeWeeklyOffs = Boolean.TRUE;
  private Boolean excludeHolidays = Boolean.TRUE;
  @Enumerated(EnumType.STRING) private MinUnit minUnit = MinUnit.DAY;

  private Boolean active = Boolean.TRUE;

  // Getters/Setters
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getOrgId() { return orgId; }
  public void setOrgId(String orgId) { this.orgId = orgId; }
  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public Boolean getIsPaid() { return Boolean.TRUE.equals(this.isPaid); }
  public void setIsPaid(Boolean isPaid) { this.isPaid = isPaid; }
  public AccrualMode getAccrualMode() { return accrualMode; }
  public void setAccrualMode(AccrualMode accrualMode) { this.accrualMode = accrualMode; }
  public BigDecimal getMonthlyQuotaDays() { return monthlyQuotaDays; }
  public void setMonthlyQuotaDays(BigDecimal monthlyQuotaDays) { this.monthlyQuotaDays = monthlyQuotaDays; }
  public MonthlyCfBehavior getMonthlyCfBehavior() { return monthlyCfBehavior; }
  public void setMonthlyCfBehavior(MonthlyCfBehavior monthlyCfBehavior) { this.monthlyCfBehavior = monthlyCfBehavior; }
  public BigDecimal getMonthlyCfCapDays() { return monthlyCfCapDays; }
  public void setMonthlyCfCapDays(BigDecimal monthlyCfCapDays) { this.monthlyCfCapDays = monthlyCfCapDays; }
  public BigDecimal getAnnualAllocationDays() { return annualAllocationDays; }
  public void setAnnualAllocationDays(BigDecimal annualAllocationDays) { this.annualAllocationDays = annualAllocationDays; }
  public Boolean getAnnualCfAllowed() { return annualCfAllowed; }
  public void setAnnualCfAllowed(Boolean annualCfAllowed) { this.annualCfAllowed = annualCfAllowed; }
  public BigDecimal getAnnualCfCapDays() { return annualCfCapDays; }
  public void setAnnualCfCapDays(BigDecimal annualCfCapDays) { this.annualCfCapDays = annualCfCapDays; }
  public ConsumeOrder getConsumeOrder() { return consumeOrder; }
  public void setConsumeOrder(ConsumeOrder consumeOrder) { this.consumeOrder = consumeOrder; }
  public Boolean getExcludeWeeklyOffs() { return excludeWeeklyOffs; }
  public void setExcludeWeeklyOffs(Boolean excludeWeeklyOffs) { this.excludeWeeklyOffs = excludeWeeklyOffs; }
  public Boolean getExcludeHolidays() { return excludeHolidays; }
  public void setExcludeHolidays(Boolean excludeHolidays) { this.excludeHolidays = excludeHolidays; }
  public MinUnit getMinUnit() { return minUnit; }
  public void setMinUnit(MinUnit minUnit) { this.minUnit = minUnit; }
  public Boolean getActive() { return active; }
  public void setActive(Boolean active) { this.active = active; }
}
