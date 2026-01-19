# 🚀 ChandraHR - AWS Free Tier Deployment Quick Start

## Files Created for Deployment

```
hrms-backend/
├── docs/
│   └── AWS_DEPLOYMENT.md          # Detailed step-by-step guide
├── scripts/
│   ├── setup-ec2.sh               # Initial EC2 server setup
│   ├── deploy-aws.sh              # Deploy from local to EC2
│   └── deploy-local.sh            # Local Docker deployment
├── nginx/
│   └── nginx.conf                 # Nginx configuration
├── Dockerfile                     # Docker build for backend
├── docker-compose.yml             # Full stack Docker setup
├── src/main/resources/
│   └── application-prod.properties # Production config template
└── DEPLOY_QUICK_START.md          # This file
```

---

## Option 1: Quick Manual Deployment (Recommended for First Time)

### Step 1: Launch EC2 Instance
1. Go to AWS Console → EC2 → Launch Instance
2. Choose: **Ubuntu 22.04 LTS**, **t2.micro** (Free Tier)
3. Security Group: Allow SSH (22), HTTP (80), HTTPS (443)
4. Create or select a key pair
5. Launch and note the Public IP
6. Allocate an **Elastic IP** and associate it (keeps IP static)

### Step 2: Connect & Setup Server
```bash
# Connect to EC2
ssh -i your-key.pem ubuntu@YOUR_EC2_IP

# Run setup script (copy from scripts/setup-ec2.sh)
# Or manually install: Java 17, MySQL 8, Nginx
```

### Step 3: Build & Upload
On your local machine:
```bash
# Build backend
cd "hrms-backend 2"
./mvnw clean package -DskipTests

# Build frontend  
cd ../attendance-ui-auth
npm install && npm run build

# Upload to EC2
scp -i your-key.pem target/hrms-backend-0.0.1-SNAPSHOT.jar ubuntu@YOUR_EC2_IP:/opt/hrms/
scp -i your-key.pem -r dist/* ubuntu@YOUR_EC2_IP:/var/www/hrms/
```

### Step 4: Configure & Start
On EC2:
```bash
# Create production config
sudo nano /opt/hrms/application-prod.properties
# (copy template and update values)

# Start service
sudo systemctl start hrms
sudo systemctl enable hrms
```

### Step 5: Access Your App
- **Frontend**: `http://YOUR_EC2_IP/`
- **API**: `http://YOUR_EC2_IP/api/`
- **Swagger**: `http://YOUR_EC2_IP/swagger`

---

## Option 2: Automated Deployment Script

After initial setup, use the deploy script for updates:

```bash
# Edit scripts/deploy-aws.sh with your EC2 details
EC2_HOST="YOUR_EC2_IP"
SSH_KEY="~/.ssh/your-key.pem"

# Run deployment
./scripts/deploy-aws.sh
```

---

## Option 3: Docker Compose (Local Testing)

```bash
# Set frontend path
export FRONTEND_DIST_PATH="../attendance-ui-auth/dist"

# Build frontend first
cd ../attendance-ui-auth && npm run build && cd -

# Start all services
docker-compose up --build -d

# Access at http://localhost
```

---

## 💰 AWS Free Tier Costs

| Service | Free Tier | Your Usage |
|---------|-----------|------------|
| EC2 t2.micro | 750 hrs/month | ✅ Covered |
| EBS Storage | 30 GB | ✅ 20 GB used |
| Elastic IP | Free when attached | ✅ |
| Data Transfer | 100 GB/month | ✅ Minimal |

**After 12 months**: ~$10-15/month for t2.micro

---

## 🔐 Security Checklist

- [ ] Change default passwords in `application-prod.properties`
- [ ] Use a strong JWT secret (64+ characters)
- [ ] Enable UFW firewall on EC2
- [ ] Set up SSL with Certbot (if you have a domain)
- [ ] Regular database backups

---

## 📞 Useful Commands

```bash
# View logs
sudo journalctl -u hrms -f

# Restart service
sudo systemctl restart hrms

# Check status
sudo systemctl status hrms

# Database backup
mysqldump -u hrms_app -p payroll_hrms > backup_$(date +%Y%m%d).sql

# SSL Certificate (if you have domain)
sudo certbot --nginx -d your-domain.com
```

---

## Need Help?

See `docs/AWS_DEPLOYMENT.md` for the complete detailed guide with troubleshooting tips.
