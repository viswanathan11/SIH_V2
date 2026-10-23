#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
echo "=========================================================="
echo " VidyaSetu Demo Database Reset"
echo " Target: ${BASE_URL}"
echo "=========================================================="

echo -n "Calling POST /api/v1/demo/reset ... "
RESP=$(curl -s -f -X POST "${BASE_URL}/api/v1/demo/reset")
echo "SUCCESS!"
echo "$RESP"
echo ""
echo "Demo state restored in under 20 seconds. Scenarios S1-S10 re-seeded."
