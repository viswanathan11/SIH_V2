# ==============================================================================
# VidyaSetu - Stop All Services
# ==============================================================================

Write-Host "Stopping VidyaSetu services..." -ForegroundColor Yellow

# Stop java backend process
Get-Process -Name "java" -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match "vidyasetu" -or $_.Path -match "java" } | Stop-Process -Force -ErrorAction SilentlyContinue

# Stop node frontend process
Get-Process -Name "node" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

Write-Host "All VidyaSetu services stopped successfully." -ForegroundColor Green
