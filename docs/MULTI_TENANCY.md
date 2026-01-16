# Multi-Tenancy Guide for HRMS

This document explains how multi-tenancy is implemented in the HRMS application.

## Overview

We use the **Shared Database, Shared Schema** approach where:
- All tenants share the same database and tables
- Each row has a `tenant_id` column for data isolation
- Tenant is identified from request (subdomain, header, or query param)

## Architecture

```
┌──────────────────────────────────────────────────┐
│                   Frontend                        │
│   (React app at sasacollection.hrms.in)         │
│                                                  │
│   ┌─────────────────────────────────────────┐   │
│   │  X-Tenant-Id header sent with requests  │   │
│   └─────────────────────────────────────────┘   │
└──────────────────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────┐
│                TenantFilter                       │
│   Extracts tenant from:                          │
│   1. Subdomain (sasacollection.hrms.in → SASA)  │
│   2. X-Tenant-Id header                         │
│   3. tenantId query param                        │
│   Sets TenantContext.setTenantId(...)           │
└──────────────────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────┐
│             Service Layer                         │
│   Uses TenantContext.getTenantId() for:         │
│   - Database queries with tenant_id filter      │
│   - Setting tenant_id on new records            │
└──────────────────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────┐
│               Shared Database                     │
│   ┌────────────────────────────────────────┐    │
│   │  employees        │ tenant_id = 'SASA' │    │
│   │  shifts           │ tenant_id = 'SASA' │    │
│   │  attendance_day   │ tenant_id = 'SASA' │    │
│   │  payroll          │ tenant_id = 'SASA' │    │
│   │  ...              │ ...                │    │
│   └────────────────────────────────────────┘    │
└──────────────────────────────────────────────────┘
```

## Creating a New Tenant

### Option 1: Direct Database Insert

```sql
INSERT INTO tenant (
    id,
    name,
    subdomain,
    email,
    phone,
    city,
    state,
    plan,
    max_employees,
    is_active
) VALUES (
    'SASA001',                           -- Unique tenant ID
    'Sasa Collection Pvt Ltd',           -- Company name
    'sasacollection',                    -- Subdomain (URL: sasacollection.hrms.in)
    'hr@sasacollection.com',             -- Admin email
    '+91-9876543210',                    -- Phone
    'Mumbai',                            -- City
    'Maharashtra',                       -- State
    'PRO',                               -- Plan: FREE, BASIC, PRO, ENTERPRISE
    200,                                 -- Max employees allowed
    TRUE                                 -- Active status
);
```

### Option 2: Using the API

```bash
curl -X POST http://localhost:8080/api/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "id": "SASA001",
    "name": "Sasa Collection Pvt Ltd",
    "subdomain": "sasacollection",
    "email": "hr@sasacollection.com",
    "phone": "+91-9876543210",
    "city": "Mumbai",
    "state": "Maharashtra",
    "plan": "PRO",
    "maxEmployees": 200,
    "isActive": true
  }'
```

### Option 3: Using Java Service (Recommended for scripts)

```java
@Autowired
private TenantService tenantService;

public void createSampleTenant() {
    Tenant tenant = Tenant.builder()
        .id("SASA001")
        .name("Sasa Collection Pvt Ltd")
        .subdomain("sasacollection")
        .email("hr@sasacollection.com")
        .phone("+91-9876543210")
        .city("Mumbai")
        .state("Maharashtra")
        .plan("PRO")
        .maxEmployees(200)
        .isActive(true)
        .build();
    
    tenantService.createTenant(tenant);
}
```

## Tenant Plans

| Plan       | Max Employees | Description               |
|------------|---------------|---------------------------|
| FREE       | 10            | For small businesses      |
| BASIC      | 50            | Growing companies         |
| PRO        | 200           | Medium enterprises        |
| ENTERPRISE | 10,000+       | Large organizations       |

## URL Structure

In production, tenants access via subdomain:
- `sasacollection.hrms.in` → Tenant SASA001
- `techcorp.hrms.in` → Tenant TECH001

For development, use:
- Header: `X-Tenant-Id: SASA001`
- Query param: `?tenantId=SASA001`
- LocalStorage: `localStorage.setItem('hrms_tenant_id', 'SASA001')`

## DNS and Nginx Setup (Production)

### 1. Wildcard DNS
Point `*.hrms.in` to your server IP.

### 2. Nginx Configuration

```nginx
server {
    listen 80;
    server_name *.hrms.in;

    # Frontend (React)
    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # Backend API
    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## Database Migration

Run the migration script to add multi-tenancy support:

```bash
mysql -u root -p hrmsdb < scripts/V2__add_multitenancy.sql
```

## Testing Multi-Tenancy

1. Create two tenants (e.g., SASA001 and TECH001)
2. Switch between them using X-Tenant-Id header
3. Create employees in each tenant
4. Verify data isolation - employees from SASA001 should not appear in TECH001

```bash
# Create employee for SASA001
curl -X POST http://localhost:8080/api/employees \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: SASA001" \
  -d '{"empCode": "EMP001", "firstName": "John", "lastName": "Doe"}'

# Create employee for TECH001
curl -X POST http://localhost:8080/api/employees \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: TECH001" \
  -d '{"empCode": "EMP001", "firstName": "Jane", "lastName": "Smith"}'

# List employees for SASA001 - should only show John Doe
curl http://localhost:8080/api/employees -H "X-Tenant-Id: SASA001"

# List employees for TECH001 - should only show Jane Smith
curl http://localhost:8080/api/employees -H "X-Tenant-Id: TECH001"
```

## Security Notes

1. **Data Isolation**: All queries automatically filter by tenant_id
2. **Cross-Tenant Access**: Prevented at service layer
3. **Subscription Validation**: Checked in TenantFilter
4. **Employee Limits**: Enforced per plan

## Troubleshooting

### Tenant not resolving
- Check if subdomain is correct
- Verify X-Tenant-Id header is being sent
- Check TenantFilter logs

### Data appearing across tenants
- Ensure all services use TenantContext
- Verify tenant_id is set on new records
- Check repository queries include tenant filter
