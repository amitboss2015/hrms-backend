-- ============================================
-- Clear Transactional Data Script
-- Keeps: employees, shifts, weekly_off_config, holidays, leave_type
-- Clears: attendance, payroll, loans, leave records
-- ============================================

SET FOREIGN_KEY_CHECKS = 0;

-- ===== ATTENDANCE RELATED =====
TRUNCATE TABLE attendance_day;
TRUNCATE TABLE attendance_punch;
TRUNCATE TABLE attendance_punches;
TRUNCATE TABLE attendance_session;
TRUNCATE TABLE import_batch;
TRUNCATE TABLE import_error;
TRUNCATE TABLE overtime_allowance;

-- ===== PAYROLL RELATED =====
TRUNCATE TABLE payroll;

-- ===== LOAN RELATED =====
TRUNCATE TABLE loan_repayments;
TRUNCATE TABLE loans;

-- ===== LEAVE RELATED =====
TRUNCATE TABLE leave_ledger;
TRUNCATE TABLE leave_calendar;
TRUNCATE TABLE employee_leave;
TRUNCATE TABLE employee_leave_allocation;

-- ===== SHIFT ASSIGNMENTS (Optional - keeping employee-shift mapping) =====
-- TRUNCATE TABLE employee_shift_assignments;
-- Uncomment above line if you want to clear shift assignments too

SET FOREIGN_KEY_CHECKS = 1;

-- Verify counts
SELECT 'Data cleared successfully!' AS status;
SELECT 'Remaining master data:' AS info;
SELECT 'Employees' AS table_name, COUNT(*) AS count FROM employees
UNION ALL
SELECT 'Shifts', COUNT(*) FROM shifts
UNION ALL
SELECT 'Weekly Off Config', COUNT(*) FROM weekly_off_config
UNION ALL
SELECT 'Holidays', COUNT(*) FROM holidays
UNION ALL
SELECT 'Leave Types', COUNT(*) FROM leave_type;

SELECT 'Cleared transactional data:' AS info;
SELECT 'Attendance Days', COUNT(*) FROM attendance_day
UNION ALL
SELECT 'Attendance Punches', COUNT(*) FROM attendance_punch
UNION ALL
SELECT 'Payroll', COUNT(*) FROM payroll
UNION ALL
SELECT 'Loans', COUNT(*) FROM loans
UNION ALL
SELECT 'Leave Records', COUNT(*) FROM employee_leave;
