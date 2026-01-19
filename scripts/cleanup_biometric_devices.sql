-- ============================================================
-- Biometric Device Cleanup Script
-- Run this in MySQL to clean up duplicate/orphan biometric devices
-- ============================================================

-- Show current state
SELECT 'Current biometric devices:' as info;
SELECT id, tenant_id, device_code, device_name, is_default, is_active 
FROM biometric_devices 
ORDER BY tenant_id, device_code, id;

-- Show devices per tenant
SELECT 'Devices per tenant:' as info;
SELECT tenant_id, COUNT(*) as device_count 
FROM biometric_devices 
GROUP BY tenant_id;

-- Find orphan devices (devices with tenant_id that doesn't exist in tenant table)
SELECT 'Orphan devices (tenant does not exist):' as info;
SELECT bd.id, bd.tenant_id, bd.device_code, bd.device_name 
FROM biometric_devices bd 
LEFT JOIN tenant t ON bd.tenant_id = t.id 
WHERE t.id IS NULL;

-- Find duplicate devices (same tenant_id and device_code)
SELECT 'Duplicate devices:' as info;
SELECT tenant_id, device_code, COUNT(*) as count, GROUP_CONCAT(id) as device_ids
FROM biometric_devices 
GROUP BY tenant_id, device_code 
HAVING COUNT(*) > 1;

-- ============================================================
-- CLEANUP OPERATIONS (uncomment to execute)
-- ============================================================

-- 1. Delete orphan devices (where tenant no longer exists)
DELETE bd FROM biometric_devices bd 
LEFT JOIN tenant t ON bd.tenant_id = t.id 
WHERE t.id IS NULL;

SELECT CONCAT('Deleted orphan devices: ', ROW_COUNT()) as result;

-- 2. Delete duplicate devices (keep lowest ID for each tenant+device_code)
DELETE FROM biometric_devices 
WHERE id NOT IN (
    SELECT min_id FROM (
        SELECT MIN(id) as min_id 
        FROM biometric_devices 
        GROUP BY tenant_id, device_code
    ) as keep_ids
);

SELECT CONCAT('Deleted duplicate devices: ', ROW_COUNT()) as result;

-- Show final state
SELECT 'Final state - biometric devices:' as info;
SELECT id, tenant_id, device_code, device_name, is_default, is_active 
FROM biometric_devices 
ORDER BY tenant_id, device_code;

SELECT 'Cleanup complete!' as info;
