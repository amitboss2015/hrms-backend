-- Add late hour tracking columns to payroll table
-- Run this script to add support for late hour charges

ALTER TABLE payroll ADD COLUMN IF NOT EXISTS total_late_hours DECIMAL(8,2) DEFAULT 0.00;
ALTER TABLE payroll ADD COLUMN IF NOT EXISTS late_hour_charges DECIMAL(12,2) DEFAULT 0.00;

-- Verify columns added
DESCRIBE payroll;
