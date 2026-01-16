-- =====================================================
-- Multi-Tenancy Migration Script
-- This script adds tenant support to the HRMS application
-- =====================================================

-- 1. Create the tenant table
CREATE TABLE IF NOT EXISTS tenant (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    subdomain VARCHAR(100) NOT NULL UNIQUE,
    custom_domain VARCHAR(255),
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    address TEXT,
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100) DEFAULT 'India',
    pincode VARCHAR(10),
    plan VARCHAR(50) DEFAULT 'FREE',
    max_employees INT DEFAULT 10,
    subscription_start DATE,
    subscription_end DATE,
    is_active BOOLEAN DEFAULT TRUE,
    logo_url VARCHAR(500),
    primary_color VARCHAR(10) DEFAULT '#10B981',
    secondary_color VARCHAR(10),
    timezone VARCHAR(50) DEFAULT 'Asia/Kolkata',
    date_format VARCHAR(10) DEFAULT 'dd/MM/yyyy',
    currency VARCHAR(10) DEFAULT 'INR',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 2. Insert default tenant for existing data
INSERT INTO tenant (id, name, subdomain, email, plan, max_employees, is_active)
VALUES ('ORG001', 'Default Organization', 'default', 'admin@default.com', 'ENTERPRISE', 10000, TRUE)
ON DUPLICATE KEY UPDATE name = name;

-- 3. Add tenant_id column to employees table (if not exists)
ALTER TABLE employees ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'ORG001';
UPDATE employees SET tenant_id = 'ORG001' WHERE tenant_id IS NULL;
ALTER TABLE employees MODIFY tenant_id VARCHAR(50) NOT NULL DEFAULT 'ORG001';

-- 4. Add tenant_id column to shifts table
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'ORG001';
UPDATE shifts SET tenant_id = 'ORG001' WHERE tenant_id IS NULL;
ALTER TABLE shifts MODIFY tenant_id VARCHAR(50) NOT NULL DEFAULT 'ORG001';

-- 5. Update holidays table (rename orgId to tenant_id if needed, or add tenant_id)
-- Note: If orgId already exists as VARCHAR, just rename/copy it
ALTER TABLE holidays ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'ORG001';
-- Copy orgId values if column exists
-- UPDATE holidays SET tenant_id = org_id WHERE org_id IS NOT NULL AND tenant_id = 'ORG001';
UPDATE holidays SET tenant_id = 'ORG001' WHERE tenant_id IS NULL;

-- 6. Update weekly_off_config table
ALTER TABLE weekly_off_config ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'ORG001';
-- UPDATE weekly_off_config SET tenant_id = org_id WHERE org_id IS NOT NULL AND tenant_id = 'ORG001';
UPDATE weekly_off_config SET tenant_id = 'ORG001' WHERE tenant_id IS NULL;

-- 7. Update attendance_day table
ALTER TABLE attendance_day ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50);
UPDATE attendance_day SET tenant_id = 'ORG001' WHERE tenant_id IS NULL;

-- 8. Update attendance_punch table
ALTER TABLE attendance_punch ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50);
UPDATE attendance_punch SET tenant_id = 'ORG001' WHERE tenant_id IS NULL;

-- 9. Update payroll table
ALTER TABLE payroll ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'ORG001';
-- UPDATE payroll SET tenant_id = org_id WHERE org_id IS NOT NULL AND tenant_id = 'ORG001';
UPDATE payroll SET tenant_id = 'ORG001' WHERE tenant_id IS NULL;
ALTER TABLE payroll MODIFY tenant_id VARCHAR(50) NOT NULL DEFAULT 'ORG001';

-- 10. Update loans table
ALTER TABLE loans ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'ORG001';
-- UPDATE loans SET tenant_id = org_id WHERE org_id IS NOT NULL AND tenant_id = 'ORG001';
UPDATE loans SET tenant_id = 'ORG001' WHERE tenant_id IS NULL;
ALTER TABLE loans MODIFY tenant_id VARCHAR(50) NOT NULL DEFAULT 'ORG001';

-- 11. Update loan_repayment table
ALTER TABLE loan_repayment ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'ORG001';
UPDATE loan_repayment SET tenant_id = 'ORG001' WHERE tenant_id IS NULL;

-- 12. Update leave_type table
ALTER TABLE leave_type ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'ORG001';
UPDATE leave_type SET tenant_id = 'ORG001' WHERE tenant_id IS NULL;
ALTER TABLE leave_type MODIFY tenant_id VARCHAR(50) NOT NULL DEFAULT 'ORG001';

-- 13. Update employee_leave table
ALTER TABLE employee_leave ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'ORG001';
UPDATE employee_leave SET tenant_id = 'ORG001' WHERE tenant_id IS NULL;
ALTER TABLE employee_leave MODIFY tenant_id VARCHAR(50) NOT NULL DEFAULT 'ORG001';

-- 14. Update employee_leave_allocation table
ALTER TABLE employee_leave_allocation ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'ORG001';
UPDATE employee_leave_allocation SET tenant_id = 'ORG001' WHERE tenant_id IS NULL;

-- 15. Update leave_calendar table
ALTER TABLE leave_calendar ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'ORG001';
UPDATE leave_calendar SET tenant_id = 'ORG001' WHERE tenant_id IS NULL;

-- 16. Update leave_ledger table
ALTER TABLE leave_ledger ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'ORG001';
UPDATE leave_ledger SET tenant_id = 'ORG001' WHERE tenant_id IS NULL;

-- 17. Update employee_shift_assignment table
ALTER TABLE employee_shift_assignment ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50) DEFAULT 'ORG001';
UPDATE employee_shift_assignment SET tenant_id = 'ORG001' WHERE tenant_id IS NULL;

-- 18. Create indexes for tenant_id columns
CREATE INDEX IF NOT EXISTS idx_employees_tenant ON employees(tenant_id);
CREATE INDEX IF NOT EXISTS idx_shifts_tenant ON shifts(tenant_id);
CREATE INDEX IF NOT EXISTS idx_holidays_tenant ON holidays(tenant_id);
CREATE INDEX IF NOT EXISTS idx_weeklyoff_tenant ON weekly_off_config(tenant_id);
CREATE INDEX IF NOT EXISTS idx_attendance_day_tenant ON attendance_day(tenant_id);
CREATE INDEX IF NOT EXISTS idx_payroll_tenant ON payroll(tenant_id);
CREATE INDEX IF NOT EXISTS idx_loans_tenant ON loans(tenant_id);
CREATE INDEX IF NOT EXISTS idx_leavetype_tenant ON leave_type(tenant_id);
CREATE INDEX IF NOT EXISTS idx_empleave_tenant ON employee_leave(tenant_id);

-- =====================================================
-- Sample tenants for testing
-- =====================================================

-- Sasa Collection tenant (example)
INSERT INTO tenant (id, name, subdomain, email, phone, city, state, plan, max_employees, is_active)
VALUES ('SASA001', 'Sasa Collection Pvt Ltd', 'sasacollection', 'hr@sasacollection.com', 
        '+91-9876543210', 'Mumbai', 'Maharashtra', 'PRO', 200, TRUE)
ON DUPLICATE KEY UPDATE name = name;

-- Tech Corp tenant (example)
INSERT INTO tenant (id, name, subdomain, email, phone, city, state, plan, max_employees, is_active)
VALUES ('TECH001', 'Tech Corp Solutions', 'techcorp', 'hr@techcorp.com', 
        '+91-9876543211', 'Bangalore', 'Karnataka', 'BASIC', 50, TRUE)
ON DUPLICATE KEY UPDATE name = name;

-- =====================================================
-- Verification queries (run after migration)
-- =====================================================
-- SELECT * FROM tenant;
-- SELECT DISTINCT tenant_id FROM employees;
-- SELECT DISTINCT tenant_id FROM shifts;
-- SELECT DISTINCT tenant_id FROM payroll;
