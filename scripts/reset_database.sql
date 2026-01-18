-- =====================================================
-- HRMS DATABASE RESET SCRIPT
-- This script deletes ALL data and creates a fresh superadmin
-- Run with: mysql -u root -p payroll_hrms < reset_database.sql
-- =====================================================

SET FOREIGN_KEY_CHECKS = 0;

-- Delete all transactional data first (order matters due to FK constraints)
TRUNCATE TABLE attendance_day;
TRUNCATE TABLE attendance_punch;
TRUNCATE TABLE attendance_punches;
TRUNCATE TABLE attendance_session;
TRUNCATE TABLE employee_leave;
TRUNCATE TABLE employee_leave_allocation;
TRUNCATE TABLE employee_shift_assignments;
TRUNCATE TABLE leave_calendar;
TRUNCATE TABLE leave_ledger;
TRUNCATE TABLE loan_repayments;
TRUNCATE TABLE loans;
TRUNCATE TABLE payroll;
TRUNCATE TABLE overtime_allowance;
TRUNCATE TABLE import_batch;
TRUNCATE TABLE import_error;
TRUNCATE TABLE login_audit;
TRUNCATE TABLE refresh_tokens;

-- Delete master data
TRUNCATE TABLE employees;
TRUNCATE TABLE shifts;
TRUNCATE TABLE holidays;
TRUNCATE TABLE weekly_off_config;
TRUNCATE TABLE salary_overtime_config;
TRUNCATE TABLE leave_type;

-- Delete registration and tenant data
TRUNCATE TABLE registration_attempts;
TRUNCATE TABLE trial_tracking;
TRUNCATE TABLE company_registrations;

-- Delete users (except superadmin which we'll create fresh)
TRUNCATE TABLE users;

-- Delete all tenants
TRUNCATE TABLE tenant;

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================
-- CREATE SUPERADMIN USER AND TENANT
-- =====================================================

-- Create superadmin tenant
INSERT INTO tenant (
    id, name, subdomain, email, phone, is_active, 
    plan, max_employees, currency, timezone, date_format,
    subscription_start, subscription_end, created_at, deleted
) VALUES (
    'SUPERADMIN', 
    'ChandraHR Admin', 
    'admin', 
    'admin@chandrahr.in', 
    '+91-9999999999',
    b'1',
    'ENTERPRISE',
    99999,
    'INR',
    'Asia/Kolkata',
    'DD/MM/YYYY',
    CURDATE(),
    DATE_ADD(CURDATE(), INTERVAL 100 YEAR),
    NOW(),
    b'0'
);

-- Create superadmin user
-- Password: Admin@123 (BCrypt hash)
INSERT INTO users (
    email, password_hash, first_name, last_name, 
    role, tenant_id, is_active, created_at
) VALUES (
    'admin@chandrahr.in',
    '$2a$10$N9qo8uLOickgx2ZMRZoMy.MqrxvE0t.W0Y.yVX3vCmqRlqvXjFSHu',
    'Super',
    'Admin',
    'SUPER_ADMIN',
    'SUPERADMIN',
    b'1',
    NOW()
);

-- =====================================================
-- VERIFY
-- =====================================================
SELECT 'Database reset complete!' AS status;
SELECT 'Superadmin created:' AS info, 'admin@chandrahr.in' AS email, 'Admin@123' AS password;
SELECT * FROM tenant;
SELECT id, email, role, tenant_id, HEX(is_active) as is_active FROM users;
