# 🏢 HRMS Backend

**Human Resource Management System** - A comprehensive Spring Boot backend API for managing employees, attendance, payroll, leaves, and loans.

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green)
![MySQL](https://img.shields.io/badge/MySQL-8.x-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

---

## 📋 Table of Contents

- [Features](#-features)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Getting Started](#-getting-started)
- [API Documentation](#-api-documentation)
- [Database Schema](#-database-schema)
- [Contributing](#-contributing)
- [License](#-license)

---

## ✨ Features

### 👥 Employee Management
- Complete employee CRUD operations
- Bulk upload via Excel
- Multiple employment types (Full-time, Part-time, Contract)
- Employee status tracking (Active, Inactive, Resigned)

### 🕐 Shift Management
- Flexible shift configuration
- Start/End time with overnight shift support
- Grace period for IN/OUT
- Rounding rules (Nearest 5, 15, 30 minutes)
- Break time configuration
- Shift assignment to employees with date ranges

### ✅ Attendance Tracking
- Excel import from biometric systems
- Cross-midnight punch handling
- Dual shift support (multiple IN/OUT per day)
- Automatic status detection:
  - PRESENT / ABSENT / HALF_DAY
  - WEEKLY_OFF / HOLIDAY / OT_DAY (Overtime)
- Late IN / Early OUT detection with penalty rounding
- Work hours calculation with break deduction

### 📝 Leave Management
- Configurable leave types
- Leave balance tracking
- Leave request workflow
- Leave reports and calendar view

### 💰 Payroll Generation
- Monthly payroll calculation
- ESI/PF statutory deductions
- Advance/Due management
- Loan EMI integration
- Multi-status workflow: DRAFT → APPROVED → PAID
- Salary sheets for paid employees

### 💳 Loan Management
- Employee loan tracking
- EMI calculation
- Automatic payroll deduction
- Loan reports

### 🎉 Holiday Configuration
- Yearly holiday calendar
- Weekly off configuration per employment type
- Alternate Saturday rules
- Optional/Restricted holidays

---

## 🏗 Architecture

```
src/main/java/com/example/hrms/
├── attendance/
│   ├── controller/     # REST endpoints for attendance
│   ├── domain/         # Entities (AttendanceDay, AttendancePunch, etc.)
│   ├── dto/            # Data Transfer Objects
│   ├── repo/           # JPA Repositories
│   └── service/        # Business logic
│       └── impl/       # Service implementations
├── config/             # Configuration classes
├── domain/             # Core entities (Employee, Shift, Holiday)
│   └── enums/          # Enumerations
├── leave/              # Leave management module
├── loan/               # Loan management module
├── payroll/            # Payroll generation module
├── repo/               # Core repositories
├── report/             # Reporting module
├── service/            # Core services
│   └── excel/          # Excel processing
└── web/                # Core REST controllers
```

---

## 🛠 Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 17 | Programming language |
| Spring Boot | 3.x | Application framework |
| Spring Data JPA | - | Database ORM |
| MySQL | 8.x | Relational database |
| Maven | 3.x | Build tool |
| Apache POI | - | Excel file processing |
| Lombok | - | Boilerplate reduction |

---

## 🚀 Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- MySQL 8.x
- IDE (IntelliJ IDEA / Eclipse / VS Code)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/amitboss2015/hrms-backend.git
   cd hrms-backend
   ```

2. **Create MySQL database**
   ```sql
   CREATE DATABASE hrms;
   ```

3. **Configure database connection**
   
   Edit `src/main/resources/application.properties`:
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/hrms
   spring.datasource.username=your_username
   spring.datasource.password=your_password
   ```

4. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

5. **Verify**
   
   API will be available at: `http://localhost:8080/api`

### Using Environment Variables (Recommended for Production)

```properties
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/hrms}
spring.datasource.username=${DB_USER:root}
spring.datasource.password=${DB_PASSWORD:}
```

---

## 📚 API Documentation

### Base URL
```
http://localhost:8080/api
```

### Endpoints Overview

| Module | Endpoint | Description |
|--------|----------|-------------|
| **Employees** | `GET /employees` | List all employees |
| | `POST /employees` | Create employee |
| | `GET /employees/{code}` | Get by employee code |
| | `PUT /employees/{code}` | Update employee |
| | `DELETE /employees/{code}` | Delete employee |
| | `POST /employees/bulk-upload` | Bulk import |
| **Shifts** | `GET /shifts` | List all shifts |
| | `POST /shifts` | Create shift |
| | `PUT /shifts/{code}` | Update shift |
| | `DELETE /shifts/{code}` | Delete shift |
| **Shift Assignment** | `GET /employee-shifts/by-shift/{code}` | Get assignments |
| | `POST /employee-shifts/bulk` | Bulk assign |
| **Attendance** | `POST /attendance/import` | Import Excel |
| | `GET /attendance/daily` | Daily punch logs |
| | `GET /attendance/summary` | Monthly summary |
| | `POST /attendance/recalculate` | Recalculate month |
| **Payroll** | `POST /payroll/generate` | Generate payroll |
| | `GET /payroll` | Get payrolls |
| | `PUT /payroll/{id}/pay` | Mark as paid |
| | `GET /payroll/skipped` | Skipped employees |
| **Leaves** | `GET /leaves/types` | Leave types |
| | `POST /leaves/mark` | Mark leave |
| | `GET /leaves/balance` | Leave balances |
| **Loans** | `GET /loans` | List loans |
| | `POST /loans` | Create loan |
| | `GET /loans/{id}` | Loan details |
| **Holidays** | `GET /holidays` | List holidays |
| | `POST /holidays` | Create holiday |
| | `GET /weekly-off` | Weekly off config |

---

## 🗄 Database Schema

### Key Entities

```
Employee
├── id, empCode, firstName, lastName
├── email, phone, address, city, state, pincode
├── department, designation, employmentType
├── salaryBasis, baseSalary, hourlyRate
├── bankAccount, ifsc, bankName
├── aadhaar, pan
├── joinDate, status
└── otAllowed, otDurationMinutes

Shift
├── id, code, name
├── startTime, endTime
├── breakMins, graceInMins, graceOutMins
├── rounding (NONE, NEAREST_5, NEAREST_15, NEAREST_30)
├── halfdayThresholdMins, minWorkMins
├── mon, tue, wed, thu, fri, sat, sun
└── effectiveFrom, effectiveTo, active

AttendanceDay
├── employeeId, workDate
├── status (PRESENT, ABSENT, HALF_DAY, WEEKLY_OFF, HOLIDAY, OT_DAY)
├── firstIn, lastOut, workMinutes
├── roundedIn, roundedOut
├── isLateIn, isEarlyOut, lateByMins, earlyByMins
├── isWeeklyOff, isHoliday, holidayName
└── isOvertimeDay, overtimeOnHolidayMins

Payroll
├── employeeId, year, month
├── grossSalary, netSalary
├── basicSalary, hra, otherAllowances
├── esiEmployee, pfEmployee, professionalTax, tds
├── advance, due, loanDeduction
├── bonus, incentive, otherDeduction
├── status (DRAFT, APPROVED, PAID)
└── paidAt, paymentMode, transactionReference
```

---

## 🤝 Contributing

We welcome contributions! Please follow these steps:

1. **Fork the repository**

2. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Make your changes**
   - Follow existing code style
   - Add comments for complex logic
   - Update documentation if needed

4. **Commit with descriptive message**
   ```bash
   git commit -m "Add: description of your changes"
   ```
   
   Commit message prefixes:
   - `Add:` - New feature
   - `Fix:` - Bug fix
   - `Update:` - Enhancement
   - `Refactor:` - Code refactoring
   - `Docs:` - Documentation

5. **Push and create Pull Request**
   ```bash
   git push origin feature/your-feature-name
   ```

### Code Guidelines

- Use meaningful variable/method names
- Follow Java naming conventions
- Add Javadoc for public methods
- Write unit tests for new features
- Keep methods focused and small

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 👤 Author

**Amit Kumar**
- GitHub: [@amitboss2015](https://github.com/amitboss2015)

---

## 🙏 Acknowledgments

- Spring Boot team for the amazing framework
- All contributors who help improve this project

---

⭐ **Star this repo if you find it helpful!**
