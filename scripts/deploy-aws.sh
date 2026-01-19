#!/bin/bash
# ChandraHR AWS EC2 Deployment Script
# Run this script from your local machine to deploy to EC2

set -e

# Configuration - UPDATE THESE VALUES
EC2_USER="ubuntu"
EC2_HOST="YOUR_EC2_IP_OR_DOMAIN"
SSH_KEY="~/.ssh/your-key.pem"
REMOTE_APP_DIR="/opt/hrms"
REMOTE_WEB_DIR="/var/www/hrms"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Get directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(dirname "$SCRIPT_DIR")"
FRONTEND_DIR="${FRONTEND_DIR:-$BACKEND_DIR/../../../attendance-ui-auth}"

echo "🚀 ChandraHR AWS Deployment"
echo "==========================="
echo -e "${YELLOW}Target:${NC} $EC2_USER@$EC2_HOST"
echo ""

# Validate inputs
if [ "$EC2_HOST" == "YOUR_EC2_IP_OR_DOMAIN" ]; then
    echo -e "${RED}Error: Please update EC2_HOST in this script${NC}"
    exit 1
fi

if [ ! -f "$SSH_KEY" ]; then
    echo -e "${RED}Error: SSH key not found at $SSH_KEY${NC}"
    exit 1
fi

# Step 1: Build Backend
echo -e "\n${GREEN}📦 Step 1: Building Backend JAR...${NC}"
cd "$BACKEND_DIR"
./mvnw clean package -DskipTests
JAR_FILE="$BACKEND_DIR/target/hrms-backend-0.0.1-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}Error: JAR file not found${NC}"
    exit 1
fi

# Step 2: Build Frontend
echo -e "\n${GREEN}📦 Step 2: Building Frontend...${NC}"
cd "$FRONTEND_DIR"
npm install
npm run build

# Step 3: Upload files to EC2
echo -e "\n${GREEN}📤 Step 3: Uploading to EC2...${NC}"

# Upload JAR
echo "Uploading backend JAR..."
scp -i "$SSH_KEY" "$JAR_FILE" "$EC2_USER@$EC2_HOST:/tmp/"

# Upload frontend (as tar to preserve structure)
echo "Uploading frontend..."
cd "$FRONTEND_DIR"
tar -czf /tmp/frontend.tar.gz -C dist .
scp -i "$SSH_KEY" /tmp/frontend.tar.gz "$EC2_USER@$EC2_HOST:/tmp/"

# Upload production config
echo "Uploading configuration..."
scp -i "$SSH_KEY" "$BACKEND_DIR/src/main/resources/application-prod.properties" "$EC2_USER@$EC2_HOST:/tmp/"

# Step 4: Deploy on EC2
echo -e "\n${GREEN}🔧 Step 4: Deploying on EC2...${NC}"
ssh -i "$SSH_KEY" "$EC2_USER@$EC2_HOST" << 'REMOTE_SCRIPT'
set -e

echo "Stopping backend service..."
sudo systemctl stop hrms 2>/dev/null || true

echo "Backing up current JAR..."
if [ -f /opt/hrms/hrms-backend-0.0.1-SNAPSHOT.jar ]; then
    sudo mv /opt/hrms/hrms-backend-0.0.1-SNAPSHOT.jar /opt/hrms/hrms-backend-backup.jar
fi

echo "Installing new JAR..."
sudo mv /tmp/hrms-backend-0.0.1-SNAPSHOT.jar /opt/hrms/
sudo chown ubuntu:ubuntu /opt/hrms/hrms-backend-0.0.1-SNAPSHOT.jar

echo "Updating configuration..."
if [ ! -f /opt/hrms/application-prod.properties ]; then
    sudo mv /tmp/application-prod.properties /opt/hrms/
    sudo chown ubuntu:ubuntu /opt/hrms/application-prod.properties
    echo "⚠️  Please update /opt/hrms/application-prod.properties with your production values!"
fi

echo "Deploying frontend..."
sudo rm -rf /var/www/hrms/*
sudo tar -xzf /tmp/frontend.tar.gz -C /var/www/hrms/
sudo chown -R www-data:www-data /var/www/hrms

echo "Starting backend service..."
sudo systemctl start hrms

echo "Cleaning up..."
rm -f /tmp/frontend.tar.gz /tmp/application-prod.properties

echo "Checking service status..."
sudo systemctl status hrms --no-pager
REMOTE_SCRIPT

echo -e "\n${GREEN}✅ Deployment Complete!${NC}"
echo "==========================="
echo -e "Your app is now live at: ${YELLOW}http://$EC2_HOST${NC}"
echo ""
echo "Useful commands on EC2:"
echo "  - View logs: sudo journalctl -u hrms -f"
echo "  - Restart: sudo systemctl restart hrms"
echo "  - Status: sudo systemctl status hrms"

# Cleanup local temp files
rm -f /tmp/frontend.tar.gz
