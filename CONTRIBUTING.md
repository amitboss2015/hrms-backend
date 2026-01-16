# Contributing to HRMS Backend

Thank you for your interest in contributing to HRMS Backend! This document provides guidelines and steps for contributing.

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Coding Standards](#coding-standards)
- [Submitting Changes](#submitting-changes)
- [Reporting Issues](#reporting-issues)

---

## Code of Conduct

Please be respectful and inclusive. We welcome contributors from all backgrounds and experience levels.

---

## Getting Started

1. **Fork** the repository on GitHub
2. **Clone** your fork locally
3. **Create a branch** for your changes
4. **Make changes** and test thoroughly
5. **Submit a Pull Request**

---

## Development Setup

### Prerequisites
- Java 17+
- Maven 3.6+
- MySQL 8.x
- IDE (IntelliJ IDEA recommended)

### Setup Steps

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/hrms-backend.git
cd hrms-backend

# Create a branch
git checkout -b feature/your-feature

# Configure database
# Edit src/main/resources/application.properties

# Run the application
mvn spring-boot:run

# Run tests
mvn test
```

---

## Project Structure

```
src/main/java/com/example/hrms/
│
├── attendance/              # Attendance Module
│   ├── controller/          # REST Controllers
│   │   ├── AttendanceImportController.java
│   │   └── AttendanceQueryController.java
│   ├── domain/              # JPA Entities
│   │   ├── AttendanceDay.java
│   │   ├── AttendancePunch.java
│   │   └── ...
│   ├── dto/                 # Data Transfer Objects
│   │   ├── DailyPunchLogDTO.java
│   │   └── MonthlySummaryDTO.java
│   ├── repo/                # JPA Repositories
│   └── service/             # Business Logic
│       ├── AttendanceEngine.java (interface)
│       └── impl/
│           ├── AttendanceEngineImpl.java
│           └── AttendanceImportServiceImpl.java
│
├── config/                  # Spring Configuration
├── domain/                  # Core Entities
│   ├── Employee.java
│   ├── Shift.java
│   └── enums/
├── leave/                   # Leave Module
├── loan/                    # Loan Module
├── payroll/                 # Payroll Module
├── repo/                    # Core Repositories
├── report/                  # Reporting Module
├── service/                 # Core Services
└── web/                     # Core Controllers
```

---

## Coding Standards

### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Classes | PascalCase | `EmployeeService` |
| Methods | camelCase | `calculatePayroll()` |
| Variables | camelCase | `employeeList` |
| Constants | UPPER_SNAKE | `MAX_RETRIES` |
| Packages | lowercase | `com.example.hrms` |

### Code Style

```java
// Good: Clear, descriptive names
public void calculateMonthlyPayroll(Employee employee, int month, int year) {
    // Implementation
}

// Bad: Cryptic names
public void calc(Employee e, int m, int y) {
    // Implementation
}
```

### Comments

```java
/**
 * Calculates the total work hours for an employee in a given month.
 * 
 * @param empCode Employee code
 * @param month Month (1-12)
 * @param year Year (e.g., 2025)
 * @return Total work minutes
 */
public int calculateWorkHours(String empCode, int month, int year) {
    // Implementation
}

// Use inline comments for complex logic
int roundedMinutes = (actualMinutes / 15) * 15;  // Round down to nearest 15
```

### Entity Guidelines

```java
@Entity
@Table(name = "your_table")
@Data  // Lombok
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YourEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 50)
    private String code;
    
    // Use appropriate column annotations
}
```

### Service Guidelines

```java
@Service
@Transactional
@RequiredArgsConstructor
public class YourServiceImpl implements YourService {
    
    private final YourRepository repository;
    
    @Override
    public YourDTO findByCode(String code) {
        return repository.findByCode(code)
            .map(this::toDTO)
            .orElseThrow(() -> new EntityNotFoundException("Not found: " + code));
    }
}
```

---

## Submitting Changes

### Commit Messages

Use descriptive commit messages with prefixes:

```
Add: New feature description
Fix: Bug fix description
Update: Enhancement description
Refactor: Code improvement description
Docs: Documentation update
Test: Test addition/update
```

Examples:
```bash
git commit -m "Add: Overtime calculation for holiday work"
git commit -m "Fix: Weekly off detection for part-time employees"
git commit -m "Update: Improve attendance import performance"
```

### Pull Request Process

1. **Update your branch** with latest main
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run tests** before submitting
   ```bash
   mvn test
   ```

3. **Create Pull Request** with:
   - Clear title describing the change
   - Description of what and why
   - Reference any related issues

4. **Address review comments** promptly

---

## Reporting Issues

### Bug Reports

Include:
- Clear description of the bug
- Steps to reproduce
- Expected vs actual behavior
- Environment details (Java version, OS)
- Logs/stack traces if available

### Feature Requests

Include:
- Clear description of the feature
- Use case / why it's needed
- Any implementation ideas (optional)

---

## Areas Where Help is Needed

- 📝 **Documentation**: Improve API docs, add examples
- 🧪 **Testing**: Add unit tests, integration tests
- 🐛 **Bug Fixes**: Check open issues
- ✨ **Features**: Check feature requests
- 🌐 **i18n**: Internationalization support
- 📊 **Reporting**: New report types

---

## Questions?

Feel free to open an issue for any questions!

Thank you for contributing! 🙏
