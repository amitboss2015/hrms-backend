package com.example.hrms.auth.controller;

import com.example.hrms.auth.domain.User;
import com.example.hrms.auth.dto.*;
import com.example.hrms.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication Controller
 * All endpoints under /api/auth are public (no JWT required)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Login with email and password
     * Returns access token in response body
     * Sets refresh token in HTTP-only cookie
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            AuthResponse response = authService.login(request, httpRequest, httpResponse);
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of(
                "error", "Invalid credentials",
                "message", e.getMessage()
            ));
        } catch (LockedException e) {
            return ResponseEntity.status(423).body(Map.of(
                "error", "Account locked",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Refresh access token using refresh token from cookie
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            AuthResponse authResponse = authService.refreshToken(request, response);
            return ResponseEntity.ok(authResponse);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of(
                "error", "Token refresh failed",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Logout - revoke refresh token
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * Register new user (Admin only in production)
     */
    @PostMapping("/register")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            User user = authService.registerUser(request);
            return ResponseEntity.ok(Map.of(
                "message", "User registered successfully",
                "userId", user.getId(),
                "email", user.getEmail()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Registration failed",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Change password
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        try {
            User currentUser = authService.getCurrentUser();
            authService.changePassword(currentUser.getId(), request);
            return ResponseEntity.ok(Map.of("message", "Password changed successfully. Please login again."));
        } catch (BadCredentialsException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Password change failed",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Get current user info
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        try {
            User user = authService.getCurrentUser();
            return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "name", user.getFullName(),
                "tenantId", user.getTenantId(),
                "role", user.getRole(),
                "employeeId", user.getEmployeeId() != null ? user.getEmployeeId() : ""
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of(
                "error", "Not authenticated"
            ));
        }
    }

    /**
     * Logout from all devices
     */
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll() {
        User currentUser = authService.getCurrentUser();
        authService.logoutAllSessions(currentUser.getId());
        return ResponseEntity.ok(Map.of("message", "Logged out from all devices"));
    }

    /**
     * Health check for auth service
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "OK"));
    }
}
