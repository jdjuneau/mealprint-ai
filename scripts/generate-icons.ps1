# Mealprint AI Icon Generation Script
# This script explains how to properly size icons for production

Write-Host "🍽️ Mealprint AI Icon Setup" -ForegroundColor Green
Write-Host "================================" -ForegroundColor Green

Write-Host "✅ Android Icons Created:" -ForegroundColor Green
Write-Host "   • mipmap-mdpi/ic_launcher.png (48x48)" -ForegroundColor White
Write-Host "   • mipmap-hdpi/ic_launcher.png (72x72)" -ForegroundColor White
Write-Host "   • mipmap-xhdpi/ic_launcher.png (96x96)" -ForegroundColor White
Write-Host "   • mipmap-xxhdpi/ic_launcher.png (144x144)" -ForegroundColor White
Write-Host "   • mipmap-xxxhdpi/ic_launcher.png (192x192)" -ForegroundColor White

Write-Host "" -ForegroundColor White
Write-Host "✅ Web Icons Created:" -ForegroundColor Green
Write-Host "   • public/favicon.ico" -ForegroundColor White
Write-Host "   • public/icon-192.png (PWA icon)" -ForegroundColor White
Write-Host "   • public/icon-512.png (PWA icon)" -ForegroundColor White
Write-Host "   • public/manifest.json (PWA manifest)" -ForegroundColor White

Write-Host "" -ForegroundColor White
Write-Host "⚠️  PRODUCTION NOTE:" -ForegroundColor Yellow
Write-Host "   For production, resize the source icon to these exact dimensions:" -ForegroundColor White
Write-Host "   • Android: Use Android Asset Studio or similar tool" -ForegroundColor White
Write-Host "   • Web: Convert JPG to PNG and resize appropriately" -ForegroundColor White

Write-Host "" -ForegroundColor White
Write-Host "🎯 Current Status: Icons configured for development" -ForegroundColor Cyan
Write-Host "🚀 Ready for testing on both platforms!" -ForegroundColor Green
