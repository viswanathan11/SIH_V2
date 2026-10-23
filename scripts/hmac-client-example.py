#!/usr/bin/env python3
"""
VidyaSetu Institution ERP Client Example
Demonstrates HMAC-SHA256 signed REST API integration for automated institutional student ingestion.
"""

import hashlib
import hmac
import json
import sys
import time
import urllib.request
import urllib.error

BASE_URL = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
KEY_ID = "demo-erp-tn001"
SECRET = "demo-secret-tn001"

print("==========================================================")
print(" VidyaSetu ERP Integration Client (HMAC-SHA256)")
print(f" Target: {BASE_URL}")
print(f" Key ID: {KEY_ID}")
print("==========================================================")

# Example: Enrol student via ERP webhook/API
endpoint = "/api/v1/integrations/students"
url = f"{BASE_URL}{endpoint}"
method = "POST"

payload = {
    "apaarId": "910000000075",
    "instituteStudentId": "CIT26CS0075",
    "fullName": "Karthik Raman",
    "dob": "2005-04-18",
    "gender": "M",
    "nationality": "IN",
    "residencyStatus": "RESIDENT_INDIAN",
    "admissionQuota": "STATE_CAP",
    "socialCategory": "GEN",
    "programCode": "BTECH-CSE",
    "entryType": "FIRST_YEAR",
    "admissionDate": "2026-08-20",
    "academicYear": "2026-27",
    "mobile": "9876540075",
    "email": "karthik.raman@cit.demo"
}

body_bytes = json.dumps(payload).encode("utf-8")
body_sha256 = hashlib.sha256(body_bytes).hexdigest()
timestamp = str(int(time.time()))

# Canonical string format: METHOD\nPATH\nTIMESTAMP\nSHA256(body)
canonical = f"{method}\n{endpoint}\n{timestamp}\n{body_sha256}"
signature = hmac.new(SECRET.encode("utf-8"), canonical.encode("utf-8"), hashlib.sha256).hexdigest()

print(f"Canonical string:\n---\n{canonical}\n---")
print(f"Generated Signature: {signature}")

req = urllib.request.Request(
    url,
    data=body_bytes,
    headers={
        "Content-Type": "application/json",
        "X-Key-Id": KEY_ID,
        "X-Timestamp": timestamp,
        "X-Signature": signature
    },
    method=method
)

try:
    with urllib.request.urlopen(req) as resp:
        res_body = resp.read().decode("utf-8")
        print("\nResponse Status:", resp.status)
        print("Response Body:\n", json.dumps(json.loads(res_body), indent=2) if res_body.startswith('{') else res_body)
        print("\n✔ ERP Ingestion call succeeded!")
except urllib.error.HTTPError as e:
    err_body = e.read().decode("utf-8")
    print("\nHTTP Error:", e.code, e.reason)
    print("Error Details:", err_body)
    sys.exit(1)
except Exception as ex:
    print("\nConnection error:", ex)
    print("Ensure backend is running at", BASE_URL)
    sys.exit(1)
