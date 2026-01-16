package com.example.hrms.auth.dto;

import com.example.hrms.auth.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
    
    @NotBlank(message = "First name is required")
    private String firstName;
    
    private String lastName;
    
    // Optional - for linking to employee
    private Long employeeId;
    
    // Role - defaults to EMPLOYEE
    private UserRole role;
    
    // Tenant ID - required for non-super-admin registrations
    private String tenantId;
}
