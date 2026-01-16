# HRMS Authentication Guide

## Overview

The HRMS application uses **JWT (JSON Web Token)** based authentication with refresh tokens for secure, stateless authentication.

## Security Features

| Feature | Implementation |
|---------|---------------|
| **Password Hashing** | BCrypt (cost factor 12) |
| **Access Token** | JWT, 15 min expiry, HS512 signature |
| **Refresh Token** | UUID, 7 days, HTTP-only cookie |
| **Rate Limiting** | 10 login attempts/min per IP |
| **Account Lockout** | 5 failed attempts = 15 min lockout |
| **CSRF Protection** | SameSite=Strict cookies |
| **XSS Protection** | HTTP-only cookies, CSP headers |
| **Audit Logging** | All login attempts logged |

---

## Default Users

After starting the application, these users are automatically created:

| Email | Password | Role | Access |
|-------|----------|------|--------|
| `superadmin@hrms.in` | `SuperAdmin@123` | SUPER_ADMIN | Manage tenants, all access |
| `admin@hrms.in` | `Admin@123` | ADMIN | Full tenant access |
| `hr@hrms.in` | `HrManager@123` | HR_MANAGER | Employees, attendance, leaves, payroll |
| `accountant@hrms.in` | `Accountant@123` | ACCOUNTANT | Payroll, loans, reports |

---

## API Endpoints

### Public Endpoints (No Auth Required)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/login` | Login with email/password |
| POST | `/api/auth/refresh` | Refresh access token |
| POST | `/api/auth/logout` | Logout (revoke refresh token) |
| GET | `/api/auth/health` | Health check |

### Protected Endpoints

| Method | Endpoint | Description | Required Role |
|--------|----------|-------------|---------------|
| GET | `/api/auth/me` | Get current user info | Any authenticated |
| POST | `/api/auth/change-password` | Change password | Any authenticated |
| POST | `/api/auth/register` | Register new user | ADMIN |
| POST | `/api/auth/logout-all` | Logout all sessions | Any authenticated |

---

## Login Flow

### 1. Login Request

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: ORG001" \
  -d '{
    "email": "admin@hrms.in",
    "password": "Admin@123"
  }'
```

### 2. Successful Response

```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "userId": 2,
  "email": "admin@hrms.in",
  "name": "Admin User",
  "tenantId": "ORG001",
  "tenantName": "Default Organization",
  "role": "ADMIN"
}
```

Also sets HTTP-only cookie: `refreshToken=<uuid>`

### 3. Use Access Token

```bash
curl http://localhost:8080/api/employees \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..." \
  -H "X-Tenant-Id: ORG001"
```

### 4. Refresh Token (before access token expires)

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "X-Tenant-Id: ORG001" \
  --cookie "refreshToken=<uuid>"
```

---

## User Roles & Permissions

```
SUPER_ADMIN
├── Manage all tenants
├── Create/delete tenants
├── View all data across tenants
└── All permissions below

ADMIN
├── Manage users within tenant
├── Full access to all modules
└── All permissions below

HR_MANAGER
├── Employee management
├── Attendance management
├── Leave management
├── Payroll processing
└── View reports

ACCOUNTANT
├── Payroll processing (read-only employees)
├── Loan management
├── Financial reports
└── Bank reconciliation

MANAGER
├── View team attendance
├── Approve team leaves
└── View team reports

EMPLOYEE
├── View own profile
├── View own attendance
├── Apply for leave
└── View own payslips
```

---

## Creating New Users

### Via API (Admin only)

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin-token>" \
  -H "X-Tenant-Id: ORG001" \
  -d '{
    "email": "newuser@company.com",
    "password": "SecurePass@123",
    "firstName": "John",
    "lastName": "Doe",
    "role": "EMPLOYEE",
    "employeeId": 5
  }'
```

### Via Database

```sql
INSERT INTO users (tenant_id, email, password_hash, first_name, last_name, role, is_active)
VALUES (
  'ORG001',
  'newuser@company.com',
  '$2a$12$...', -- BCrypt hash of password
  'John',
  'Doe',
  'EMPLOYEE',
  TRUE
);
```

---

## Creating a New Company/Customer (Tenant)

### Step 1: Create Tenant

```bash
curl -X POST http://localhost:8080/api/tenants \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <superadmin-token>" \
  -d '{
    "id": "SASA001",
    "name": "Sasa Collection Pvt Ltd",
    "subdomain": "sasacollection",
    "email": "hr@sasacollection.com",
    "phone": "+91-9876543210",
    "city": "Mumbai",
    "state": "Maharashtra",
    "plan": "PRO",
    "maxEmployees": 200
  }'
```

### Step 2: Create Admin User for Tenant

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <superadmin-token>" \
  -H "X-Tenant-Id: SASA001" \
  -d '{
    "email": "admin@sasacollection.com",
    "password": "SasaAdmin@123",
    "firstName": "Sasa",
    "lastName": "Admin",
    "role": "ADMIN",
    "tenantId": "SASA001"
  }'
```

### Step 3: Access via Subdomain

In production: `https://sasacollection.hrms.in`

For development, set header: `X-Tenant-Id: SASA001`

---

## Security Best Practices

### For Production

1. **Change JWT Secret**
   ```properties
   jwt.secret=your-super-secure-random-key-at-least-64-characters-long
   ```

2. **Use HTTPS Only**
   - All cookies are set with `Secure` flag
   - HSTS header enabled

3. **Strong Password Policy**
   - Minimum 8 characters
   - Recommend: uppercase, lowercase, number, special char

4. **Monitor Login Attempts**
   - Check `login_audit` table regularly
   - Alert on multiple failures from same IP

5. **Token Cleanup Job**
   ```sql
   -- Run daily
   DELETE FROM refresh_tokens WHERE expires_at < NOW();
   DELETE FROM login_audit WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY);
   ```

---

## Troubleshooting

### "Invalid credentials" error
- Check email/password
- Verify tenant ID in X-Tenant-Id header
- Check if account is active in database

### "Account locked" error
- Wait 15 minutes or unlock manually:
  ```sql
  UPDATE users SET is_locked = FALSE, failed_attempts = 0, lock_time = NULL
  WHERE email = 'user@email.com';
  ```

### "Token expired" error
- Frontend should auto-refresh before expiry
- Call `/api/auth/refresh` endpoint
- If refresh fails, user must login again

### CORS errors
- Verify `cors.allowed-origins` in application.properties
- Include your frontend URL

---

## Testing Authentication

```bash
# 1. Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: ORG001" \
  -d '{"email":"admin@hrms.in","password":"Admin@123"}' | jq -r '.accessToken')

echo "Access Token: $TOKEN"

# 2. Access protected endpoint
curl http://localhost:8080/api/employees \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: ORG001"

# 3. Get current user
curl http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: ORG001"
```
