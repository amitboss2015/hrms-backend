package com.example.hrms.auth.repo;

import com.example.hrms.auth.domain.User;
import com.example.hrms.auth.domain.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by email (unique across all tenants for super admin)
     */
    Optional<User> findByEmail(String email);

    /**
     * Find user by tenant and email
     */
    Optional<User> findByTenantIdAndEmail(String tenantId, String email);

    /**
     * Check if email exists in tenant
     */
    boolean existsByTenantIdAndEmail(String tenantId, String email);

    /**
     * Find all users for a tenant
     */
    List<User> findByTenantId(String tenantId);

    /**
     * Find users by role in tenant
     */
    List<User> findByTenantIdAndRole(String tenantId, UserRole role);

    /**
     * Find active users in tenant
     */
    List<User> findByTenantIdAndIsActiveTrue(String tenantId);

    /**
     * Find user by employee ID
     */
    Optional<User> findByTenantIdAndEmployeeId(String tenantId, Long employeeId);

    /**
     * Count users by tenant
     */
    long countByTenantId(String tenantId);

    /**
     * Find users with login credentials for authentication
     * Includes both tenant-specific and super admin lookup
     */
    @Query("SELECT u FROM User u WHERE u.email = :email AND (u.tenantId = :tenantId OR u.role = 'SUPER_ADMIN')")
    Optional<User> findForAuthentication(@Param("tenantId") String tenantId, @Param("email") String email);
}
