package com.example.hrms.auth.dto;

import com.example.hrms.auth.domain.enums.UserRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String tokenType;
    private Long expiresIn;  // seconds
    private Long userId;
    private String email;
    private String name;
    private String tenantId;
    private String tenantName;
    private UserRole role;
    
    // Refresh token is sent via HTTP-only cookie, not in response body
}
