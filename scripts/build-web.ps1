# Mealprint AI Web App Build Script

Write-Host "🍽️ Building Mealprint AI Web App..." -ForegroundColor Green

# Navigate to web app directory
Set-Location "$PSScriptRoot\..\web\web"

# Install dependencies
Write-Host "📦 Installing dependencies..." -ForegroundColor Yellow
npm install

# Build the app
Write-Host "🔨 Building production app..." -ForegroundColor Yellow
npm run build

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Web app built successfully!" -ForegroundColor Green
    Write-Host "🚀 Ready for deployment to Firebase Hosting" -ForegroundColor Cyan
} else {
    Write-Host "❌ Build failed!" -ForegroundColor Red
    exit 1
}
