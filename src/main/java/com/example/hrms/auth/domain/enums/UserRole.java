package com.example.hrms.auth.domain.enums;

/**
 * User roles for authorization
 */
public enum UserRole {
    SUPER_ADMIN,    // System admin - can manage tenants
    ADMIN,          // Tenant admin - full access within tenant
    HR_MANAGER,     // HR operations - employees, attendance, leave, payroll
    ACCOUNTANT,     // Financial - payroll, loans, reports
    MANAGER,        // Team manager - approve leaves, view team attendance
    EMPLOYEE        // Self-service - own profile, attendance, leaves
}
