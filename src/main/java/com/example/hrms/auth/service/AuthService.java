package com.example.hrms.auth.service;

import com.example.hrms.auth.domain.LoginAudit;
import com.example.hrms.auth.domain.RefreshToken;
import com.example.hrms.auth.domain.User;
import com.example.hrms.auth.domain.enums.UserRole;
import com.example.hrms.auth.dto.*;
import com.example.hrms.auth.repo.LoginAuditRepository;
import com.example.hrms.auth.repo.RefreshTokenRepository;
import com.example.hrms.auth.repo.UserRepository;
import com.example.hrms.auth.security.JwtTokenProvider;
import com.example.hrms.tenant.TenantContext;
import com.example.hrms.tenant.domain.Tenant;
import com.example.hrms.tenant.repo.TenantRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

/**
 * Authentication Service - handles login, logout, token refresh
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAuditRepository loginAuditRepository;
    private final TenantRepository tenantRepository;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.refresh-token-expiry-ms:604800000}")
    private long refreshTokenExpiryMs;

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    /**
     * Authenticate user and return tokens
     */
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String tenantId = request.getTenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            tenantId = TenantContext.getTenantIdOrDefault("SASA001");
        }
        
        // Set tenant context for CustomUserDetailsService
        TenantContext.setTenantId(tenantId);

        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        try {
            // Find user
            User user = userRepository.findForAuthentication(tenantId, request.getEmail())
                    .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

            // Check if account is locked
            if (!user.isAccountNonLocked()) {
                loginAuditRepository.save(
                    LoginAudit.loginFailed(tenantId, request.getEmail(), clientIp, userAgent, "Account locked")
                );
                throw new LockedException("Account is locked. Try again after 15 minutes.");
            }

            // Authenticate using the user's actual tenant (important for multi-tenant)
            TenantContext.setTenantId(user.getTenantId());
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Reset failed attempts and record login
            user.recordLogin(clientIp);
            userRepository.save(user);

            // Generate tokens
            String accessToken = tokenProvider.generateAccessToken(user);
            String refreshToken = tokenProvider.generateRefreshToken();

            // Save refresh token (hashed)
            RefreshToken refreshTokenEntity = RefreshToken.builder()
                    .userId(user.getId())
                    .tokenHash(tokenProvider.hashToken(refreshToken))
                    .deviceInfo(userAgent)
                    .ipAddress(clientIp)
                    .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiryMs / 1000))
                    .build();
            refreshTokenRepository.save(refreshTokenEntity);

            // Set refresh token in HTTP-only cookie
            setRefreshTokenCookie(httpResponse, refreshToken);

            // Log successful login
            loginAuditRepository.save(
                LoginAudit.loginSuccess(user.getId(), tenantId, user.getEmail(), clientIp, userAgent)
            );

            // Get tenant name
            String tenantName = tenantRepository.findById(user.getTenantId())
                    .map(Tenant::getName)
                    .orElse(user.getTenantId());

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .tokenType("Bearer")
                    .expiresIn(900L) // 15 minutes
                    .userId(user.getId())
                    .email(user.getEmail())
                    .name(user.getFullName())
                    .tenantId(user.getTenantId())
                    .tenantName(tenantName)
                    .role(user.getRole())
                    .build();

        } catch (BadCredentialsException e) {
            // Increment failed attempts
            userRepository.findByTenantIdAndEmail(tenantId, request.getEmail())
                    .ifPresent(user -> {
                        user.incrementFailedAttempts();
                        userRepository.save(user);
                    });

            loginAuditRepository.save(
                LoginAudit.loginFailed(tenantId, request.getEmail(), clientIp, userAgent, "Invalid credentials")
            );
            throw new BadCredentialsException("Invalid email or password");
        }
    }

    /**
     * Refresh access token using refresh token from cookie
     */
    public AuthResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = getRefreshTokenFromCookie(request);
        
        if (refreshToken == null) {
            throw new BadCredentialsException("Refresh token not found");
        }

        String tokenHash = tokenProvider.hashToken(refreshToken);
        RefreshToken storedToken = refreshTokenRepository
                .findValidByTokenHash(tokenHash, LocalDateTime.now())
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));

        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            throw new LockedException("Account is disabled or locked");
        }

        // Generate new access token
        String newAccessToken = tokenProvider.generateAccessToken(user);

        // Rotate refresh token (optional but recommended)
        String newRefreshToken = tokenProvider.generateRefreshToken();
        
        // Revoke old token
        storedToken.revoke();
        refreshTokenRepository.save(storedToken);

        // Save new token
        RefreshToken newTokenEntity = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(tokenProvider.hashToken(newRefreshToken))
                .deviceInfo(storedToken.getDeviceInfo())
                .ipAddress(getClientIp(request))
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiryMs / 1000))
                .build();
        refreshTokenRepository.save(newTokenEntity);

        // Update cookie
        setRefreshTokenCookie(response, newRefreshToken);

        // Log token refresh
        loginAuditRepository.save(
            LoginAudit.tokenRefresh(user.getId(), user.getTenantId(), getClientIp(request))
        );

        String tenantName = tenantRepository.findById(user.getTenantId())
                .map(Tenant::getName)
                .orElse(user.getTenantId());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresIn(900L)
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getFullName())
                .tenantId(user.getTenantId())
                .tenantName(tenantName)
                .role(user.getRole())
                .build();
    }

    /**
     * Logout - revoke refresh token
     */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = getRefreshTokenFromCookie(request);
        
        if (refreshToken != null) {
            String tokenHash = tokenProvider.hashToken(refreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash)
                    .ifPresent(token -> {
                        token.revoke();
                        refreshTokenRepository.save(token);
                        
                        // Log logout
                        loginAuditRepository.save(
                            LoginAudit.logout(token.getUserId(), 
                                TenantContext.getTenantId(), 
                                null, 
                                getClientIp(request))
                        );
                    });
        }

        // Clear cookie
        clearRefreshTokenCookie(response);
        SecurityContextHolder.clearContext();
    }

    /**
     * Logout all sessions for a user
     */
    public void logoutAllSessions(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
    }

    /**
     * Register new user
     */
    public User registerUser(RegisterRequest request) {
        String tenantId = request.getTenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            tenantId = TenantContext.getTenantIdOrDefault("SASA001");
        }

        // Check if email already exists
        if (userRepository.existsByTenantIdAndEmail(tenantId, request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = User.builder()
                .tenantId(tenantId)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .employeeId(request.getEmployeeId())
                .role(request.getRole() != null ? request.getRole() : UserRole.EMPLOYEE)
                .isActive(true)
                .isLocked(false)
                .failedAttempts(0)
                .passwordChangedAt(LocalDateTime.now())
                .build();

        return userRepository.save(user);
    }

    /**
     * Change password
     */
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        // Revoke all refresh tokens to force re-login
        refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now());
    }

    /**
     * Get current authenticated user
     */
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("Not authenticated");
        }
        return (User) auth.getPrincipal();
    }

    // ============ Helper Methods ============

    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);  // HTTPS only
        cookie.setPath("/api/auth");
        cookie.setMaxAge((int) (refreshTokenExpiryMs / 1000));
        // SameSite=Strict via header (Cookie class doesn't support it directly)
        response.addHeader("Set-Cookie", 
            String.format("%s=%s; HttpOnly; Secure; Path=/api/auth; Max-Age=%d; SameSite=Strict",
                REFRESH_TOKEN_COOKIE, token, refreshTokenExpiryMs / 1000));
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", 
            String.format("%s=; HttpOnly; Secure; Path=/api/auth; Max-Age=0; SameSite=Strict",
                REFRESH_TOKEN_COOKIE));
    }

    private String getRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        
        return Arrays.stream(request.getCookies())
                .filter(c -> REFRESH_TOKEN_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
