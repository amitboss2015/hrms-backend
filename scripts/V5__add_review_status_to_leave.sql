-- V5: Add REVIEW status to employee_leave table
-- This script updates the status column to support the new REVIEW status
-- Run this script to update the database schema

-- First, check if the column is ENUM and convert it to VARCHAR if needed
-- MySQL ENUM columns need to be altered to include new values
-- We'll convert to VARCHAR(20) to support all status values

-- Step 1: Modify the column to VARCHAR if it's ENUM, or ensure it's large enough
ALTER TABLE employee_leave 
MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'REVIEW';

-- Step 2: Update any existing NULL or invalid statuses to REVIEW (for safety)
UPDATE employee_leave 
SET status = 'REVIEW' 
WHERE status IS NULL OR status NOT IN ('REVIEW', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED');

-- Step 3: Verify the change (optional - for manual verification)
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT 
-- FROM INFORMATION_SCHEMA.COLUMNS 
-- WHERE TABLE_SCHEMA = DATABASE() 
-- AND TABLE_NAME = 'employee_leave' 
-- AND COLUMN_NAME = 'status';
