-- Add late/early approval columns to attendance_day table
-- Run this script to enable late/early approval feature

-- Add all columns in a single ALTER statement
ALTER TABLE attendance_day 
ADD COLUMN late_approved BOOLEAN DEFAULT FALSE,
ADD COLUMN early_out_approved BOOLEAN DEFAULT FALSE,
ADD COLUMN approved_by VARCHAR(64),
ADD COLUMN approved_at DATETIME,
ADD COLUMN approval_remarks VARCHAR(256);

-- Create index for quick lookup of pending approvals (optional)
-- CREATE INDEX idx_attendance_approval ON attendance_day(tenant_id, late_approved, early_out_approved);

-- Verify columns added
SELECT COLUMN_NAME, DATA_TYPE, COLUMN_DEFAULT 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_NAME = 'attendance_day' 
AND COLUMN_NAME IN ('late_approved', 'early_out_approved', 'approved_by', 'approved_at', 'approval_remarks');
