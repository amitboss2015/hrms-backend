package com.example.hrms.auth;

import com.example.hrms.auth.domain.User;
import com.example.hrms.auth.domain.enums.UserRole;
import com.example.hrms.auth.repo.UserRepository;
import com.example.hrms.tenant.domain.Tenant;
import com.example.hrms.tenant.repo.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Initializes the superadmin user on application startup.
 * Only creates if no users exist in the system.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private static final String SUPERADMIN_TENANT_ID = "SUPERADMIN";
    private static final String SUPERADMIN_EMAIL = "admin@chandrahr.in";
    private static final String SUPERADMIN_PASSWORD = "Admin@123";
    
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
                    .name("ChandraHR Admin")
                    .subdomain("admin")
                    .email(SUPERADMIN_EMAIL)
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
        if (!userRepository.existsByEmail(SUPERADMIN_EMAIL)) {
            User user = User.builder()
                    .tenantId(SUPERADMIN_TENANT_ID)
                    .email(SUPERADMIN_EMAIL)
                    .passwordHash(passwordEncoder.encode(SUPERADMIN_PASSWORD))
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
            log.info("✅ Created superadmin: {} (password: {})", SUPERADMIN_EMAIL, SUPERADMIN_PASSWORD);
        }
    }
}
