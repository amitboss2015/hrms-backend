-- =====================================================
-- HRMS DATABASE COMPLETE RESET SCRIPT
-- Deletes ALL data except superadmin login
-- Run this before fresh testing
-- =====================================================

SET FOREIGN_KEY_CHECKS = 0;
SET SQL_SAFE_UPDATES = 0;

-- ========== 1. ATTENDANCE & IMPORT DATA ==========
DELETE FROM attendance_day;
DELETE FROM attendance_punches;
DELETE FROM attendance_sessions;
DELETE FROM import_error;
DELETE FROM import_batch;

-- ========== 2. LEAVE DATA ==========
DELETE FROM employee_leaves;
DELETE FROM leave_calendar;
DELETE FROM leave_ledger;
DELETE FROM leave_type;

-- ========== 3. PAYROLL & LOAN DATA ==========
DELETE FROM loan_repayments;
DELETE FROM loans;
DELETE FROM payroll;
DELETE FROM salary_overtime_config;

-- ========== 4. EMPLOYEE & SHIFT DATA ==========
DELETE FROM employee_shift_assignments;
DELETE FROM employees;
DELETE FROM shifts;
DELETE FROM holidays;
DELETE FROM weekly_off_config;

-- ========== 5. BIOMETRIC DEVICE DATA ==========
DELETE FROM biometric_device_mappings;
DELETE FROM biometric_devices;

-- ========== 6. AUTH & AUDIT DATA ==========
DELETE FROM login_audit;
DELETE FROM refresh_tokens;

-- ========== 7. REGISTRATION DATA ==========
DELETE FROM registration_attempts;
DELETE FROM trial_tracking;
DELETE FROM company_registrations;

-- ========== 8. USERS (except SUPERADMIN) ==========
DELETE FROM users WHERE role != 'SUPERADMIN';

-- ========== 9. TENANTS (except SUPERADMIN) ==========
DELETE FROM tenant WHERE id != 'SUPERADMIN';

SET FOREIGN_KEY_CHECKS = 1;
SET SQL_SAFE_UPDATES = 1;

-- ========== VERIFICATION ==========
SELECT '✅ DATABASE CLEANUP COMPLETE!' AS status;
SELECT 'Users remaining:' AS info, COUNT(*) AS count FROM users;
SELECT 'Tenants remaining:' AS info, COUNT(*) AS count FROM tenant;
SELECT 'Employees remaining:' AS info, COUNT(*) AS count FROM employees;
SELECT 'Biometric devices remaining:' AS info, COUNT(*) AS count FROM biometric_devices;
SELECT 'Attendance days remaining:' AS info, COUNT(*) AS count FROM attendance_day;
SELECT 'Payroll records remaining:' AS info, COUNT(*) AS count FROM payroll;
SELECT 'Loans remaining:' AS info, COUNT(*) AS count FROM loans;
