package com.example.hrms.registration.repo;

import com.example.hrms.registration.domain.CompanyRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRegistrationRepository extends JpaRepository<CompanyRegistration, Long> {
    
    /**
     * Find by activation token for verification
     */
    Optional<CompanyRegistration> findByActivationToken(String token);
    
    /**
     * Find by email (for checking duplicates)
     */
    Optional<CompanyRegistration> findByEmail(String email);
    
    /**
     * Check if email already registered
     */
    boolean existsByEmail(String email);
    
    /**
     * Check if subdomain already taken
     */
    boolean existsBySubdomain(String subdomain);
    
    /**
     * Find pending (not activated) registration by email
     */
    Optional<CompanyRegistration> findByEmailAndActivatedFalse(String email);
    
    /**
     * Find activated registration by email
     */
    Optional<CompanyRegistration> findByEmailAndActivatedTrue(String email);
}
