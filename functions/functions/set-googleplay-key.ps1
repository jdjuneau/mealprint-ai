# PowerShell script to set Google Play service account key
# Usage: .\set-googleplay-key.ps1 "C:\path\to\service-account-key.json"

param(
    [Parameter(Mandatory=$true)]
    [string]$KeyFilePath
)

Write-Host "📋 Setting Google Play service account key..." -ForegroundColor Cyan

if (-not (Test-Path $KeyFilePath)) {
    Write-Host "❌ Error: File not found: $KeyFilePath" -ForegroundColor Red
    exit 1
}

# Read JSON file and escape it properly
$jsonContent = Get-Content $KeyFilePath -Raw
$jsonEscaped = $jsonContent -replace '"', '\"'

# Set Firebase config
Write-Host "Setting Firebase config..." -ForegroundColor Yellow
firebase functions:config:set "googleplay.service_account_key=$jsonContent"

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Service account key configured successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next step: Deploy Google Play functions" -ForegroundColor Cyan
} else {
    Write-Host "❌ Failed to set config" -ForegroundColor Red
    exit 1
}
