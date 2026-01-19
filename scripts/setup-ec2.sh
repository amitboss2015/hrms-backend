#!/bin/bash
# ChandraHR EC2 Initial Setup Script
# Run this on a fresh Ubuntu 22.04 EC2 instance

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "🚀 ChandraHR EC2 Setup Script"
echo "=============================="
echo "This script will install all required dependencies"
echo ""

# Check if running as root
if [ "$EUID" -eq 0 ]; then
    echo -e "${RED}Please run as ubuntu user, not root${NC}"
    exit 1
fi

# Step 1: Update system
echo -e "\n${GREEN}📦 Step 1: Updating system...${NC}"
sudo apt update && sudo apt upgrade -y

# Step 2: Install Java 17
echo -e "\n${GREEN}☕ Step 2: Installing Java 17...${NC}"
sudo apt install openjdk-17-jdk -y
java -version

# Step 3: Install MySQL 8.0
echo -e "\n${GREEN}🗄️ Step 3: Installing MySQL 8.0...${NC}"
sudo apt install mysql-server -y
sudo systemctl start mysql
sudo systemctl enable mysql

# Create database and user
echo -e "\n${YELLOW}Creating database and user...${NC}"
echo "Please enter the password you want for the hrms_app database user:"
read -s DB_PASSWORD

sudo mysql << EOF
CREATE DATABASE IF NOT EXISTS payroll_hrms;
CREATE USER IF NOT EXISTS 'hrms_app'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
GRANT ALL PRIVILEGES ON payroll_hrms.* TO 'hrms_app'@'localhost';
FLUSH PRIVILEGES;
EOF

echo -e "${GREEN}Database created successfully!${NC}"

# Step 4: Install Nginx
echo -e "\n${GREEN}🌐 Step 4: Installing Nginx...${NC}"
sudo apt install nginx -y
sudo systemctl start nginx
sudo systemctl enable nginx

# Step 5: Create application directories
echo -e "\n${GREEN}📁 Step 5: Creating application directories...${NC}"
sudo mkdir -p /opt/hrms/logs
sudo mkdir -p /var/www/hrms
sudo chown -R ubuntu:ubuntu /opt/hrms

# Step 6: Create systemd service
echo -e "\n${GREEN}⚙️ Step 6: Creating systemd service...${NC}"
sudo tee /etc/systemd/system/hrms.service > /dev/null << 'EOF'
[Unit]
Description=ChandraHR HRMS Backend
After=syslog.target network.target mysql.service

[Service]
User=ubuntu
WorkingDirectory=/opt/hrms
ExecStart=/usr/bin/java -jar -Dspring.profiles.active=prod -Dspring.config.additional-location=/opt/hrms/application-prod.properties -Xmx512m /opt/hrms/hrms-backend-0.0.1-SNAPSHOT.jar
SuccessExitStatus=143
Restart=always
RestartSec=10

# Logging
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable hrms

# Step 7: Configure Nginx
echo -e "\n${GREEN}🔧 Step 7: Configuring Nginx...${NC}"
sudo tee /etc/nginx/sites-available/hrms > /dev/null << 'EOF'
server {
    listen 80;
    server_name _;

    # Frontend - React SPA
    root /var/www/hrms;
    index index.html;

    # Gzip compression
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml;

    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;

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
        
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
        
        client_max_body_size 10M;
    }

    # Swagger UI
    location /swagger {
        proxy_pass http://127.0.0.1:8080/swagger;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
    }

    location /swagger-ui/ {
        proxy_pass http://127.0.0.1:8080/swagger-ui/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
    }

    location /api-docs {
        proxy_pass http://127.0.0.1:8080/api-docs;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
    }
}
EOF

sudo ln -sf /etc/nginx/sites-available/hrms /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx

# Step 8: Configure firewall
echo -e "\n${GREEN}🔒 Step 8: Configuring firewall...${NC}"
sudo ufw allow ssh
sudo ufw allow http
sudo ufw allow https
sudo ufw --force enable

# Step 9: Install Certbot for SSL (optional)
echo -e "\n${GREEN}🔐 Step 9: Installing Certbot for SSL...${NC}"
sudo apt install certbot python3-certbot-nginx -y

echo ""
echo -e "${GREEN}✅ Setup Complete!${NC}"
echo "=============================="
echo ""
echo "Next steps:"
echo "1. Update /opt/hrms/application-prod.properties with your configuration"
echo "2. Upload your JAR file to /opt/hrms/"
echo "3. Upload your frontend build to /var/www/hrms/"
echo "4. Start the service: sudo systemctl start hrms"
echo ""
echo "Database credentials:"
echo "  - Database: payroll_hrms"
echo "  - Username: hrms_app"
echo "  - Password: (the one you entered)"
echo ""
echo "For SSL certificate (if you have a domain):"
echo "  sudo certbot --nginx -d your-domain.com"
echo ""
echo -e "${YELLOW}⚠️  Remember to save these credentials securely!${NC}"
