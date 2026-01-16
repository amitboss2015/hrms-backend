package com.example.hrms.domain;

import com.example.hrms.domain.enums.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "employees", indexes = {
    @Index(name = "idx_emp_code", columnList = "empCode"),
    @Index(name = "idx_tenant_emp", columnList = "tenantId, empCode", unique = true),
    @Index(name = "idx_tenant_status", columnList = "tenantId, status")
})
public class Employee {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Multi-tenancy support
    @Column(nullable = false, length = 50)
    private String tenantId = "ORG001";

    @NotBlank @Column(nullable = false)
    private String empCode;

    private String firstName;
    private String lastName;

    @Enumerated(EnumType.STRING)
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    private String department;
    private String designation;

    @Enumerated(EnumType.STRING)
    private SalaryBasis salaryBasis = SalaryBasis.MONTHLY;

    private BigDecimal baseSalary;
    private BigDecimal increment;         // Monthly increment amount
    private BigDecimal hourlyRate;
    private LocalDate joinDate;

    @Enumerated(EnumType.STRING)
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    // contacts & address
    private String email, phone;
    @Column(length = 1024) private String address;
    private String city, state, pincode;

    // KYC/bank
    private String aadhaar, pan, bankAccount, ifsc, uanNumber, esicNumber;
    private String bankName, branchName;

    // emergency
    private String emergencyContactName, emergencyContactPhone;

    // OT
    private boolean otAllowed = false;
    private Integer otDurationMinutes;

    // Salary components (for payroll calculation)
    private BigDecimal hraPercent;           // HRA as % of basic (e.g., 40 or 50)
    private BigDecimal daPercent;            // DA as % of basic
    private BigDecimal conveyanceAllowance;
    private BigDecimal medicalAllowance;
    private BigDecimal specialAllowance;
    private BigDecimal otherAllowance;

    // Deduction settings
    private Boolean epfApplicable = true;    // 12% of basic
    private Boolean esicApplicable = true;   // 0.75% of gross (if gross <= 21000)
    private Boolean ptApplicable = true;     // Professional Tax
    private Boolean tdsApplicable = false;   // TDS applicable

    // Weekly off configuration (can override org-level)
    private String weeklyOffDays;            // Comma-separated: "SUNDAY" or "SATURDAY,SUNDAY"

    // Working hours per day (for part-time calculation)
    private Integer standardWorkingHoursPerDay = 8;
    private Integer workingDaysPerMonth = 26;

    public Employee() {}

    // getters/setters
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getTenantId() { return tenantId; } public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getEmpCode() { return empCode; } public void setEmpCode(String empCode) { this.empCode = empCode; }
    public String getFirstName() { return firstName; } public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; } public void setLastName(String lastName) { this.lastName = lastName; }
    public EmploymentType getEmploymentType() { return employmentType; } public void setEmploymentType(EmploymentType employmentType) { this.employmentType = employmentType; }
    public String getDepartment() { return department; } public void setDepartment(String department) { this.department = department; }
    public String getDesignation() { return designation; } public void setDesignation(String designation) { this.designation = designation; }
    public SalaryBasis getSalaryBasis() { return salaryBasis; } public void setSalaryBasis(SalaryBasis salaryBasis) { this.salaryBasis = salaryBasis; }
    public BigDecimal getBaseSalary() { return baseSalary; } public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }
    public BigDecimal getIncrement() { return increment; } public void setIncrement(BigDecimal increment) { this.increment = increment; }
    public BigDecimal getHourlyRate() { return hourlyRate; } public void setHourlyRate(BigDecimal hourlyRate) { this.hourlyRate = hourlyRate; }
    public LocalDate getJoinDate() { return joinDate; } public void setJoinDate(LocalDate joinDate) { this.joinDate = joinDate; }
    public EmployeeStatus getStatus() { return status; } public void setStatus(EmployeeStatus status) { this.status = status; }
    public String getEmail() { return email; } public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; } public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; } public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; } public void setCity(String city) { this.city = city; }
    public String getState() { return state; } public void setState(String state) { this.state = state; }
    public String getPincode() { return pincode; } public void setPincode(String pincode) { this.pincode = pincode; }
    public String getAadhaar() { return aadhaar; } public void setAadhaar(String aadhaar) { this.aadhaar = aadhaar; }
    public String getPan() { return pan; } public void setPan(String pan) { this.pan = pan; }
    public String getBankAccount() { return bankAccount; } public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }
    public String getIfsc() { return ifsc; } public void setIfsc(String ifsc) { this.ifsc = ifsc; }
    public String getEmergencyContactName() { return emergencyContactName; } public void setEmergencyContactName(String emergencyContactName) { this.emergencyContactName = emergencyContactName; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; } public void setEmergencyContactPhone(String emergencyContactPhone) { this.emergencyContactPhone = emergencyContactPhone; }
    public boolean isOtAllowed() { return otAllowed; } public void setOtAllowed(boolean otAllowed) { this.otAllowed = otAllowed; }
    public Integer getOtDurationMinutes() { return otDurationMinutes; } public void setOtDurationMinutes(Integer otDurationMinutes) { this.otDurationMinutes = otDurationMinutes; }

    // New getters/setters
    public String getUanNumber() { return uanNumber; } public void setUanNumber(String uanNumber) { this.uanNumber = uanNumber; }
    public String getEsicNumber() { return esicNumber; } public void setEsicNumber(String esicNumber) { this.esicNumber = esicNumber; }
    public String getBankName() { return bankName; } public void setBankName(String bankName) { this.bankName = bankName; }
    public String getBranchName() { return branchName; } public void setBranchName(String branchName) { this.branchName = branchName; }
    public BigDecimal getHraPercent() { return hraPercent; } public void setHraPercent(BigDecimal hraPercent) { this.hraPercent = hraPercent; }
    public BigDecimal getDaPercent() { return daPercent; } public void setDaPercent(BigDecimal daPercent) { this.daPercent = daPercent; }
    public BigDecimal getConveyanceAllowance() { return conveyanceAllowance; } public void setConveyanceAllowance(BigDecimal conveyanceAllowance) { this.conveyanceAllowance = conveyanceAllowance; }
    public BigDecimal getMedicalAllowance() { return medicalAllowance; } public void setMedicalAllowance(BigDecimal medicalAllowance) { this.medicalAllowance = medicalAllowance; }
    public BigDecimal getSpecialAllowance() { return specialAllowance; } public void setSpecialAllowance(BigDecimal specialAllowance) { this.specialAllowance = specialAllowance; }
    public BigDecimal getOtherAllowance() { return otherAllowance; } public void setOtherAllowance(BigDecimal otherAllowance) { this.otherAllowance = otherAllowance; }
    public Boolean getEpfApplicable() { return epfApplicable; } public void setEpfApplicable(Boolean epfApplicable) { this.epfApplicable = epfApplicable; }
    public Boolean getEsicApplicable() { return esicApplicable; } public void setEsicApplicable(Boolean esicApplicable) { this.esicApplicable = esicApplicable; }
    public Boolean getPtApplicable() { return ptApplicable; } public void setPtApplicable(Boolean ptApplicable) { this.ptApplicable = ptApplicable; }
    public Boolean getTdsApplicable() { return tdsApplicable; } public void setTdsApplicable(Boolean tdsApplicable) { this.tdsApplicable = tdsApplicable; }
    public String getWeeklyOffDays() { return weeklyOffDays; } public void setWeeklyOffDays(String weeklyOffDays) { this.weeklyOffDays = weeklyOffDays; }
    public Integer getStandardWorkingHoursPerDay() { return standardWorkingHoursPerDay; } public void setStandardWorkingHoursPerDay(Integer standardWorkingHoursPerDay) { this.standardWorkingHoursPerDay = standardWorkingHoursPerDay; }
    public Integer getWorkingDaysPerMonth() { return workingDaysPerMonth; } public void setWorkingDaysPerMonth(Integer workingDaysPerMonth) { this.workingDaysPerMonth = workingDaysPerMonth; }
}
