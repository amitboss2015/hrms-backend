-- =====================================================
-- Authentication Tables Migration Script
-- This script adds authentication support to HRMS
-- =====================================================

-- 1. Create users table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    employee_id BIGINT,
    role VARCHAR(50) NOT NULL DEFAULT 'EMPLOYEE',
    is_active BOOLEAN DEFAULT TRUE,
    is_locked BOOLEAN DEFAULT FALSE,
    failed_attempts INT DEFAULT 0,
    lock_time TIMESTAMP NULL,
    password_changed_at TIMESTAMP NULL,
    mfa_enabled BOOLEAN DEFAULT FALSE,
    mfa_secret VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NULL,
    last_login_ip VARCHAR(50),
    UNIQUE KEY uk_user_tenant_email (tenant_id, email),
    INDEX idx_user_email (email),
    INDEX idx_user_tenant (tenant_id),
    FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE SET NULL
);

-- 2. Create refresh_tokens table
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    device_info VARCHAR(500),
    ip_address VARCHAR(50),
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    revoked BOOLEAN DEFAULT FALSE,
    revoked_at TIMESTAMP NULL,
    INDEX idx_refresh_token_hash (token_hash),
    INDEX idx_refresh_user (user_id),
    INDEX idx_refresh_expiry (expires_at),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 3. Create login_audit table
CREATE TABLE IF NOT EXISTS login_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    tenant_id VARCHAR(50),
    email VARCHAR(255),
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    action VARCHAR(50),
    failure_reason VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_tenant (tenant_id),
    INDEX idx_audit_created (created_at),
    INDEX idx_audit_email_action (email, action)
);

-- =====================================================
-- Default Users
-- =====================================================

-- Super Admin (can manage all tenants)
-- Password: SuperAdmin@123
INSERT INTO users (tenant_id, email, password_hash, first_name, last_name, role, is_active)
VALUES (
    'ORG001',
    'superadmin@hrms.in',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/X4.V4jWvKqJ3xK9Gy',
    'Super',
    'Admin',
    'SUPER_ADMIN',
    TRUE
) ON DUPLICATE KEY UPDATE email = email;

-- Default Admin for ORG001
-- Password: Admin@123
INSERT INTO users (tenant_id, email, password_hash, first_name, last_name, role, is_active)
VALUES (
    'ORG001',
    'admin@hrms.in',
    '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi',
    'Admin',
    'User',
    'ADMIN',
    TRUE
) ON DUPLICATE KEY UPDATE email = email;

-- HR Manager for ORG001
-- Password: HrManager@123
INSERT INTO users (tenant_id, email, password_hash, first_name, last_name, role, is_active)
VALUES (
    'ORG001',
    'hr@hrms.in',
    '$2a$12$DqQz3LQv8YXzM4.eZ3KzP.B8D4lPmNqR5sPg6tA2hJkVwXyM1nOeS',
    'HR',
    'Manager',
    'HR_MANAGER',
    TRUE
) ON DUPLICATE KEY UPDATE email = email;

-- =====================================================
-- Sample Users for Testing (Optional)
-- =====================================================

-- Accountant
-- Password: Accountant@123
INSERT INTO users (tenant_id, email, password_hash, first_name, last_name, role, is_active)
VALUES (
    'ORG001',
    'accountant@hrms.in',
    '$2a$12$kN8TQeK3P.Y5zM7.fL4rR.D9F6qOnSu8vXg2wB4jLmYxN1pAcRtWi',
    'Finance',
    'Manager',
    'ACCOUNTANT',
    TRUE
) ON DUPLICATE KEY UPDATE email = email;

-- =====================================================
-- Cleanup Job (run periodically)
-- =====================================================
-- DELETE FROM refresh_tokens WHERE expires_at < DATE_SUB(NOW(), INTERVAL 1 DAY);
-- DELETE FROM login_audit WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY);
