package com.example.hrms.tenant;

/**
 * Thread-local storage for the current tenant context.
 * This allows tenant information to be accessed anywhere in the request processing chain.
 * 
 * Usage:
 * - TenantContext.setTenantId("TENANT001") - Set current tenant
 * - TenantContext.getTenantId() - Get current tenant
 * - TenantContext.clear() - Clear after request processing
 */
public class TenantContext {
    
    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();
    private static final ThreadLocal<String> currentTenantName = new ThreadLocal<>();
    
    /**
     * Set the current tenant ID for this thread
     */
    public static void setTenantId(String tenantId) {
        currentTenant.set(tenantId);
    }
    
    /**
     * Get the current tenant ID
     * @return tenant ID or null if not set
     */
    public static String getTenantId() {
        return currentTenant.get();
    }
    
    /**
     * Get the current tenant ID, with a default fallback
     * @param defaultTenantId fallback value if no tenant is set
     * @return tenant ID or default
     * @deprecated Use requireTenantId() instead. Falling back to default tenant is a security risk.
     */
    @Deprecated
    public static String getTenantIdOrDefault(String defaultTenantId) {
        String tenantId = currentTenant.get();
        if (tenantId == null) {
            // Log warning in production - this should not happen
            System.err.println("[SECURITY WARNING] TenantContext.getTenantIdOrDefault called without tenant - using fallback: " + defaultTenantId);
        }
        return tenantId != null ? tenantId : defaultTenantId;
    }
    
    /**
     * Set the tenant name (for display purposes)
     */
    public static void setTenantName(String tenantName) {
        currentTenantName.set(tenantName);
    }
    
    /**
     * Get the tenant name
     */
    public static String getTenantName() {
        return currentTenantName.get();
    }
    
    /**
     * Check if a tenant is set
     */
    public static boolean hasTenant() {
        return currentTenant.get() != null;
    }
    
    /**
     * Clear the tenant context (call this after request processing)
     */
    public static void clear() {
        currentTenant.remove();
        currentTenantName.remove();
    }
    
    /**
     * Require tenant to be set, throw exception if not
     * @return tenant ID
     * @throws TenantNotResolvedException if tenant is not set
     */
    public static String requireTenantId() {
        String tenantId = currentTenant.get();
        if (tenantId == null) {
            throw new TenantNotResolvedException("Tenant not identified. Please access via tenant subdomain or provide X-Tenant-Id header.");
        }
        return tenantId;
    }
}
