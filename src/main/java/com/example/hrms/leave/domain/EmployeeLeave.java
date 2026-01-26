package com.example.hrms.leave.domain;

import com.example.hrms.leave.domain.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "employee_leave",
       indexes = @Index(name = "idx_empleave_tenant", columnList = "tenant_id"))
public class EmployeeLeave {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Multi-tenancy support
  @Column(name = "tenant_id", nullable = false, length = 50) 
  private String tenantId;
  
  @Column(name = "org_id", nullable = false, length = 255)
  private String orgId;
  @Column(nullable = false) private String empId;

  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "leave_type_id", nullable = false)
  private LeaveType leaveType;

  @Column(nullable = false) private LocalDate startDate;
  @Column(nullable = false) private LocalDate endDate;

  @Enumerated(EnumType.STRING) @Column(nullable = false)
  private DurationKind durationKind = DurationKind.FULL_DAY;

  @Column(nullable = false) private BigDecimal totalDays = BigDecimal.ONE;

  @Enumerated(EnumType.STRING) @Column(nullable = false)
  private LeaveStatus status = LeaveStatus.REVIEW;

  private Boolean payable = Boolean.TRUE;
  private Boolean consumesBalance = Boolean.TRUE;
  @Enumerated(EnumType.STRING) private ConsumedFrom consumedFrom = ConsumedFrom.MIXED;
  private Integer consumedFromYear;
  @Lob private String consumptionBreakupJson;
  private String remarks;

  // Getters/Setters
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getTenantId() { return tenantId; }
  public void setTenantId(String tenantId) { this.tenantId = tenantId; }
  // Org ID field (for database compatibility)
  public String getOrgId() { return orgId; }
  public void setOrgId(String orgId) { 
    this.orgId = orgId; 
    this.tenantId = orgId; // Keep both in sync
  }
  public String getEmpId() { return empId; }
  public void setEmpId(String empId) { this.empId = empId; }
  public LeaveType getLeaveType() { return leaveType; }
  public void setLeaveType(LeaveType leaveType) { this.leaveType = leaveType; }
  public LocalDate getStartDate() { return startDate; }
  public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
  public LocalDate getEndDate() { return endDate; }
  public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
  public DurationKind getDurationKind() { return durationKind; }
  public void setDurationKind(DurationKind durationKind) { this.durationKind = durationKind; }
  public BigDecimal getTotalDays() { return totalDays; }
  public void setTotalDays(BigDecimal totalDays) { this.totalDays = totalDays; }
  public LeaveStatus getStatus() { return status; }
  public void setStatus(LeaveStatus status) { this.status = status; }
  public Boolean getPayable() { return payable; }
  public void setPayable(Boolean payable) { this.payable = payable; }
  public Boolean getConsumesBalance() { return consumesBalance; }
  public void setConsumesBalance(Boolean consumesBalance) { this.consumesBalance = consumesBalance; }
  public ConsumedFrom getConsumedFrom() { return consumedFrom; }
  public void setConsumedFrom(ConsumedFrom consumedFrom) { this.consumedFrom = consumedFrom; }
  public Integer getConsumedFromYear() { return consumedFromYear; }
  public void setConsumedFromYear(Integer consumedFromYear) { this.consumedFromYear = consumedFromYear; }
  public String getConsumptionBreakupJson() { return consumptionBreakupJson; }
  public void setConsumptionBreakupJson(String consumptionBreakupJson) { this.consumptionBreakupJson = consumptionBreakupJson; }
  public String getRemarks() { return remarks; }
  public void setRemarks(String remarks) { this.remarks = remarks; }
}
