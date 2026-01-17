package com.example.hrms.domain;

import com.example.hrms.domain.enums.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "employees", 
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_emp_code", columnNames = {"tenant_id", "emp_code"})
    },
    indexes = {
        @Index(name = "idx_emp_tenant", columnList = "tenantId"),
        @Index(name = "idx_tenant_status", columnList = "tenantId, status")
    }
)
public class Employee {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Multi-tenancy support - no default, must be set explicitly from TenantContext
    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @NotBlank(message = "Employee code is required")
    @Size(min = 1, max = 20, message = "Employee code must be between 1 and 20 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Employee code can only contain letters, numbers, underscores and hyphens")
    @Column(name = "emp_code", nullable = false, length = 20)
    private String empCode;

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    @Pattern(regexp = "^[A-Za-z\\s.'-]+$", message = "First name can only contain letters, spaces, dots, apostrophes and hyphens")
    @Column(length = 50)
    private String firstName;

    @Size(max = 50, message = "Last name must be less than 50 characters")
    @Pattern(regexp = "^[A-Za-z\\s.'-]*$", message = "Last name can only contain letters, spaces, dots, apostrophes and hyphens")
    @Column(length = 50)
    private String lastName;

    @Enumerated(EnumType.STRING)
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Size(max = 50, message = "Department must be less than 50 characters")
    @Column(length = 50)
    private String department;

    @Size(max = 50, message = "Designation must be less than 50 characters")
    @Column(length = 50)
    private String designation;

    @Enumerated(EnumType.STRING)
    private SalaryBasis salaryBasis = SalaryBasis.MONTHLY;

    @DecimalMin(value = "0.0", message = "Base salary cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Base salary must be a valid amount")
    private BigDecimal baseSalary;

    @DecimalMin(value = "0.0", message = "Increment cannot be negative")
    private BigDecimal increment;

    @DecimalMin(value = "0.0", message = "Hourly rate cannot be negative")
    private BigDecimal hourlyRate;

    @PastOrPresent(message = "Join date cannot be in the future")
    private LocalDate joinDate;

    @Enumerated(EnumType.STRING)
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    // Contact & address
    @Email(message = "Email must be a valid email address")
    @Size(max = 100, message = "Email must be less than 100 characters")
    @Column(length = 100)
    private String email;

    @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Phone must be a valid 10-digit Indian mobile number starting with 6-9")
    @Column(length = 15)
    private String phone;

    @Size(max = 1024, message = "Address must be less than 1024 characters")
    @Column(length = 1024)
    private String address;

    @Size(max = 50, message = "City must be less than 50 characters")
    @Column(length = 50)
    private String city;

    @Size(max = 50, message = "State must be less than 50 characters")
    @Column(length = 50)
    private String state;

    @Pattern(regexp = "^$|^[1-9][0-9]{5}$", message = "Pincode must be a valid 6-digit Indian pincode")
    @Column(length = 10)
    private String pincode;

    // KYC/bank
    @Pattern(regexp = "^$|^[2-9]{1}[0-9]{11}$", message = "Aadhaar must be a valid 12-digit number")
    @Column(length = 20)
    private String aadhaar;

    @Pattern(regexp = "^$|^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "PAN must be in valid format (e.g., ABCDE1234F)")
    @Column(length = 15)
    private String pan;

    @Size(max = 20, message = "Bank account must be less than 20 characters")
    @Column(length = 20)
    private String bankAccount;

    @Pattern(regexp = "^$|^[A-Z]{4}0[A-Z0-9]{6}$", message = "IFSC must be in valid format (e.g., SBIN0001234)")
    @Column(length = 15)
    private String ifsc;

    @Pattern(regexp = "^$|^[0-9]{12}$", message = "UAN must be a valid 12-digit number")
    @Column(length = 15)
    private String uanNumber;

    @Pattern(regexp = "^$|^[0-9]{17}$", message = "ESIC number must be a valid 17-digit number")
    @Column(length = 20)
    private String esicNumber;

    @Size(max = 50, message = "Bank name must be less than 50 characters")
    @Column(length = 50)
    private String bankName;

    @Size(max = 50, message = "Branch name must be less than 50 characters")
    @Column(length = 50)
    private String branchName;

    // Emergency contact
    @Size(max = 100, message = "Emergency contact name must be less than 100 characters")
    @Column(length = 100)
    private String emergencyContactName;

    @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Emergency contact phone must be a valid 10-digit Indian mobile number")
    @Column(length = 15)
    private String emergencyContactPhone;

    // OT
    private boolean otAllowed = false;
    private Integer otDurationMinutes;

    // Salary components (for payroll calculation)
    @DecimalMin(value = "0.0", message = "HRA percent cannot be negative")
    @DecimalMax(value = "100.0", message = "HRA percent cannot exceed 100")
    private BigDecimal hraPercent;

    @DecimalMin(value = "0.0", message = "DA percent cannot be negative")
    @DecimalMax(value = "100.0", message = "DA percent cannot exceed 100")
    private BigDecimal daPercent;

    @DecimalMin(value = "0.0", message = "Conveyance allowance cannot be negative")
    private BigDecimal conveyanceAllowance;

    @DecimalMin(value = "0.0", message = "Medical allowance cannot be negative")
    private BigDecimal medicalAllowance;

    @DecimalMin(value = "0.0", message = "Special allowance cannot be negative")
    private BigDecimal specialAllowance;

    @DecimalMin(value = "0.0", message = "Other allowance cannot be negative")
    private BigDecimal otherAllowance;

    // Deduction settings
    private Boolean epfApplicable = true;
    private Boolean esicApplicable = true;
    private Boolean ptApplicable = true;
    private Boolean tdsApplicable = false;

    // Weekly off configuration
    private String weeklyOffDays;

    // Working hours
    private Integer standardWorkingHoursPerDay = 8;
    private Integer workingDaysPerMonth = 26;

    public Employee() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getEmpCode() { return empCode; }
    public void setEmpCode(String empCode) { this.empCode = empCode != null ? empCode.trim() : null; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName != null ? firstName.trim() : null; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName != null ? lastName.trim() : null; }

    public EmploymentType getEmploymentType() { return employmentType; }
    public void setEmploymentType(EmploymentType employmentType) { this.employmentType = employmentType; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department != null ? department.trim() : null; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation != null ? designation.trim() : null; }

    public SalaryBasis getSalaryBasis() { return salaryBasis; }
    public void setSalaryBasis(SalaryBasis salaryBasis) { this.salaryBasis = salaryBasis; }

    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }

    public BigDecimal getIncrement() { return increment; }
    public void setIncrement(BigDecimal increment) { this.increment = increment; }

    public BigDecimal getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(BigDecimal hourlyRate) { this.hourlyRate = hourlyRate; }

    public LocalDate getJoinDate() { return joinDate; }
    public void setJoinDate(LocalDate joinDate) { this.joinDate = joinDate; }

    public EmployeeStatus getStatus() { return status; }
    public void setStatus(EmployeeStatus status) { this.status = status; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email != null ? email.trim().toLowerCase() : null; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { 
        if (phone != null) {
            phone = phone.replaceAll("[\\s-]", "").trim();
        }
        this.phone = phone; 
    }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address != null ? address.trim() : null; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city != null ? city.trim() : null; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state != null ? state.trim() : null; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode != null ? pincode.trim() : null; }

    public String getAadhaar() { return aadhaar; }
    public void setAadhaar(String aadhaar) { 
        if (aadhaar != null) {
            aadhaar = aadhaar.replaceAll("\\s", "").trim();
        }
        this.aadhaar = aadhaar; 
    }

    public String getPan() { return pan; }
    public void setPan(String pan) { this.pan = pan != null ? pan.trim().toUpperCase() : null; }

    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount != null ? bankAccount.trim() : null; }

    public String getIfsc() { return ifsc; }
    public void setIfsc(String ifsc) { this.ifsc = ifsc != null ? ifsc.trim().toUpperCase() : null; }

    public String getUanNumber() { return uanNumber; }
    public void setUanNumber(String uanNumber) { this.uanNumber = uanNumber != null ? uanNumber.trim() : null; }

    public String getEsicNumber() { return esicNumber; }
    public void setEsicNumber(String esicNumber) { this.esicNumber = esicNumber != null ? esicNumber.trim() : null; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName != null ? bankName.trim() : null; }

    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName != null ? branchName.trim() : null; }

    public String getEmergencyContactName() { return emergencyContactName; }
    public void setEmergencyContactName(String emergencyContactName) { 
        this.emergencyContactName = emergencyContactName != null ? emergencyContactName.trim() : null; 
    }

    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String emergencyContactPhone) { 
        if (emergencyContactPhone != null) {
            emergencyContactPhone = emergencyContactPhone.replaceAll("[\\s-]", "").trim();
        }
        this.emergencyContactPhone = emergencyContactPhone; 
    }

    public boolean isOtAllowed() { return otAllowed; }
    public void setOtAllowed(boolean otAllowed) { this.otAllowed = otAllowed; }

    public Integer getOtDurationMinutes() { return otDurationMinutes; }
    public void setOtDurationMinutes(Integer otDurationMinutes) { this.otDurationMinutes = otDurationMinutes; }

    public BigDecimal getHraPercent() { return hraPercent; }
    public void setHraPercent(BigDecimal hraPercent) { this.hraPercent = hraPercent; }

    public BigDecimal getDaPercent() { return daPercent; }
    public void setDaPercent(BigDecimal daPercent) { this.daPercent = daPercent; }

    public BigDecimal getConveyanceAllowance() { return conveyanceAllowance; }
    public void setConveyanceAllowance(BigDecimal conveyanceAllowance) { this.conveyanceAllowance = conveyanceAllowance; }

    public BigDecimal getMedicalAllowance() { return medicalAllowance; }
    public void setMedicalAllowance(BigDecimal medicalAllowance) { this.medicalAllowance = medicalAllowance; }

    public BigDecimal getSpecialAllowance() { return specialAllowance; }
    public void setSpecialAllowance(BigDecimal specialAllowance) { this.specialAllowance = specialAllowance; }

    public BigDecimal getOtherAllowance() { return otherAllowance; }
    public void setOtherAllowance(BigDecimal otherAllowance) { this.otherAllowance = otherAllowance; }

    public Boolean getEpfApplicable() { return epfApplicable; }
    public void setEpfApplicable(Boolean epfApplicable) { this.epfApplicable = epfApplicable; }

    public Boolean getEsicApplicable() { return esicApplicable; }
    public void setEsicApplicable(Boolean esicApplicable) { this.esicApplicable = esicApplicable; }

    public Boolean getPtApplicable() { return ptApplicable; }
    public void setPtApplicable(Boolean ptApplicable) { this.ptApplicable = ptApplicable; }

    public Boolean getTdsApplicable() { return tdsApplicable; }
    public void setTdsApplicable(Boolean tdsApplicable) { this.tdsApplicable = tdsApplicable; }

    public String getWeeklyOffDays() { return weeklyOffDays; }
    public void setWeeklyOffDays(String weeklyOffDays) { this.weeklyOffDays = weeklyOffDays; }

    public Integer getStandardWorkingHoursPerDay() { return standardWorkingHoursPerDay; }
    public void setStandardWorkingHoursPerDay(Integer standardWorkingHoursPerDay) { this.standardWorkingHoursPerDay = standardWorkingHoursPerDay; }

    public Integer getWorkingDaysPerMonth() { return workingDaysPerMonth; }
    public void setWorkingDaysPerMonth(Integer workingDaysPerMonth) { this.workingDaysPerMonth = workingDaysPerMonth; }
}
