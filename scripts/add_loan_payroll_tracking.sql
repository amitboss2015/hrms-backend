-- Script to add payroll tracking columns to loans table
-- This enables proper one-time loan management:
-- - Track which payroll deducted the loan
-- - Clear association when payroll is deleted
-- - Re-deduct in next payroll generation

-- Add new columns for payroll association (drop first if exists, then add)
-- MySQL doesn't support IF NOT EXISTS for columns, so we use a safe approach

-- Check and add columns
SET @dbname = DATABASE();
SET @tablename = 'loans';

-- Add deducted_in_payroll_id if not exists
SET @columnname = 'deducted_in_payroll_id';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname 
   AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT "Column deducted_in_payroll_id already exists"',
  'ALTER TABLE loans ADD COLUMN deducted_in_payroll_id BIGINT NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- Add deducted_in_month if not exists
SET @columnname = 'deducted_in_month';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname 
   AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT "Column deducted_in_month already exists"',
  'ALTER TABLE loans ADD COLUMN deducted_in_month INT NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- Add deducted_in_year if not exists
SET @columnname = 'deducted_in_year';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname 
   AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT "Column deducted_in_year already exists"',
  'ALTER TABLE loans ADD COLUMN deducted_in_year INT NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- Reset any one-time loans that were marked as CLOSED but never properly associated
-- This makes them available for the next payroll generation
UPDATE loans 
SET status = 'ACTIVE',
    total_paid = 0,
    outstanding_balance = total_repayable,
    emis_paid = 0,
    closed_date = NULL,
    deducted_in_payroll_id = NULL,
    deducted_in_month = NULL,
    deducted_in_year = NULL
WHERE is_one_time_deduction = TRUE 
  AND status = 'CLOSED'
  AND (deducted_in_payroll_id IS NULL OR deducted_in_payroll_id = 0);

SELECT 'Loan payroll tracking columns added successfully!' as status;

-- Show current one-time loans status
SELECT 
    id,
    emp_id,
    principal_amount,
    status,
    is_one_time_deduction,
    deducted_in_payroll_id,
    deducted_in_month,
    deducted_in_year
FROM loans 
WHERE is_one_time_deduction = TRUE
ORDER BY id;
