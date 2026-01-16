package com.example.hrms.tenant;

import com.example.hrms.tenant.domain.Tenant;
import com.example.hrms.tenant.repo.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Filter to resolve tenant from incoming requests.
 * 
 * Resolution order:
 * 1. Subdomain (sasacollection.hrms.in)
 * 2. X-Tenant-Id header
 * 3. tenantId query parameter
 * 4. Default tenant (for development)
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class TenantFilter extends OncePerRequestFilter {
    
    private final TenantRepository tenantRepository;
    
    // Paths that don't require tenant resolution
    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
        "/api/tenants",        // Tenant management endpoints
        "/api/health",         // Health check
        "/api/public",         // Public endpoints
        "/api/auth",           // Auth endpoints
        "/actuator",           // Spring actuator
        "/swagger",            // Swagger docs
        "/v3/api-docs"         // OpenAPI docs
    );
    
    // Subdomains to ignore (not tenant subdomains)
    private static final List<String> IGNORED_SUBDOMAINS = Arrays.asList(
        "www", "api", "admin", "app", "localhost", "127"
    );
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) 
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        
        // Skip tenant resolution for excluded paths
        if (isExcludedPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            String tenantId = resolveTenant(request);
            
            if (tenantId != null) {
                TenantContext.setTenantId(tenantId);
                log.debug("Tenant resolved: {}", tenantId);
            } else {
                // For development, use default tenant (Sasa Collection)
                TenantContext.setTenantId("SASA001");
                log.debug("Using default tenant: SASA001");
            }
            
            filterChain.doFilter(request, response);
            
        } finally {
            // Always clear tenant context after request
            TenantContext.clear();
        }
    }
    
    /**
     * Resolve tenant from request using multiple strategies
     */
    private String resolveTenant(HttpServletRequest request) {
        String tenantId = null;
        
        // Strategy 1: From subdomain (production)
        // Example: sasacollection.hrms.in
        tenantId = resolveFromSubdomain(request);
        if (tenantId != null) {
            return tenantId;
        }
        
        // Strategy 2: From X-Tenant-Id header (API calls)
        tenantId = request.getHeader("X-Tenant-Id");
        if (tenantId != null && !tenantId.isBlank()) {
            return validateTenant(tenantId);
        }
        
        // Strategy 3: From query parameter (testing)
        tenantId = request.getParameter("tenantId");
        if (tenantId != null && !tenantId.isBlank()) {
            return validateTenant(tenantId);
        }
        
        // Strategy 4: From X-Org-Id header (backward compatibility)
        tenantId = request.getHeader("X-Org-Id");
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId; // Accept as-is for backward compatibility
        }
        
        return null;
    }
    
    /**
     * Resolve tenant from subdomain
     */
    private String resolveFromSubdomain(HttpServletRequest request) {
        String host = request.getServerName();
        
        if (host == null || !host.contains(".")) {
            return null;
        }
        
        String subdomain = host.split("\\.")[0].toLowerCase();
        
        // Skip ignored subdomains
        if (IGNORED_SUBDOMAINS.contains(subdomain)) {
            return null;
        }
        
        // Look up tenant by subdomain
        Optional<Tenant> tenant = tenantRepository.findActiveBySubdomain(subdomain);
        
        if (tenant.isPresent()) {
            Tenant t = tenant.get();
            
            // Check if subscription is valid
            if (!t.isSubscriptionValid()) {
                log.warn("Tenant {} subscription expired", subdomain);
                return null;
            }
            
            TenantContext.setTenantName(t.getName());
            return t.getId();
        }
        
        return null;
    }
    
    /**
     * Validate tenant ID exists and is active
     */
    private String validateTenant(String tenantId) {
        // For development, accept SASA001 without DB check
        if ("SASA001".equals(tenantId)) {
            return tenantId;
        }
        
        Optional<Tenant> tenant = tenantRepository.findById(tenantId);
        if (tenant.isPresent() && tenant.get().getIsActive()) {
            TenantContext.setTenantName(tenant.get().getName());
            return tenantId;
        }
        
        return null;
    }
    
    /**
     * Check if path should be excluded from tenant resolution
     */
    private boolean isExcludedPath(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }
}
