# ==============================================================================
# VidyaSetu (विद्यासेतु) - One-Click Project Launcher
# ==============================================================================

$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "          VidyaSetu (विद्यासेतु) - AICTE Student Platform         " -ForegroundColor Yellow
Write-Host "                Starting Local Full-Stack Services               " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""

$RootPath = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $RootPath) { $RootPath = Get-Location }

# Function to test TCP port
function Test-PortOpen([int]$port) {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $asyncResult = $client.BeginConnect("127.0.0.1", $port, $null, $null)
        $wait = $asyncResult.AsyncWaitHandle.WaitOne(800, $false)
        if ($wait) {
            $client.EndConnect($asyncResult)
            $client.Close()
            return $true
        }
        $client.Close()
        return $false
    } catch {
        return $false
    }
}

# 1. Start Backend if not already running
$BackendRunning = Test-PortOpen 8080
if ($BackendRunning) {
    Write-Host "[1/3] Backend is already running on http://localhost:8080" -ForegroundColor Green
} else {
    Write-Host "[1/3] Starting Spring Boot Backend on http://localhost:8080 ..." -ForegroundColor Cyan
    $JarPath = Join-Path $RootPath "backend\target\vidyasetu-backend-1.0.0.jar"
    if (Test-Path $JarPath) {
        Start-Process java -ArgumentList "-jar", "`"$JarPath`"", "--spring.profiles.active=local-embedded" -WorkingDirectory (Join-Path $RootPath "backend") -WindowStyle Hidden
    } else {
        Start-Process mvn -ArgumentList "spring-boot:run", "-Dspring-boot.run.profiles=local-embedded" -WorkingDirectory (Join-Path $RootPath "backend")
    }

    Write-Host "      Waiting for database initialization and seeding" -NoNewline
    $MaxWait = 35
    $Count = 0
    while ($Count -lt $MaxWait) {
        Start-Sleep -Seconds 1
        $Count++
        Write-Host -NoNewline "."
        try {
            $resp = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/public/config" -Method Get -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($resp.appName -match "VidyaSetu") {
                break
            }
        } catch {}
    }
    Write-Host " Done!" -ForegroundColor Green
}

# 2. Start Frontend if not already running
$FrontendRunning = Test-PortOpen 5173
if ($FrontendRunning) {
    Write-Host "[2/3] Frontend is already running on http://localhost:5173" -ForegroundColor Green
} else {
    Write-Host "[2/3] Starting React Vite Frontend on http://localhost:5173 ..." -ForegroundColor Cyan
    Start-Process npm -ArgumentList "run", "dev" -WorkingDirectory (Join-Path $RootPath "frontend") -WindowStyle Hidden
    Start-Sleep -Seconds 3
}

# 3. Open Browser
Write-Host "[3/3] Opening web browser at http://localhost:5173 ..." -ForegroundColor Green
Start-Process "http://localhost:5173"

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "                   VIDYASETU IS READY & ACTIVE!                  " -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  Web Portal:    http://localhost:5173" -ForegroundColor White
Write-Host "  API Base:      http://localhost:8080/api/v1" -ForegroundColor White
Write-Host "  Swagger UI:    http://localhost:8080/swagger-ui.html" -ForegroundColor White
Write-Host ""
Write-Host "  Demo Login Accounts (Password for all accounts: Demo@12345):" -ForegroundColor Yellow
Write-Host "    - AICTE Super Admin: aicte.admin@demo.vidyasetu.example" -ForegroundColor Gray
Write-Host "    - Inst. Admin (CIT): admin.tn001@demo.vidyasetu.example" -ForegroundColor Gray
Write-Host "    - Student Portal:    student.910000000001@demo.vidyasetu.example" -ForegroundColor Gray
Write-Host "    - Public Verifier:   http://localhost:5173/verify (No login)" -ForegroundColor Gray
Write-Host ""
Write-Host "  Commands:" -ForegroundColor Yellow
Write-Host "    Run Smoke Tests: powershell -ExecutionPolicy Bypass -File .\scripts\smoke.ps1" -ForegroundColor Gray
Write-Host "    Reset Demo Data: powershell -ExecutionPolicy Bypass -File .\scripts\reset-demo.ps1" -ForegroundColor Gray
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""
