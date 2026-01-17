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
 * Initializes default users and tenant on application startup.
 * 
 * Default tenant: SASA001 (Sasa Collection Pvt Ltd)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private static final String DEFAULT_TENANT_ID = "SASA001";
    private static final String DEFAULT_TENANT_NAME = "Sasa Collection Pvt Ltd";
    private static final String DEFAULT_SUBDOMAIN = "sasacollection";
    
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        initializeDefaultTenant();
        initializeDefaultUsers();
    }

    private void initializeDefaultTenant() {
        if (!tenantRepository.existsById(DEFAULT_TENANT_ID)) {
            Tenant tenant = Tenant.builder()
                    .id(DEFAULT_TENANT_ID)
                    .name(DEFAULT_TENANT_NAME)
                    .subdomain(DEFAULT_SUBDOMAIN)
                    .email("hr@sasacollection.com")
                    .city("Mumbai")
                    .state("Maharashtra")
                    .country("India")
                    .plan("ENTERPRISE")
                    .maxEmployees(10000)
                    .isActive(true)
                    .subscriptionStart(LocalDate.now())
                    .primaryColor("#6366F1")
                    .currency("INR")
                    .dateFormat("dd/MM/yyyy")
                    .timezone("Asia/Kolkata")
                    .build();
            tenantRepository.save(tenant);
            log.info("✅ Created default tenant: {} ({})", DEFAULT_TENANT_NAME, DEFAULT_TENANT_ID);
        }
    }

    private void initializeDefaultUsers() {
        // Super Admin - for system-level access
        createUserIfNotExists(
            DEFAULT_TENANT_ID,
            "superadmin@chandrahr.in",
            "SuperAdmin@123",
            "Super",
            "Admin",
            UserRole.SUPER_ADMIN
        );
        
        // Also create with old email for backward compatibility
        createUserIfNotExists(
            DEFAULT_TENANT_ID,
            "superadmin@hrms.in",
            "SuperAdmin@123",
            "Super",
            "Admin",
            UserRole.SUPER_ADMIN
        );

        // Sasa Collection Admin
        createUserIfNotExists(
            DEFAULT_TENANT_ID,
            "admin@sasacollection.com",
            "admin@123",
            "Sasa",
            "Admin",
            UserRole.ADMIN
        );

        // HR Manager
        createUserIfNotExists(
            DEFAULT_TENANT_ID,
            "hr@sasacollection.com",
            "hr@123",
            "HR",
            "Manager",
            UserRole.HR_MANAGER
        );

        // Accountant
        createUserIfNotExists(
            DEFAULT_TENANT_ID,
            "accounts@sasacollection.com",
            "accounts@123",
            "Accounts",
            "Manager",
            UserRole.ACCOUNTANT
        );
    }

    private void createUserIfNotExists(String tenantId, String email, String password,
                                        String firstName, String lastName, UserRole role) {
        // Check if user exists by email only (ignoring tenant for global uniqueness)
        if (!userRepository.existsByEmail(email)) {
            User user = User.builder()
                    .tenantId(tenantId)
                    .email(email)
                    .passwordHash(passwordEncoder.encode(password))
                    .firstName(firstName)
                    .lastName(lastName)
                    .role(role)
                    .isActive(true)
                    .isLocked(false)
                    .failedAttempts(0)
                    .mfaEnabled(false)
                    .passwordChangedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);
            log.info("✅ Created user: {} with role: {} for tenant: {}", email, role, tenantId);
        }
    }
}
