# AWS Free Tier Deployment Guide - ChandraHR HRMS

This guide will help you deploy your HRMS application (Spring Boot backend + React frontend) on AWS Free Tier.

## 🎯 Architecture Overview

For AWS Free Tier with 1-2 users, we'll use:

```
┌─────────────────────────────────────────────────────────────┐
│                        AWS Cloud                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                  EC2 t2.micro (Free Tier)            │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌───────────┐  │   │
│  │  │   Nginx      │  │  Spring Boot │  │   MySQL   │  │   │
│  │  │  (Frontend   │  │   Backend    │  │   8.0     │  │   │
│  │  │   + Proxy)   │  │   :8080      │  │   :3306   │  │   │
│  │  │   :80/:443   │  └──────────────┘  └───────────┘  │   │
│  │  └──────────────┘                                    │   │
│  └─────────────────────────────────────────────────────┘   │
│                           ↑                                 │
│                    Elastic IP                               │
└─────────────────────────────────────────────────────────────┘
```

## 📋 Prerequisites

- AWS Account with Free Tier eligibility
- Domain name (optional, but recommended for SSL)
- SSH key pair for EC2 access

---

## 🚀 Step 1: Launch EC2 Instance

### 1.1 Go to AWS Console → EC2 → Launch Instance

### 1.2 Configure Instance:
- **Name**: `chandraHR-server`
- **AMI**: Ubuntu 22.04 LTS (Free Tier eligible)
- **Instance Type**: `t2.micro` (Free Tier - 750 hours/month)
- **Key Pair**: Create new or use existing
- **Network Settings**:
  - Allow SSH (port 22)
  - Allow HTTP (port 80)
  - Allow HTTPS (port 443)
  - Allow Custom TCP (port 8080) - for API

### 1.3 Storage:
- **Root Volume**: 20 GB gp3 (Free Tier: 30 GB total)

### 1.4 Launch and note your Public IPv4 address

### 1.5 Allocate Elastic IP (recommended):
- EC2 → Elastic IPs → Allocate
- Associate with your instance
- This gives you a static IP

---

## 🔧 Step 2: Connect and Setup Server

### 2.1 Connect via SSH
```bash
chmod 400 your-key.pem
ssh -i your-key.pem ubuntu@YOUR_EC2_PUBLIC_IP
```

### 2.2 Update System
```bash
sudo apt update && sudo apt upgrade -y
```

### 2.3 Install Java 17
```bash
sudo apt install openjdk-17-jdk -y
java -version
```

### 2.4 Install MySQL 8.0
```bash
sudo apt install mysql-server -y
sudo systemctl start mysql
sudo systemctl enable mysql

# Secure MySQL installation
sudo mysql_secure_installation
# Follow prompts: Set root password, remove anonymous users, etc.
```

### 2.5 Configure MySQL
```bash
sudo mysql -u root -p
```

Run these SQL commands:
```sql
-- Create database
CREATE DATABASE payroll_hrms;

-- Create application user
CREATE USER 'hrms_app'@'localhost' IDENTIFIED BY 'YourStrongPassword123!';
GRANT ALL PRIVILEGES ON payroll_hrms.* TO 'hrms_app'@'localhost';
FLUSH PRIVILEGES;

EXIT;
```

### 2.6 Install Nginx
```bash
sudo apt install nginx -y
sudo systemctl start nginx
sudo systemctl enable nginx
```

### 2.7 Install Node.js (for building frontend)
```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install nodejs -y
node -v
npm -v
```

---

## 📦 Step 3: Deploy Backend

### 3.1 Create application directory
```bash
sudo mkdir -p /opt/hrms
sudo chown ubuntu:ubuntu /opt/hrms
cd /opt/hrms
```

### 3.2 Build JAR locally and transfer
On your local machine:
```bash
cd "/Users/amit.kumar/Documents/study_Material/workspace/EmployeeAttendance/EABackend/final/hrms-backend 2"

# Build the JAR
./mvnw clean package -DskipTests

# Transfer to server
scp -i your-key.pem target/hrms-backend-0.0.1-SNAPSHOT.jar ubuntu@YOUR_EC2_IP:/opt/hrms/
```

### 3.3 Create production configuration
On EC2 server, create `/opt/hrms/application-prod.properties`:

```bash
nano /opt/hrms/application-prod.properties
```

Add the content (see `application-prod.properties` file in this repo).

### 3.4 Create systemd service
```bash
sudo nano /etc/systemd/system/hrms.service
```

Add:
```ini
[Unit]
Description=ChandraHR HRMS Backend
After=syslog.target network.target mysql.service

[Service]
User=ubuntu
WorkingDirectory=/opt/hrms
ExecStart=/usr/bin/java -jar -Dspring.profiles.active=prod -Xmx512m /opt/hrms/hrms-backend-0.0.1-SNAPSHOT.jar
SuccessExitStatus=143
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### 3.5 Start backend service
```bash
sudo systemctl daemon-reload
sudo systemctl start hrms
sudo systemctl enable hrms
sudo systemctl status hrms

# View logs
sudo journalctl -u hrms -f
```

---

## 🎨 Step 4: Deploy Frontend

### 4.1 Build frontend locally
On your local machine:
```bash
cd /Users/amit.kumar/Documents/study_Material/workspace/attendance-ui-auth

# Create production .env
echo "VITE_API_BASE_URL=https://your-domain.com/api" > .env.production
# OR if no domain: echo "VITE_API_BASE_URL=http://YOUR_EC2_IP/api" > .env.production

# Build
npm install
npm run build
```

### 4.2 Transfer to server
```bash
scp -i your-key.pem -r dist/* ubuntu@YOUR_EC2_IP:/tmp/frontend/
```

### 4.3 Setup on server
```bash
sudo mkdir -p /var/www/hrms
sudo cp -r /tmp/frontend/* /var/www/hrms/
sudo chown -R www-data:www-data /var/www/hrms
```

---

## 🌐 Step 5: Configure Nginx

### 5.1 Create Nginx configuration
```bash
sudo nano /etc/nginx/sites-available/hrms
```

Add:
```nginx
server {
    listen 80;
    server_name YOUR_DOMAIN_OR_IP;

    # Frontend - React SPA
    root /var/www/hrms;
    index index.html;

    # Gzip compression
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml;

    # React Router - handle client-side routing
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API Proxy to Spring Boot backend
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # Timeouts for long operations
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # Swagger UI
    location /swagger {
        proxy_pass http://127.0.0.1:8080/swagger;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # OpenAPI docs
    location /api-docs {
        proxy_pass http://127.0.0.1:8080/api-docs;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
    }
}
```

### 5.2 Enable site
```bash
sudo ln -s /etc/nginx/sites-available/hrms /etc/nginx/sites-enabled/
sudo rm /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

---

## 🔒 Step 6: Setup SSL (HTTPS) - Recommended

### 6.1 Install Certbot
```bash
sudo apt install certbot python3-certbot-nginx -y
```

### 6.2 Get SSL Certificate (requires domain)
```bash
sudo certbot --nginx -d your-domain.com
```

Follow prompts and select "Redirect HTTP to HTTPS".

### 6.3 Auto-renewal
```bash
sudo certbot renew --dry-run
```
Certbot auto-creates a cron job for renewal.

---

## 🔄 Step 7: Deployment Script

Create `/opt/hrms/deploy.sh` for easy updates:

```bash
#!/bin/bash
# ChandraHR Deployment Script

echo "🚀 Starting deployment..."

# Stop services
sudo systemctl stop hrms

# Backup current JAR
if [ -f /opt/hrms/hrms-backend-0.0.1-SNAPSHOT.jar ]; then
    mv /opt/hrms/hrms-backend-0.0.1-SNAPSHOT.jar /opt/hrms/hrms-backend-backup.jar
fi

# Copy new JAR (expects it to be uploaded to /tmp/)
if [ -f /tmp/hrms-backend-0.0.1-SNAPSHOT.jar ]; then
    mv /tmp/hrms-backend-0.0.1-SNAPSHOT.jar /opt/hrms/
fi

# Copy new frontend (expects it to be uploaded to /tmp/frontend/)
if [ -d /tmp/frontend ]; then
    sudo rm -rf /var/www/hrms/*
    sudo cp -r /tmp/frontend/* /var/www/hrms/
    sudo chown -R www-data:www-data /var/www/hrms
fi

# Start services
sudo systemctl start hrms

echo "✅ Deployment complete!"
echo "📊 Checking service status..."
sudo systemctl status hrms
```

```bash
chmod +x /opt/hrms/deploy.sh
```

---

## 💰 AWS Free Tier Limits

| Service | Free Tier Limit | Our Usage |
|---------|-----------------|-----------|
| EC2 t2.micro | 750 hours/month | ~720 hours (1 instance 24/7) ✅ |
| EBS Storage | 30 GB | 20 GB ✅ |
| Data Transfer | 100 GB out/month | ~1-2 GB for 1-2 users ✅ |
| Elastic IP | Free when attached | 1 IP attached ✅ |

⚠️ **After 12 months**, Free Tier expires. Estimated cost: ~$10-15/month for t2.micro.

---

## 🔧 Useful Commands

```bash
# View backend logs
sudo journalctl -u hrms -f

# Restart backend
sudo systemctl restart hrms

# Check backend status
sudo systemctl status hrms

# View Nginx logs
sudo tail -f /var/log/nginx/access.log
sudo tail -f /var/log/nginx/error.log

# Restart Nginx
sudo systemctl restart nginx

# Check MySQL
sudo systemctl status mysql

# Database backup
mysqldump -u hrms_app -p payroll_hrms > backup_$(date +%Y%m%d).sql
```

---

## 🚨 Troubleshooting

### Backend not starting
```bash
# Check logs
sudo journalctl -u hrms -n 100

# Check if port 8080 is in use
sudo lsof -i :8080

# Test JAR directly
java -jar /opt/hrms/hrms-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

### Database connection issues
```bash
# Test MySQL connection
mysql -u hrms_app -p payroll_hrms

# Check MySQL is running
sudo systemctl status mysql
```

### Frontend not loading
```bash
# Check Nginx config
sudo nginx -t

# Check Nginx logs
sudo tail -f /var/log/nginx/error.log
```

---

## 🔐 Security Recommendations

1. **Change default passwords** in application-prod.properties
2. **Use strong JWT secret** (at least 256 bits)
3. **Enable UFW firewall**:
   ```bash
   sudo ufw allow ssh
   sudo ufw allow http
   sudo ufw allow https
   sudo ufw enable
   ```
4. **Regular backups** of MySQL database
5. **Keep system updated**: `sudo apt update && sudo apt upgrade`

---

## 📱 After Deployment

Your application will be available at:
- **Frontend**: `http://YOUR_DOMAIN_OR_IP/`
- **API**: `http://YOUR_DOMAIN_OR_IP/api/`
- **Swagger**: `http://YOUR_DOMAIN_OR_IP/swagger`

For 1-2 users on AWS Free Tier, this setup will work perfectly! 🎉
