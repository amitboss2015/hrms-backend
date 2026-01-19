-- =====================================================
-- Add statutory deduction rates to salary_overtime_config
-- These are org-level settings, not per-employee
-- =====================================================

-- Add ESI columns
ALTER TABLE salary_overtime_config 
ADD COLUMN IF NOT EXISTS esi_employee_rate DECIMAL(6,4) NOT NULL DEFAULT 0.0075,
ADD COLUMN IF NOT EXISTS esi_employer_rate DECIMAL(6,4) NOT NULL DEFAULT 0.0325,
ADD COLUMN IF NOT EXISTS esi_wage_ceiling DECIMAL(10,2) NOT NULL DEFAULT 21000.00;

-- Add PF columns
ALTER TABLE salary_overtime_config 
ADD COLUMN IF NOT EXISTS pf_employee_rate DECIMAL(6,4) NOT NULL DEFAULT 0.06,
ADD COLUMN IF NOT EXISTS pf_employer_rate DECIMAL(6,4) NOT NULL DEFAULT 0.06,
ADD COLUMN IF NOT EXISTS pf_wage_ceiling DECIMAL(10,2) NOT NULL DEFAULT 15000.00,
ADD COLUMN IF NOT EXISTS pf_calculation_base VARCHAR(20) NOT NULL DEFAULT 'FULL_PAYMENT';

-- Add Professional Tax column
ALTER TABLE salary_overtime_config 
ADD COLUMN IF NOT EXISTS professional_tax_amount DECIMAL(10,2) NOT NULL DEFAULT 0;

-- Verify
DESCRIBE salary_overtime_config;
SELECT '✅ Statutory rates columns added!' AS status;
