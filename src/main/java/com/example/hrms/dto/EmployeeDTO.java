package com.example.hrms.dto;

import com.example.hrms.domain.Employee;
import com.example.hrms.domain.enums.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Data Transfer Object for Employee with comprehensive validation
 */
public class EmployeeDTO {

    private Long id;

    @NotBlank(message = "Employee code is required")
    @Size(min = 1, max = 20, message = "Employee code must be between 1 and 20 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Employee code can only contain letters, numbers, underscores and hyphens")
    private String empCode;

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    @Pattern(regexp = "^[A-Za-z\\s.'-]+$", message = "First name can only contain letters, spaces, dots, apostrophes and hyphens")
    private String firstName;

    @Size(max = 50, message = "Last name must be less than 50 characters")
    @Pattern(regexp = "^[A-Za-z\\s.'-]*$", message = "Last name can only contain letters, spaces, dots, apostrophes and hyphens")
    private String lastName;

    @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Phone number must be a valid 10-digit Indian mobile number starting with 6-9")
    private String phone;

    @Email(message = "Email must be a valid email address")
    @Size(max = 100, message = "Email must be less than 100 characters")
    private String email;

    @Size(max = 50, message = "Department must be less than 50 characters")
    private String department;

    @Size(max = 50, message = "Designation must be less than 50 characters")
    private String designation;

    private EmploymentType employmentType;
    private SalaryBasis salaryBasis;

    @DecimalMin(value = "0.0", message = "Base salary cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Base salary must be a valid amount with up to 2 decimal places")
    private BigDecimal baseSalary;

    @DecimalMin(value = "0.0", message = "Hourly rate cannot be negative")
    @Digits(integer = 6, fraction = 2, message = "Hourly rate must be a valid amount with up to 2 decimal places")
    private BigDecimal hourlyRate;

    @PastOrPresent(message = "Join date cannot be in the future")
    private LocalDate joinDate;

    private EmployeeStatus status;

    @Size(max = 1024, message = "Address must be less than 1024 characters")
    private String address;

    @Size(max = 50, message = "City must be less than 50 characters")
    private String city;

    @Size(max = 50, message = "State must be less than 50 characters")
    private String state;

    @Pattern(regexp = "^$|^[1-9][0-9]{5}$", message = "Pincode must be a valid 6-digit Indian pincode")
    private String pincode;

    @Pattern(regexp = "^$|^[2-9]{1}[0-9]{3}\\s?[0-9]{4}\\s?[0-9]{4}$", message = "Aadhaar must be a valid 12-digit number")
    private String aadhaar;

    @Pattern(regexp = "^$|^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "PAN must be in valid format (e.g., ABCDE1234F)")
    private String pan;

    @Size(max = 20, message = "Bank account must be less than 20 characters")
    private String bankAccount;

    @Pattern(regexp = "^$|^[A-Z]{4}0[A-Z0-9]{6}$", message = "IFSC must be in valid format (e.g., SBIN0001234)")
    private String ifsc;

    @Size(max = 50, message = "Bank name must be less than 50 characters")
    private String bankName;

    @Size(max = 50, message = "Branch name must be less than 50 characters")
    private String branchName;

    @Pattern(regexp = "^$|^[0-9]{12}$", message = "UAN must be a valid 12-digit number")
    private String uanNumber;

    @Pattern(regexp = "^$|^[0-9]{17}$", message = "ESIC number must be a valid 17-digit number")
    private String esicNumber;

    @Size(max = 100, message = "Emergency contact name must be less than 100 characters")
    private String emergencyContactName;

    @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Emergency contact phone must be a valid 10-digit Indian mobile number")
    private String emergencyContactPhone;

    private boolean otAllowed;
    private Integer otDurationMinutes;

    // Allowances
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

    private Boolean epfApplicable;
    private Boolean esicApplicable;
    private Boolean ptApplicable;
    private Boolean tdsApplicable;

    private String weeklyOffDays;
    private Integer standardWorkingHoursPerDay;
    private Integer workingDaysPerMonth;

    // Default constructor
    public EmployeeDTO() {}

    // Convert from Entity to DTO
    public static EmployeeDTO fromEntity(Employee e) {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setId(e.getId());
        dto.setEmpCode(e.getEmpCode());
        dto.setFirstName(e.getFirstName());
        dto.setLastName(e.getLastName());
        dto.setPhone(e.getPhone());
        dto.setEmail(e.getEmail());
        dto.setDepartment(e.getDepartment());
        dto.setDesignation(e.getDesignation());
        dto.setEmploymentType(e.getEmploymentType());
        dto.setSalaryBasis(e.getSalaryBasis());
        dto.setBaseSalary(e.getBaseSalary());
        dto.setHourlyRate(e.getHourlyRate());
        dto.setJoinDate(e.getJoinDate());
        dto.setStatus(e.getStatus());
        dto.setAddress(e.getAddress());
        dto.setCity(e.getCity());
        dto.setState(e.getState());
        dto.setPincode(e.getPincode());
        dto.setAadhaar(e.getAadhaar());
        dto.setPan(e.getPan());
        dto.setBankAccount(e.getBankAccount());
        dto.setIfsc(e.getIfsc());
        dto.setBankName(e.getBankName());
        dto.setBranchName(e.getBranchName());
        dto.setUanNumber(e.getUanNumber());
        dto.setEsicNumber(e.getEsicNumber());
        dto.setEmergencyContactName(e.getEmergencyContactName());
        dto.setEmergencyContactPhone(e.getEmergencyContactPhone());
        dto.setOtAllowed(e.isOtAllowed());
        dto.setOtDurationMinutes(e.getOtDurationMinutes());
        dto.setHraPercent(e.getHraPercent());
        dto.setDaPercent(e.getDaPercent());
        dto.setConveyanceAllowance(e.getConveyanceAllowance());
        dto.setMedicalAllowance(e.getMedicalAllowance());
        dto.setSpecialAllowance(e.getSpecialAllowance());
        dto.setOtherAllowance(e.getOtherAllowance());
        dto.setEpfApplicable(e.getEpfApplicable());
        dto.setEsicApplicable(e.getEsicApplicable());
        dto.setPtApplicable(e.getPtApplicable());
        dto.setTdsApplicable(e.getTdsApplicable());
        dto.setWeeklyOffDays(e.getWeeklyOffDays());
        dto.setStandardWorkingHoursPerDay(e.getStandardWorkingHoursPerDay());
        dto.setWorkingDaysPerMonth(e.getWorkingDaysPerMonth());
        return dto;
    }

    // Convert DTO to Entity
    public Employee toEntity() {
        Employee e = new Employee();
        e.setId(this.id);
        e.setEmpCode(this.empCode != null ? this.empCode.trim() : null);
        e.setFirstName(this.firstName != null ? this.firstName.trim() : null);
        e.setLastName(this.lastName != null ? this.lastName.trim() : null);
        e.setPhone(this.phone != null ? this.phone.trim() : null);
        e.setEmail(this.email != null ? this.email.trim().toLowerCase() : null);
        e.setDepartment(this.department != null ? this.department.trim() : null);
        e.setDesignation(this.designation != null ? this.designation.trim() : null);
        e.setEmploymentType(this.employmentType != null ? this.employmentType : EmploymentType.FULL_TIME);
        e.setSalaryBasis(this.salaryBasis != null ? this.salaryBasis : SalaryBasis.MONTHLY);
        e.setBaseSalary(this.baseSalary);
        e.setHourlyRate(this.hourlyRate);
        e.setJoinDate(this.joinDate);
        e.setStatus(this.status != null ? this.status : EmployeeStatus.ACTIVE);
        e.setAddress(this.address != null ? this.address.trim() : null);
        e.setCity(this.city != null ? this.city.trim() : null);
        e.setState(this.state != null ? this.state.trim() : null);
        e.setPincode(this.pincode != null ? this.pincode.trim() : null);
        e.setAadhaar(this.aadhaar != null ? this.aadhaar.replaceAll("\\s", "") : null);
        e.setPan(this.pan != null ? this.pan.trim().toUpperCase() : null);
        e.setBankAccount(this.bankAccount != null ? this.bankAccount.trim() : null);
        e.setIfsc(this.ifsc != null ? this.ifsc.trim().toUpperCase() : null);
        e.setBankName(this.bankName != null ? this.bankName.trim() : null);
        e.setBranchName(this.branchName != null ? this.branchName.trim() : null);
        e.setUanNumber(this.uanNumber != null ? this.uanNumber.trim() : null);
        e.setEsicNumber(this.esicNumber != null ? this.esicNumber.trim() : null);
        e.setEmergencyContactName(this.emergencyContactName != null ? this.emergencyContactName.trim() : null);
        e.setEmergencyContactPhone(this.emergencyContactPhone != null ? this.emergencyContactPhone.trim() : null);
        e.setOtAllowed(this.otAllowed);
        e.setOtDurationMinutes(this.otDurationMinutes);
        e.setHraPercent(this.hraPercent);
        e.setDaPercent(this.daPercent);
        e.setConveyanceAllowance(this.conveyanceAllowance);
        e.setMedicalAllowance(this.medicalAllowance);
        e.setSpecialAllowance(this.specialAllowance);
        e.setOtherAllowance(this.otherAllowance);
        e.setEpfApplicable(this.epfApplicable);
        e.setEsicApplicable(this.esicApplicable);
        e.setPtApplicable(this.ptApplicable);
        e.setTdsApplicable(this.tdsApplicable);
        e.setWeeklyOffDays(this.weeklyOffDays);
        e.setStandardWorkingHoursPerDay(this.standardWorkingHoursPerDay);
        e.setWorkingDaysPerMonth(this.workingDaysPerMonth);
        return e;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getEmpCode() { return empCode; }
    public void setEmpCode(String empCode) { this.empCode = empCode; }
    
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    
    public EmploymentType getEmploymentType() { return employmentType; }
    public void setEmploymentType(EmploymentType employmentType) { this.employmentType = employmentType; }
    
    public SalaryBasis getSalaryBasis() { return salaryBasis; }
    public void setSalaryBasis(SalaryBasis salaryBasis) { this.salaryBasis = salaryBasis; }
    
    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }
    
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(BigDecimal hourlyRate) { this.hourlyRate = hourlyRate; }
    
    public LocalDate getJoinDate() { return joinDate; }
    public void setJoinDate(LocalDate joinDate) { this.joinDate = joinDate; }
    
    public EmployeeStatus getStatus() { return status; }
    public void setStatus(EmployeeStatus status) { this.status = status; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    
    public String getAadhaar() { return aadhaar; }
    public void setAadhaar(String aadhaar) { this.aadhaar = aadhaar; }
    
    public String getPan() { return pan; }
    public void setPan(String pan) { this.pan = pan; }
    
    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }
    
    public String getIfsc() { return ifsc; }
    public void setIfsc(String ifsc) { this.ifsc = ifsc; }
    
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    
    public String getUanNumber() { return uanNumber; }
    public void setUanNumber(String uanNumber) { this.uanNumber = uanNumber; }
    
    public String getEsicNumber() { return esicNumber; }
    public void setEsicNumber(String esicNumber) { this.esicNumber = esicNumber; }
    
    public String getEmergencyContactName() { return emergencyContactName; }
    public void setEmergencyContactName(String emergencyContactName) { this.emergencyContactName = emergencyContactName; }
    
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String emergencyContactPhone) { this.emergencyContactPhone = emergencyContactPhone; }
    
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
