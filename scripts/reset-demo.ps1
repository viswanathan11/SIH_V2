param (
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " VidyaSetu Demo Database Reset (PowerShell)" -ForegroundColor Cyan
Write-Host " Target: $BaseUrl" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

Write-Host -NoNewline "Calling POST $BaseUrl/api/v1/demo/reset ... "
$resp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/demo/reset" -Method Post
Write-Host "SUCCESS!" -ForegroundColor Green
Write-Host ($resp | ConvertTo-Json -Depth 3)
Write-Host ""
Write-Host "Demo state restored in under 20 seconds. Scenarios S1-S10 re-seeded." -ForegroundColor Green
