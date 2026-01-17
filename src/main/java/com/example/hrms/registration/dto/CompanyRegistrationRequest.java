package com.example.hrms.registration.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for company registration
 */
@Data
public class CompanyRegistrationRequest {
    
    @NotBlank(message = "Company name is required")
    @Size(min = 2, max = 255, message = "Company name must be 2-255 characters")
    private String companyName;
    
    @NotBlank(message = "Admin name is required")
    @Size(min = 2, max = 255, message = "Name must be 2-255 characters")
    private String adminName;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email")
    private String email;
    
    @Size(min = 10, max = 20, message = "Phone number must be 10-20 characters")
    private String phone;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
