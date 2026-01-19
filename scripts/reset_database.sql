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

-- Delete biometric device data
TRUNCATE TABLE biometric_device_mapping;
TRUNCATE TABLE biometric_devices;

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
-- NOTE: Superadmin will be auto-created by DataInitializer
-- when the Spring Boot application starts (if no users exist)
-- =====================================================

-- Verify clean state
SELECT 'Database reset complete!' AS status;
SELECT 'Restart backend to auto-create superadmin' AS note;
SELECT 'Superadmin credentials: admin@chandrahr.in / Admin@123' AS info;
SELECT COUNT(*) as tenant_count FROM tenant;
SELECT COUNT(*) as user_count FROM users;
