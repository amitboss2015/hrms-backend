-- ============================================================
-- HRMS Employee Data Seed Script
-- Generated: 2026-01-14
-- 
-- This script will:
-- 1. Clear all employee-related data (cascade)
-- 2. Insert fresh employee data with emp codes and basic salaries
-- ============================================================

-- Disable foreign key checks temporarily
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- STEP 1: Clear existing data from dependent tables first
-- ============================================================

-- Clear attendance related data
DELETE FROM attendance_punch;
DELETE FROM attendance_punches;
DELETE FROM attendance_session;
DELETE FROM attendance_day;
DELETE FROM import_batch;
DELETE FROM import_error;

-- Clear leave related data
DELETE FROM employee_leave;
DELETE FROM leave_ledger;
DELETE FROM employee_leave_allocation;

-- Clear payroll data
DELETE FROM payroll;

-- Clear loan data
DELETE FROM loan_repayments;
DELETE FROM loans;

-- Clear shift assignments
DELETE FROM employee_shift_assignments;

-- Clear overtime allowances
DELETE FROM overtime_allowance;

-- Finally, clear employees table
DELETE FROM employees;

-- Reset auto-increment
ALTER TABLE employees AUTO_INCREMENT = 1;

-- Re-enable foreign key checks
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- STEP 2: Insert Employee Data
-- Format: emp_code, first_name, base_salary
-- Default: employment_type = 'FULL_TIME', status = 'ACTIVE'
-- ============================================================

INSERT INTO employees (
    emp_code, 
    first_name, 
    last_name,
    employment_type, 
    status, 
    base_salary, 
    salary_basis,
    working_days_per_month,
    standard_working_hours_per_day,
    ot_allowed,
    epf_applicable,
    esic_applicable,
    pt_applicable,
    tds_applicable
) VALUES

-- ========================
-- GENTS STAFF (Numeric IDs)
-- ========================
('2', 'MD SARWAR', NULL, 'FULL_TIME', 'ACTIVE', 30000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('4', 'DABLU KUMAR', 'PRITY', 'FULL_TIME', 'ACTIVE', 9688.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('7', 'GOPAL KUMAR', NULL, 'FULL_TIME', 'ACTIVE', 9688.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('9', 'JITENDRA TANTI', NULL, 'FULL_TIME', 'ACTIVE', 12160.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('10', 'VIJAY RAVIDAS', NULL, 'FULL_TIME', 'ACTIVE', 12160.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('11', 'SAURABH KUMAR', NULL, 'FULL_TIME', 'ACTIVE', 8800.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('12', 'VISHAL CHAUHAN', NULL, 'FULL_TIME', 'ACTIVE', 9800.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('13', 'BIPIN CHAUDHARY', NULL, 'FULL_TIME', 'ACTIVE', 8500.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('15', 'PANKAJ KUMAR', NULL, 'FULL_TIME', 'ACTIVE', 1500.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('16', 'LALAN TANTI', NULL, 'FULL_TIME', 'ACTIVE', 26666.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('18', 'VAKIL KUMAR', NULL, 'FULL_TIME', 'ACTIVE', 11846.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('19', 'DHARMVEER KUMAR', NULL, 'FULL_TIME', 'ACTIVE', 12800.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('21', 'YOGENDRA KUMAR', NULL, 'FULL_TIME', 'ACTIVE', 7500.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('30', 'BITTU KUMAR', NULL, 'FULL_TIME', 'ACTIVE', 11000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('31', 'BALRAM KUMAR', NULL, 'FULL_TIME', 'ACTIVE', 12000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('32', 'RAJEEV PANDIT', NULL, 'FULL_TIME', 'ACTIVE', 15615.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('33', 'CHANDAN KUMAR', NULL, 'FULL_TIME', 'ACTIVE', 10640.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('36', 'AMIT KUMAR SINGH', NULL, 'FULL_TIME', 'ACTIVE', 8500.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('37', 'RANJAY KUMAR PANDAY', NULL, 'FULL_TIME', 'ACTIVE', 10200.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('42', 'MANOJ KUMAR DAS', NULL, 'FULL_TIME', 'ACTIVE', 10220.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('43', 'SURAJ KUMAR SWEEPER', NULL, 'FULL_TIME', 'ACTIVE', 5000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('44', 'AZAM', NULL, 'FULL_TIME', 'ACTIVE', 0.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('45', 'RAJIK', NULL, 'FULL_TIME', 'ACTIVE', 0.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('46', 'SATENDRA KUMAR', NULL, 'FULL_TIME', 'ACTIVE', 380.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('47', 'BHUSHAN', NULL, 'FULL_TIME', 'ACTIVE', 0.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('49', 'VIJENDRA KUMAR', NULL, 'FULL_TIME', 'ACTIVE', 28000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('53', 'AMIT KUMAR', NULL, 'FULL_TIME', 'ACTIVE', 8500.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('61', 'PAWAN KUMAR PRESSMAN', NULL, 'FULL_TIME', 'ACTIVE', 0.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('67', 'RAVI RANJAN KR', NULL, 'FULL_TIME', 'ACTIVE', 8500.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('69', 'LALU KUMAR', NULL, 'FULL_TIME', 'ACTIVE', 8500.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('70', 'MITHLESH KUMAR', NULL, 'FULL_TIME', 'ACTIVE', 7500.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('71', 'VIJAY KUMAR PRESSMAN', NULL, 'FULL_TIME', 'ACTIVE', 0.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('85', 'VISHANU KUMAR', NULL, 'FULL_TIME', 'ACTIVE', 0.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('DELHI', 'GOPAL KUMAR DELHI', NULL, 'FULL_TIME', 'ACTIVE', 8000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),

-- ========================
-- LADIES STAFF (L suffix)
-- ========================
('05L', 'PRIYESHI KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 5320.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('08L', 'MADHU KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 5320.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('09L', 'RUPAM KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 5700.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('11L', 'KANCHAN DEVI', NULL, 'FULL_TIME', 'ACTIVE', 5880.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('12L', 'SIMA DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('14L', 'ARPANA DEVI', NULL, 'FULL_TIME', 'ACTIVE', 5880.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('15L', 'SABINA KHATOON', NULL, 'FULL_TIME', 'ACTIVE', 5320.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('16L', 'PUJA KUMARI 1', NULL, 'FULL_TIME', 'ACTIVE', 4600.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('17L', 'SIMA DEVI 2', NULL, 'FULL_TIME', 'ACTIVE', 5320.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('18L', 'SIMA KUMARI VERMA', NULL, 'FULL_TIME', 'ACTIVE', 5320.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('19L', 'KOMAL KUMARI CHOTI', NULL, 'FULL_TIME', 'ACTIVE', 4760.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('20L', 'HEMA KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 5880.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('21L', 'PRITI DEVI', NULL, 'FULL_TIME', 'ACTIVE', 5320.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('22L', 'KHUSHI KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('23L', 'SANTU KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 5320.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('31L', 'SUPRITI DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4600.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('37L', 'AMRITA DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4760.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('38L', 'KUNDAN DEVI', NULL, 'FULL_TIME', 'ACTIVE', 5880.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('40L', 'PUTUL DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('43L', 'RANJU DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4760.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('58L', 'NEELAM KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 3500.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('59L', 'ROSHNI KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 3733.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('67L', 'REETA KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 5320.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('74L', 'PRIYANKA KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 3500.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('75L', 'PARWATI DEVI', NULL, 'FULL_TIME', 'ACTIVE', 3500.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('76L', 'MADHURI DEVI', NULL, 'FULL_TIME', 'ACTIVE', 3500.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('77L', 'SITA KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('84L', 'CHOTI KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('87L', 'MINA KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('88L', 'SWETA KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('89L', 'AMISHA KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('90L', 'SWATI KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('91L', 'FRUTI KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('92L', 'MINKI KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('94L', 'RUPAM KUMARI 2', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('96L', 'KALPANA KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('97L', 'KOMAL KUMARI 2', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('100L', 'NISHA KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('102L', 'NEETU DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('105L', 'KOMAL KUMARI 3', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('109L', 'PRIYANKA DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('111L', 'TULSI DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('112L', 'PRIYANKA DEVI 2', NULL, 'FULL_TIME', 'ACTIVE', 3000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('113L', 'SANGITA DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('114L', 'SONI DEVI MEHUS', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('115L', 'JYOTI KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4620.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('116L', 'KIRAN KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('117L', 'LALITA DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('118L', 'NISHA KUMARI 2', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('119L', 'SHIVANI KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('120L', 'RUBI DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('121L', 'SARITA DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('122L', 'PUJA KUMARI TOLA', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('123L', 'SONI DEVI CHITORA', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('124L', 'RINA DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('125L', 'KANCHAN KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('126L', 'ANJALI KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('127L', 'CHOTI KUMARI 2', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('128L', 'SAPNA KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('129L', 'DOLI KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('130L', 'RAGANI KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('L1', 'NITU KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 3000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('L2', 'MUSKAN KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 3000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),

-- ========================
-- FINISHING STAFF (F suffix)
-- ========================
('14F', 'PAMPI DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('17F', 'SUDDI DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('22F', 'SHARDA DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('23F', 'SUNITA DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4500.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('24F', 'MINKI DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('26F', 'SIMPI DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('27F', 'KAVITA DEVI', NULL, 'FULL_TIME', 'ACTIVE', 3500.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('54F', 'BOBY KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('55F', 'GURIYA DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('59F', 'ANITA KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('62F', 'NIRMALA DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('63F', 'KANCHAN DEVI 2', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('66F', 'PRITY KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('73F', 'DROPADI KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('83F', 'KANCHAN DEVI 3', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('84F', 'TUNNI DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('86F', 'RADHA KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('87F', 'ANISHA DEVI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('88F', 'KHUSHI KUMARI 2', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('89F', 'LALITA KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0),
('90F', 'SHILA KUMARI', NULL, 'FULL_TIME', 'ACTIVE', 4000.00, 'MONTHLY', 26, 8, 0, 1, 1, 1, 0);

-- ============================================================
-- STEP 3: Verify the insert
-- ============================================================

SELECT 
    COUNT(*) AS total_employees,
    SUM(CASE WHEN emp_code LIKE '%L' THEN 1 ELSE 0 END) AS ladies_staff,
    SUM(CASE WHEN emp_code LIKE '%F' THEN 1 ELSE 0 END) AS finishing_staff,
    SUM(CASE WHEN emp_code NOT LIKE '%L' AND emp_code NOT LIKE '%F' THEN 1 ELSE 0 END) AS gents_staff,
    SUM(base_salary) AS total_salary_expense
FROM employees;

-- Show all employees ordered by emp_code
SELECT 
    id,
    emp_code,
    first_name,
    last_name,
    base_salary,
    employment_type,
    status
FROM employees
ORDER BY emp_code;

-- ============================================================
-- END OF SCRIPT
-- ============================================================
