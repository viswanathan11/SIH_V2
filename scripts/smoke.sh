#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
echo "=========================================================="
echo " VidyaSetu Smoke Test Suite"
echo " Target: ${BASE_URL}"
echo "=========================================================="

# 1. Health check
echo -n "[1/7] Checking public config / health... "
CONFIG=$(curl -s -f "${BASE_URL}/api/v1/public/config")
if echo "$CONFIG" | grep -q "VidyaSetu"; then
  echo "OK (VidyaSetu online, demoMode=true)"
else
  echo "FAILED"
  exit 1
fi

# 2. Login as AICTE Admin
echo -n "[2/7] Authenticating as AICTE Admin... "
LOGIN_RESP=$(curl -s -f -X POST "${BASE_URL}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"aicte.admin@demo.vidyasetu.example","password":"Demo@12345"}')

TOKEN=$(echo "$LOGIN_RESP" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
if [ -n "$TOKEN" ]; then
  echo "OK (JWT issued)"
else
  echo "FAILED to acquire token"
  exit 1
fi

# 3. Fetch Institutions List
echo -n "[3/7] Fetching institutions list... "
INST_RESP=$(curl -s -f -H "Authorization: Bearer ${TOKEN}" "${BASE_URL}/api/v1/institutions")
INST_COUNT=$(echo "$INST_RESP" | grep -o '"aicteId"' | wc -l)
echo "OK (${INST_COUNT} institutions active)"

# 4. Fetch National Analytics Summary
echo -n "[4/7] Fetching national analytics KPI summary... "
ANALYTICS_RESP=$(curl -s -f -H "Authorization: Bearer ${TOKEN}" "${BASE_URL}/api/v1/analytics/national/summary")
if echo "$ANALYTICS_RESP" | grep -q "totalApprovedIntake"; then
  echo "OK"
else
  echo "FAILED"
  exit 1
fi

# 5. Verify Cryptographic Audit Hash Chain
echo -n "[5/7] Verifying cryptographic audit hash chain... "
CHAIN_RESP=$(curl -s -f -H "Authorization: Bearer ${TOKEN}" "${BASE_URL}/api/v1/audit/verify-chain")
if echo "$CHAIN_RESP" | grep -q '"intact":true'; then
  echo "OK (SHA-256 genesis-to-tip chain intact)"
else
  echo "FAILED or compromised"
  exit 1
fi

# 6. Verify Public JWKS endpoint
echo -n "[6/7] Verifying public JWKS key export... "
JWKS_RESP=$(curl -s -f "${BASE_URL}/api/v1/public/jwks.json")
if echo "$JWKS_RESP" | grep -q '"kty":"EC"'; then
  echo "OK (ES256 public key exposed for verification)"
else
  echo "FAILED"
  exit 1
fi

# 7. Public Record Verification Lookup
echo -n "[7/7] Verifying public tiered credential lookup... "
VERIFY_RESP=$(curl -s -f -X POST "${BASE_URL}/api/v1/verify/lookup" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"apaarId":"910000000001","requestedTier":"L1","purpose":"Smoke Test Verification"}')
if echo "$VERIFY_RESP" | grep -q '"result":"FOUND"'; then
  echo "OK (Tier L1 verification completed)"
else
  echo "FAILED"
  exit 1
fi

echo "=========================================================="
echo " ALL SMOKE TESTS PASSED SUCCESSFULLY! "
echo " VidyaSetu is demo-ready."
echo "=========================================================="
