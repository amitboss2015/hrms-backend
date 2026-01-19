#!/bin/bash
# ChandraHR Local Deployment Script
# This script builds and deploys the application using Docker Compose

set -e

echo "🚀 ChandraHR Local Deployment"
echo "=============================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(dirname "$SCRIPT_DIR")"
FRONTEND_DIR="${FRONTEND_DIR:-$BACKEND_DIR/../../../attendance-ui-auth}"

echo -e "${YELLOW}Backend directory:${NC} $BACKEND_DIR"
echo -e "${YELLOW}Frontend directory:${NC} $FRONTEND_DIR"

# Check if frontend directory exists
if [ ! -d "$FRONTEND_DIR" ]; then
    echo -e "${RED}Error: Frontend directory not found at $FRONTEND_DIR${NC}"
    echo "Please set FRONTEND_DIR environment variable to the correct path"
    exit 1
fi

# Step 1: Build Frontend
echo -e "\n${GREEN}📦 Step 1: Building Frontend...${NC}"
cd "$FRONTEND_DIR"
npm install
npm run build

# Step 2: Start Docker Compose
echo -e "\n${GREEN}🐳 Step 2: Starting Docker Compose...${NC}"
cd "$BACKEND_DIR"

# Set environment variable for frontend dist path
export FRONTEND_DIST_PATH="$FRONTEND_DIR/dist"

# Stop existing containers
docker-compose down 2>/dev/null || true

# Build and start
docker-compose up --build -d

# Wait for services to be ready
echo -e "\n${YELLOW}⏳ Waiting for services to start...${NC}"
sleep 10

# Check service status
echo -e "\n${GREEN}📊 Service Status:${NC}"
docker-compose ps

echo -e "\n${GREEN}✅ Deployment Complete!${NC}"
echo "=============================="
echo -e "Frontend: ${YELLOW}http://localhost${NC}"
echo -e "Backend API: ${YELLOW}http://localhost/api${NC}"
echo -e "Swagger UI: ${YELLOW}http://localhost/swagger${NC}"
echo ""
echo "View logs with: docker-compose logs -f"
