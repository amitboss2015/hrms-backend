-- =====================================================
-- Fix ESI and EPF applicability for existing employees
-- Sets default values where NULL
-- =====================================================

-- Set esicApplicable to TRUE where it's NULL
UPDATE employees 
SET esic_applicable = 1 
WHERE esic_applicable IS NULL;

-- Set epfApplicable to TRUE where it's NULL
UPDATE employees 
SET epf_applicable = 1 
WHERE epf_applicable IS NULL;

-- Set ptApplicable to TRUE where it's NULL
UPDATE employees 
SET pt_applicable = 1 
WHERE pt_applicable IS NULL;

-- Verify
SELECT 
    COUNT(*) as total_employees,
    SUM(CASE WHEN esic_applicable = 1 THEN 1 ELSE 0 END) as esic_enabled,
    SUM(CASE WHEN epf_applicable = 1 THEN 1 ELSE 0 END) as epf_enabled,
    SUM(CASE WHEN pt_applicable = 1 THEN 1 ELSE 0 END) as pt_enabled
FROM employees;

SELECT '✅ ESI/EPF/PT defaults fixed for all employees!' AS status;
