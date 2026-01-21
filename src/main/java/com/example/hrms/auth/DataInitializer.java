package com.example.hrms.auth;

import com.example.hrms.auth.domain.User;
import com.example.hrms.auth.domain.enums.UserRole;
import com.example.hrms.auth.repo.UserRepository;
import com.example.hrms.tenant.domain.Tenant;
import com.example.hrms.tenant.repo.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Initializes the superadmin user on application startup.
 * Only creates if no users exist in the system.
 * 
 * Security: 
 * - Uses environment variables for credentials (recommended for production)
 * - Falls back to random password generation if not provided
 * - Logs the generated password ONCE for initial setup
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private static final String SUPERADMIN_TENANT_ID = "SUPERADMIN";
    
    // Read from environment variables with secure defaults
    @Value("${app.admin.email:admin@hrms.local}")
    private String adminEmail;
    
    @Value("${app.admin.password:}")  // Empty default = generate random
    private String adminPassword;
    
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // Only initialize if database is empty (no users exist)
        if (userRepository.count() == 0) {
            log.info("🔧 No users found. Initializing superadmin...");
            initializeSuperadminTenant();
            initializeSuperadminUser();
        } else {
            log.info("✅ Users exist. Skipping data initialization.");
        }
    }

    private void initializeSuperadminTenant() {
        if (!tenantRepository.existsById(SUPERADMIN_TENANT_ID)) {
            Tenant tenant = Tenant.builder()
                    .id(SUPERADMIN_TENANT_ID)
                    .name("HRMS Admin")
                    .subdomain("admin")
                    .email(adminEmail)
                    .city("India")
                    .state("")
                    .country("India")
                    .plan("ENTERPRISE")
                    .maxEmployees(99999)
                    .isActive(true)
                    .subscriptionStart(LocalDate.now())
                    .primaryColor("#10B981")
                    .currency("INR")
                    .dateFormat("dd/MM/yyyy")
                    .timezone("Asia/Kolkata")
                    .build();
            tenantRepository.save(tenant);
            log.info("✅ Created superadmin tenant: {}", SUPERADMIN_TENANT_ID);
        }
    }

    private void initializeSuperadminUser() {
        if (!userRepository.existsByEmail(adminEmail)) {
            // Use provided password or generate a secure random one
            String password = getOrGeneratePassword();
            
            User user = User.builder()
                    .tenantId(SUPERADMIN_TENANT_ID)
                    .email(adminEmail)
                    .passwordHash(passwordEncoder.encode(password))
                    .firstName("Super")
                    .lastName("Admin")
                    .role(UserRole.SUPER_ADMIN)
                    .isActive(true)
                    .isLocked(false)
                    .failedAttempts(0)
                    .mfaEnabled(false)
                    .passwordChangedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);
            
            // Log credentials - ONLY on first creation
            log.warn("═══════════════════════════════════════════════════════════════");
            log.warn("  🔐 SUPERADMIN CREDENTIALS (Save these securely!)");
            log.warn("  📧 Email: {}", adminEmail);
            if (adminPassword == null || adminPassword.isBlank()) {
                log.warn("  🔑 Password: {} (AUTO-GENERATED)", password);
                log.warn("  ⚠️  Change this password immediately after first login!");
            } else {
                log.warn("  🔑 Password: (provided via environment variable)");
            }
            log.warn("═══════════════════════════════════════════════════════════════");
        }
    }
    
    /**
     * Get password from environment or generate a secure random one
     */
    private String getOrGeneratePassword() {
        if (adminPassword != null && !adminPassword.isBlank()) {
            return adminPassword;
        }
        
        // Generate a secure random password (16 chars)
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        
        // Add special chars to meet password requirements
        return randomPart.substring(0, 12) + "@1Aa";
    }
}
