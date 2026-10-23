param (
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " VidyaSetu Smoke Test Suite (PowerShell)" -ForegroundColor Cyan
Write-Host " Target: $BaseUrl" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Health check
Write-Host -NoNewline "[1/7] Checking public config / health... "
$config = Invoke-RestMethod -Uri "$BaseUrl/api/v1/public/config" -Method Get
if ($config.appName -match "VidyaSetu") {
    Write-Host "OK (VidyaSetu online, demoMode=$($config.demoMode))" -ForegroundColor Green
} else {
    Write-Host "FAILED" -ForegroundColor Red
    exit 1
}

# 2. Login as AICTE Admin
Write-Host -NoNewline "[2/7] Authenticating as AICTE Admin... "
$loginBody = @{
    email = "aicte.admin@demo.vidyasetu.example"
    password = "Demo@12345"
} | ConvertTo-Json

$loginResp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$token = $loginResp.accessToken
if ($token) {
    Write-Host "OK (JWT issued)" -ForegroundColor Green
} else {
    Write-Host "FAILED to acquire token" -ForegroundColor Red
    exit 1
}

$headers = @{
    Authorization = "Bearer $token"
}

# 3. Fetch Institutions List
Write-Host -NoNewline "[3/7] Fetching institutions list... "
$instList = Invoke-RestMethod -Uri "$BaseUrl/api/v1/institutions" -Method Get -Headers $headers
Write-Host "OK ($($instList.Count) institutions active)" -ForegroundColor Green

# 4. Fetch National Analytics Summary
Write-Host -NoNewline "[4/7] Fetching national analytics KPI summary... "
$analytics = Invoke-RestMethod -Uri "$BaseUrl/api/v1/analytics/national/summary" -Method Get -Headers $headers
if ($analytics.totalApprovedIntake -gt 0) {
    Write-Host "OK (Intake: $($analytics.totalApprovedIntake), Enrolled: $($analytics.totalEnrolled))" -ForegroundColor Green
} else {
    Write-Host "FAILED" -ForegroundColor Red
    exit 1
}

# 5. Verify Cryptographic Audit Hash Chain
Write-Host -NoNewline "[5/7] Verifying cryptographic audit hash chain... "
$chain = Invoke-RestMethod -Uri "$BaseUrl/api/v1/audit/verify-chain" -Method Get -Headers $headers
if ($chain.intact -eq $true) {
    Write-Host "OK (SHA-256 genesis-to-tip chain intact, checked $($chain.checked) entries)" -ForegroundColor Green
} else {
    Write-Host "FAILED" -ForegroundColor Red
    exit 1
}

# 6. Verify Public JWKS endpoint
Write-Host -NoNewline "[6/7] Verifying public JWKS key export... "
$jwks = Invoke-RestMethod -Uri "$BaseUrl/api/v1/public/jwks.json" -Method Get
if ($jwks.keys.Count -gt 0) {
    Write-Host "OK (ES256 public key exposed for offline verification)" -ForegroundColor Green
} else {
    Write-Host "FAILED" -ForegroundColor Red
    exit 1
}

# 7. Public Record Verification Lookup
Write-Host -NoNewline "[7/7] Verifying public tiered credential lookup... "
$verifyBody = @{
    apaarId = "910000000001"
    requestedTier = "L1"
    purpose = "Smoke Test Verification"
} | ConvertTo-Json

$verify = Invoke-RestMethod -Uri "$BaseUrl/api/v1/verify/lookup" -Method Post -Body $verifyBody -Headers $headers -ContentType "application/json"
if ($verify.result -eq "FOUND") {
    Write-Host "OK (Tier L1 verification completed for $($verify.data.apaarMasked))" -ForegroundColor Green
} else {
    Write-Host "FAILED" -ForegroundColor Red
    exit 1
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " ALL SMOKE TESTS PASSED SUCCESSFULLY! " -ForegroundColor Green
Write-Host " VidyaSetu is demo-ready." -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
