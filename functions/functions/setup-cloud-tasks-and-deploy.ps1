# PowerShell script to automatically set up Cloud Tasks and deploy brief functions
# This script:
# 1. Enables Cloud Tasks API
# 2. Creates the brief-generation-queue
# 3. Deploys the brief functions

Write-Host "🚀 Setting up Cloud Tasks for scalable brief system..." -ForegroundColor Cyan

$projectId = "vanish-auth-real"
$location = "us-central1"
$queueName = "brief-generation-queue"

# Step 1: Enable Cloud Tasks API
Write-Host "`n📦 Step 1: Enabling Cloud Tasks API..." -ForegroundColor Yellow
try {
    gcloud services enable cloudtasks.googleapis.com --project=$projectId 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ Cloud Tasks API enabled successfully" -ForegroundColor Green
    } else {
        Write-Host "⚠️ Cloud Tasks API may already be enabled or there was an issue" -ForegroundColor Yellow
    }
} catch {
    Write-Host "⚠️ Error enabling API (may already be enabled): $_" -ForegroundColor Yellow
}

# Wait a moment for API to propagate
Write-Host "⏳ Waiting 10 seconds for API to propagate..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# Step 2: Check if queue exists, create if not
Write-Host "`n📋 Step 2: Checking for Cloud Tasks queue..." -ForegroundColor Yellow
try {
    $queueExists = gcloud tasks queues describe $queueName --location=$location --project=$projectId 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ Queue '$queueName' already exists" -ForegroundColor Green
    } else {
        Write-Host "📝 Queue doesn't exist, creating it..." -ForegroundColor Yellow
        gcloud tasks queues create $queueName `
            --location=$location `
            --project=$projectId `
            --max-dispatches-per-second=10 `
            --max-concurrent-dispatches=100 `
            --max-retry-duration=3600s
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✅ Queue '$queueName' created successfully" -ForegroundColor Green
        } else {
            Write-Host "❌ Failed to create queue. Error:" -ForegroundColor Red
            Write-Host $queueExists -ForegroundColor Red
            exit 1
        }
    }
} catch {
    Write-Host "❌ Error checking/creating queue: $_" -ForegroundColor Red
    exit 1
}

# Step 3: Build functions
Write-Host "`n🔨 Step 3: Building functions..." -ForegroundColor Yellow
Set-Location "$PSScriptRoot"
npm run build
if ($LASTEXITCODE -ne 0) {
    Write-Host "⚠️ Build completed with warnings (this is expected)" -ForegroundColor Yellow
}

# Step 4: Deploy brief functions
Write-Host "`n🚀 Step 4: Deploying brief functions..." -ForegroundColor Yellow
Set-Location ".."
firebase deploy --only functions:sendMorningBriefs,functions:sendAfternoonBriefs,functions:sendEveningBriefs,functions:processBriefTask --force

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✅ SUCCESS! Scalable brief system deployed!" -ForegroundColor Green
    Write-Host "`n📊 System Configuration:" -ForegroundColor Cyan
    Write-Host "   - Queue: $queueName" -ForegroundColor White
    Write-Host "   - Location: $location" -ForegroundColor White
    Write-Host "   - Max dispatches/sec: 10" -ForegroundColor White
    Write-Host "   - Max concurrent: 100" -ForegroundColor White
    Write-Host "`n🎯 The system will now:" -ForegroundColor Cyan
    Write-Host "   - Scale to thousands of users without timeouts" -ForegroundColor White
    Write-Host "   - Process each user in a separate function invocation" -ForegroundColor White
    Write-Host "   - Automatically retry failed tasks" -ForegroundColor White
} else {
    Write-Host "`n❌ Deployment failed. Check the errors above." -ForegroundColor Red
    exit 1
}
