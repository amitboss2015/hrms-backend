# 🚀 CI/CD Setup Guide - ChandraHR

## Overview

This project uses **GitHub Actions** for automated CI/CD:
- **CI**: Build & Test on every Pull Request
- **CD**: Auto-deploy to AWS EC2 when merged to `master`

---

## 📋 Branch Strategy

```
feature/xyz (developer work)
    ↓ PR + approval
develop (integration)
    ↓ PR + approval
master (production) → AUTO-DEPLOY 🚀
```

---

## 🔧 GitHub Secrets Setup

### Step 1: Navigate to Repository Settings

1. Go to your GitHub repository
2. Click **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**

### Step 2: Add Required Secrets

Add these secrets to **BOTH** repositories (hrms-backend & hrms-frontend):

| Secret Name | Value | Description |
|-------------|-------|-------------|
| `EC2_HOST` | `52.66.34.89` | EC2 Public IP |
| `EC2_USER` | `ubuntu` | SSH Username |
| `EC2_SSH_KEY` | (see below) | Private SSH Key |

### Step 3: Add SSH Key Secret

1. Open your PEM file: `/Users/amit.kumar/Downloads/chandrahr-key.pem`
2. Copy the ENTIRE content (including BEGIN and END lines)
3. Paste as the value for `EC2_SSH_KEY`

Example format:
```
-----BEGIN RSA PRIVATE KEY-----
MIIEowIBAAKCAQEA...
...
-----END RSA PRIVATE KEY-----
```

---

## 🔒 Branch Protection Setup

### Protect `master` Branch

1. Go to **Settings** → **Branches** → **Add rule**
2. Branch name pattern: `master`
3. Enable:
   - ✅ Require a pull request before merging
   - ✅ Require approvals (1)
   - ✅ Dismiss stale pull request approvals
   - ✅ Require status checks to pass before merging
     - Select: `Build & Test Backend` (or Frontend)
   - ✅ Require branches to be up to date before merging

### Protect `develop` Branch (Optional)

Same settings, but fewer restrictions:
- ✅ Require a pull request before merging
- ✅ Require status checks to pass

---

## 👥 Adding Team Members

1. Go to **Settings** → **Collaborators**
2. Click **Add people**
3. Enter GitHub username
4. Select role:
   - **Write**: Can push branches, create PRs
   - **Read**: View only

---

## 🔄 Workflow for Developers

### 1. Create Feature Branch
```bash
git checkout develop
git pull origin develop
git checkout -b feature/my-feature
```

### 2. Make Changes & Push
```bash
git add .
git commit -m "Add my feature"
git push origin feature/my-feature
```

### 3. Create Pull Request
- Go to GitHub
- Click "Compare & pull request"
- Select: `feature/my-feature` → `develop`
- Fill PR template
- Request review from @amitboss2015

### 4. Wait for Approval
- CI will run automatically
- Amit will review and approve
- Merge when approved

---

## 🚀 Workflow for Admin (Amit)

### Review & Merge to Develop
1. Review PR
2. Check CI status (must pass)
3. Approve and Merge

### Deploy to Production
1. Create PR: `develop` → `master`
2. Review changes
3. Merge to master
4. **🚀 Auto-deploy triggered!**

### Monitor Deployment
1. Go to **Actions** tab
2. Watch "🚀 Deploy to Production" workflow
3. Check for green checkmark ✅

---

## 🔍 Troubleshooting

### Deployment Failed?

1. Check Actions log for error
2. SSH manually to verify:
   ```bash
   ssh -i chandrahr-key.pem ubuntu@52.66.34.89
   sudo systemctl status hrms
   sudo journalctl -u hrms -n 50
   ```

### Rollback
```bash
ssh -i chandrahr-key.pem ubuntu@52.66.34.89
sudo systemctl stop hrms
sudo mv /opt/hrms/hrms-backend-backup.jar /opt/hrms/hrms-backend-0.0.1-SNAPSHOT.jar
sudo systemctl start hrms
```

---

## 📊 Workflow Summary

| Event | Trigger | Actions |
|-------|---------|---------|
| PR to develop | Developer creates PR | Build & Test |
| Merge to develop | Admin approves | Nothing |
| PR to master | Admin creates PR | Build & Test |
| Merge to master | Admin approves | **🚀 Auto-deploy** |

---

## 🎉 You're All Set!

Once configured:
1. Developers push to feature branches
2. Create PRs to develop
3. You review and merge
4. When ready, merge develop → master
5. Sit back and watch auto-deploy! 🚀
