package com.example.hrms.leave.domain;

import com.example.hrms.leave.domain.enums.LockState;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "leave_ledger",
       uniqueConstraints = @UniqueConstraint(columnNames = {"orgId","empId","leave_type_id","leaveYear","leaveMonth"}))
public class LeaveLedger {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false) private String orgId;
  @Column(nullable = false) private String empId;

  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "leave_type_id", nullable = false)
  private LeaveType leaveType;

  @Column(nullable = false) private Integer leaveYear;
  @Column(nullable = false) private Integer leaveMonth; // 1-12

  @Column(nullable = false) private BigDecimal openingMonthly = BigDecimal.ZERO;
  @Column(nullable = false) private BigDecimal accruedMonthly = BigDecimal.ZERO;
  @Column(nullable = false) private BigDecimal usedFromMonthly = BigDecimal.ZERO;
  @Column(nullable = false) private BigDecimal movedToAnnual = BigDecimal.ZERO;
  @Column(nullable = false) private BigDecimal expiredMonthly = BigDecimal.ZERO;
  @Column(nullable = false) private BigDecimal closingMonthly = BigDecimal.ZERO;

  @Enumerated(EnumType.STRING) @Column(nullable = false)
  private LockState lockState = LockState.OPEN;

  @Lob private String auditJson;

  // Getters/Setters
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getOrgId() { return orgId; }
  public void setOrgId(String orgId) { this.orgId = orgId; }
  public String getEmpId() { return empId; }
  public void setEmpId(String empId) { this.empId = empId; }
  public LeaveType getLeaveType() { return leaveType; }
  public void setLeaveType(LeaveType leaveType) { this.leaveType = leaveType; }
  public Integer getLeaveYear() { return leaveYear; }
  public void setLeaveYear(Integer leaveYear) { this.leaveYear = leaveYear; }
  public Integer getLeaveMonth() { return leaveMonth; }
  public void setLeaveMonth(Integer leaveMonth) { this.leaveMonth = leaveMonth; }
  public BigDecimal getOpeningMonthly() { return openingMonthly; }
  public void setOpeningMonthly(BigDecimal openingMonthly) { this.openingMonthly = openingMonthly; }
  public BigDecimal getAccruedMonthly() { return accruedMonthly; }
  public void setAccruedMonthly(BigDecimal accruedMonthly) { this.accruedMonthly = accruedMonthly; }
  public BigDecimal getUsedFromMonthly() { return usedFromMonthly; }
  public void setUsedFromMonthly(BigDecimal usedFromMonthly) { this.usedFromMonthly = usedFromMonthly; }
  public BigDecimal getMovedToAnnual() { return movedToAnnual; }
  public void setMovedToAnnual(BigDecimal movedToAnnual) { this.movedToAnnual = movedToAnnual; }
  public BigDecimal getExpiredMonthly() { return expiredMonthly; }
  public void setExpiredMonthly(BigDecimal expiredMonthly) { this.expiredMonthly = expiredMonthly; }
  public BigDecimal getClosingMonthly() { return closingMonthly; }
  public void setClosingMonthly(BigDecimal closingMonthly) { this.closingMonthly = closingMonthly; }
  public LockState getLockState() { return lockState; }
  public void setLockState(LockState lockState) { this.lockState = lockState; }
  public String getAuditJson() { return auditJson; }
  public void setAuditJson(String auditJson) { this.auditJson = auditJson; }
}
