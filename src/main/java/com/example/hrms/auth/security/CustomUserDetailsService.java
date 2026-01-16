package com.example.hrms.auth.security;

import com.example.hrms.auth.domain.User;
import com.example.hrms.auth.repo.UserRepository;
import com.example.hrms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Custom UserDetailsService for Spring Security
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String tenantId = TenantContext.getTenantIdOrDefault("ORG001");
        
        // Try tenant-specific lookup first
        User user = userRepository.findByTenantIdAndEmail(tenantId, email)
                .orElseGet(() -> 
                    // Fallback to global lookup (for SUPER_ADMIN)
                    userRepository.findByEmail(email)
                        .orElseThrow(() -> 
                            new UsernameNotFoundException("User not found: " + email)
                        )
                );

        return user;
    }

    /**
     * Load user by email and tenant (explicit tenant)
     */
    public User loadUserByEmailAndTenant(String email, String tenantId) {
        return userRepository.findForAuthentication(tenantId, email)
                .orElseThrow(() -> 
                    new UsernameNotFoundException("User not found: " + email + " in tenant: " + tenantId)
                );
    }

    /**
     * Load user by ID
     */
    public User loadUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> 
                    new UsernameNotFoundException("User not found with ID: " + userId)
                );
    }
}
