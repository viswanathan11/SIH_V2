#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
python3 "$(dirname "$0")/hmac-client-example.py" "$BASE_URL"
