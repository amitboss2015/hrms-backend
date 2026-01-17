package com.example.hrms.registration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for registration operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationResponse {
    
    private boolean success;
    private String message;
    private String email;
    private String subdomain;
    private String tenantId;
    
    public static RegistrationResponse success(String message) {
        return RegistrationResponse.builder()
                .success(true)
                .message(message)
                .build();
    }
    
    public static RegistrationResponse success(String message, String email, String subdomain) {
        return RegistrationResponse.builder()
                .success(true)
                .message(message)
                .email(email)
                .subdomain(subdomain)
                .build();
    }
    
    public static RegistrationResponse error(String message) {
        return RegistrationResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
    
    public static RegistrationResponse activated(String tenantId, String subdomain) {
        return RegistrationResponse.builder()
                .success(true)
                .message("Account activated successfully! You can now login.")
                .tenantId(tenantId)
                .subdomain(subdomain)
                .build();
    }
}
