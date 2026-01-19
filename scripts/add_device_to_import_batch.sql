-- =====================================================
-- Add device_id and device_code to import_batch table
-- This allows multiple attendance batches per month for different devices
-- =====================================================

-- Step 1: Drop the old unique constraint (if exists)
ALTER TABLE import_batch DROP INDEX IF EXISTS UK_import_batch_org_month_year;
ALTER TABLE import_batch DROP INDEX IF EXISTS UKg7yvp7g9w9xnqjqn6o6qptm0f;

-- Step 2: Add new columns
ALTER TABLE import_batch 
ADD COLUMN IF NOT EXISTS device_id BIGINT NULL,
ADD COLUMN IF NOT EXISTS device_code VARCHAR(50) NULL;

-- Step 3: Add new unique constraint (org + month + year + device)
-- This allows multiple batches per month if they have different device_ids
ALTER TABLE import_batch 
ADD UNIQUE KEY UK_import_batch_org_month_year_device (orgId, month, year, device_id);

-- Verify
DESCRIBE import_batch;
SELECT 'Schema updated successfully!' AS status;
