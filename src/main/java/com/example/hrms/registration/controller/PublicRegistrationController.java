package com.example.hrms.registration.controller;

import com.example.hrms.registration.dto.CompanyRegistrationRequest;
import com.example.hrms.registration.dto.RegistrationResponse;
import com.example.hrms.registration.service.CompanyRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public REST Controller for company self-registration.
 * These endpoints are accessible without authentication.
 * 
 * Endpoints:
 * - POST /api/public/register - Create new company registration
 * - GET /api/public/activate/{token} - Activate company
 * - POST /api/public/resend-activation - Resend activation email
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Slf4j
public class PublicRegistrationController {

    private final CompanyRegistrationService registrationService;

    /**
     * Register a new company.
     * Creates a pending registration and sends activation email.
     */
    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> registerCompany(
            @Valid @RequestBody CompanyRegistrationRequest request,
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        
        log.info("📝 New company registration: {} ({})", request.getCompanyName(), request.getEmail());
        
        RegistrationResponse response = registrationService.registerCompany(request, ipAddress, userAgent);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Activate company registration.
     * Creates Tenant and Admin User from pending registration.
     */
    @GetMapping("/activate/{token}")
    public ResponseEntity<RegistrationResponse> activateCompany(@PathVariable String token) {
        log.info("🔓 Activation attempt with token: {}...", token.substring(0, Math.min(8, token.length())));
        
        try {
            RegistrationResponse response = registrationService.activateCompany(token);
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("❌ Activation failed with exception: {}", e.getMessage(), e);
            // Return a proper JSON error response instead of letting Spring return HTML error page
            RegistrationResponse errorResponse = RegistrationResponse.error(
                "Activation failed due to an internal error. Please contact support or try registering again."
            );
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Resend activation email for pending registration.
     */
    @PostMapping("/resend-activation")
    public ResponseEntity<RegistrationResponse> resendActivation(@RequestParam String email) {
        log.info("📧 Resend activation requested for: {}", email);
        
        RegistrationResponse response = registrationService.resendActivation(email);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Check if email is available for registration
     */
    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestParam String email) {
        // This would need to be implemented in the service
        return ResponseEntity.ok().build();
    }

    /**
     * Get client IP address, considering proxy headers
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
