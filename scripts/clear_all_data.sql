-- =====================================================
-- HRMS DATABASE COMPLETE RESET SCRIPT
-- Deletes ALL data except superadmin
-- =====================================================

SET FOREIGN_KEY_CHECKS = 0;
SET SQL_SAFE_UPDATES = 0;

-- Delete all transactional data (using DELETE for safety)
DELETE FROM attendance_day;
DELETE FROM attendance_punch;
DELETE FROM attendance_punches;
DELETE FROM attendance_session;
DELETE FROM employee_leave;
DELETE FROM employee_leave_allocation;
DELETE FROM employee_shift_assignments;
DELETE FROM leave_calendar;
DELETE FROM leave_ledger;
DELETE FROM loan_repayments;
DELETE FROM loans;
DELETE FROM payroll;
DELETE FROM overtime_allowance;
DELETE FROM import_batch;
DELETE FROM import_error;
DELETE FROM login_audit;
DELETE FROM refresh_tokens;

-- Delete biometric device data
DELETE FROM biometric_device_mapping;
DELETE FROM biometric_devices;

-- Delete master data
DELETE FROM employees;
DELETE FROM shifts;
DELETE FROM holidays;
DELETE FROM weekly_off_config;
DELETE FROM salary_overtime_config;
DELETE FROM leave_type;

-- Delete registration and tenant data
DELETE FROM registration_attempts;
DELETE FROM trial_tracking;
DELETE FROM company_registrations;

-- Delete all non-superadmin users
DELETE FROM users WHERE role != 'SUPERADMIN';

-- Delete all non-superadmin tenants
DELETE FROM tenant WHERE id != 'SUPERADMIN';

SET FOREIGN_KEY_CHECKS = 1;
SET SQL_SAFE_UPDATES = 1;

-- Verify
SELECT 'Cleanup complete!' AS status;
SELECT COUNT(*) as remaining_users FROM users;
SELECT COUNT(*) as remaining_tenants FROM tenant;
SELECT COUNT(*) as remaining_employees FROM employees;
