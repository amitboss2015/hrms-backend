package com.example.hrms.leave.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "employee_leave_allocation",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id","empId","leave_type_id","leaveYear"}),
       indexes = @Index(name = "idx_alloc_tenant", columnList = "tenant_id"))
public class EmployeeLeaveAllocation {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false, length = 50)
  private String tenantId;
  
  @Column(name = "org_id", nullable = false, length = 255)
  private String orgId;
  
  @Column(nullable = false) private String empId;

  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "leave_type_id", nullable = false)
  private LeaveType leaveType;

  @Column(nullable = false) private Integer leaveYear;

  @Column(nullable = false) private BigDecimal openingAnnual = BigDecimal.ZERO;
  @Column(nullable = false) private BigDecimal accruedAnnual = BigDecimal.ZERO;
  @Column(nullable = false) private BigDecimal usedFromAnnual = BigDecimal.ZERO;
  @Column(nullable = false) private BigDecimal carriedForwardOut = BigDecimal.ZERO;
  @Column(nullable = false) private BigDecimal expiredAnnual = BigDecimal.ZERO;
  @Column(nullable = false) private BigDecimal closingAnnual = BigDecimal.ZERO;

  private Boolean yearLocked = Boolean.FALSE;
  @Lob private String auditJson;

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
  public Integer getLeaveYear() { return leaveYear; }
  public void setLeaveYear(Integer leaveYear) { this.leaveYear = leaveYear; }
  public BigDecimal getOpeningAnnual() { return openingAnnual; }
  public void setOpeningAnnual(BigDecimal openingAnnual) { this.openingAnnual = openingAnnual; }
  public BigDecimal getAccruedAnnual() { return accruedAnnual; }
  public void setAccruedAnnual(BigDecimal accruedAnnual) { this.accruedAnnual = accruedAnnual; }
  public BigDecimal getUsedFromAnnual() { return usedFromAnnual; }
  public void setUsedFromAnnual(BigDecimal usedFromAnnual) { this.usedFromAnnual = usedFromAnnual; }
  public BigDecimal getCarriedForwardOut() { return carriedForwardOut; }
  public void setCarriedForwardOut(BigDecimal carriedForwardOut) { this.carriedForwardOut = carriedForwardOut; }
  public BigDecimal getExpiredAnnual() { return expiredAnnual; }
  public void setExpiredAnnual(BigDecimal expiredAnnual) { this.expiredAnnual = expiredAnnual; }
  public BigDecimal getClosingAnnual() { return closingAnnual; }
  public void setClosingAnnual(BigDecimal closingAnnual) { this.closingAnnual = closingAnnual; }
  public Boolean getYearLocked() { return yearLocked; }
  public void setYearLocked(Boolean yearLocked) { this.yearLocked = yearLocked; }
  public String getAuditJson() { return auditJson; }
  public void setAuditJson(String auditJson) { this.auditJson = auditJson; }
}
