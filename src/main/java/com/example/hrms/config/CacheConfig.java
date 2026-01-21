package com.example.hrms.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration for frequently accessed, rarely changing data.
 * 
 * Cached Data:
 * - WeeklyOffConfig: Weekly off settings per tenant/employment type
 * - SalaryOvertimeConfig: Salary calculation rules per tenant
 * - Holidays: Holiday list per tenant/year
 * - Shifts: Shift definitions per tenant
 * 
 * This significantly reduces database queries during batch operations
 * like payroll generation and attendance processing.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Cache names used across the application
     */
    public static final String CACHE_WEEKLY_OFF = "weeklyOffConfig";
    public static final String CACHE_SALARY_CONFIG = "salaryOvertimeConfig";
    public static final String CACHE_HOLIDAYS = "holidays";
    public static final String CACHE_SHIFTS = "shifts";
    public static final String CACHE_EMPLOYEES = "employees";
    public static final String CACHE_TENANT_CONFIG = "tenantConfig";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Default cache spec: 10 minute TTL, max 1000 entries
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats()); // Enable stats for monitoring
        
        // Register cache names
        cacheManager.setCacheNames(java.util.List.of(
                CACHE_WEEKLY_OFF,
                CACHE_SALARY_CONFIG,
                CACHE_HOLIDAYS,
                CACHE_SHIFTS,
                CACHE_EMPLOYEES,
                CACHE_TENANT_CONFIG
        ));
        
        return cacheManager;
    }

    /**
     * Utility to generate cache keys
     */
    public static String tenantKey(String tenantId, String... parts) {
        StringBuilder key = new StringBuilder(tenantId);
        for (String part : parts) {
            key.append(":").append(part != null ? part : "null");
        }
        return key.toString();
    }
}
