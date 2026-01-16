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
 * Initializes default users and tenant on application startup
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        initializeDefaultTenant();
        initializeDefaultUsers();
    }

    private void initializeDefaultTenant() {
        if (!tenantRepository.existsById("ORG001")) {
            Tenant tenant = Tenant.builder()
                    .id("ORG001")
                    .name("Default Organization")
                    .subdomain("default")
                    .email("admin@hrms.in")
                    .plan("ENTERPRISE")
                    .maxEmployees(10000)
                    .isActive(true)
                    .subscriptionStart(LocalDate.now())
                    .build();
            tenantRepository.save(tenant);
            log.info("✅ Created default tenant: ORG001");
        }
    }

    private void initializeDefaultUsers() {
        // Super Admin
        createUserIfNotExists(
            "ORG001",
            "superadmin@hrms.in",
            "SuperAdmin@123",
            "Super",
            "Admin",
            UserRole.SUPER_ADMIN
        );

        // Admin
        createUserIfNotExists(
            "ORG001",
            "admin@hrms.in",
            "Admin@123",
            "Admin",
            "User",
            UserRole.ADMIN
        );

        // HR Manager
        createUserIfNotExists(
            "ORG001",
            "hr@hrms.in",
            "HrManager@123",
            "HR",
            "Manager",
            UserRole.HR_MANAGER
        );

        // Accountant
        createUserIfNotExists(
            "ORG001",
            "accountant@hrms.in",
            "Accountant@123",
            "Finance",
            "Manager",
            UserRole.ACCOUNTANT
        );
    }

    private void createUserIfNotExists(String tenantId, String email, String password,
                                        String firstName, String lastName, UserRole role) {
        if (!userRepository.existsByTenantIdAndEmail(tenantId, email)) {
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
                    .passwordChangedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);
            log.info("✅ Created user: {} with role: {}", email, role);
        }
    }
}
