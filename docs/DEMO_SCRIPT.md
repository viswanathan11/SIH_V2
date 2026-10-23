# VidyaSetu — 7-Minute Live Demonstration Script

This document provides a precise, time-coded script for demonstrating **VidyaSetu** to AICTE leadership, jury members, and stakeholders.

---

## Pre-Demo Checklist (30 seconds before presentation)
1. **Reset Demo State:**
   ```bash
   # In terminal or browser
   curl -X POST http://localhost:8080/api/v1/demo/reset
   ```
2. **Open Two Browser Windows / Profiles:**
   - **Window 1 (Regulator / AICTE):** `http://localhost:3000` (or 5173 in dev)
   - **Window 2 (Institution & Public):** `http://localhost:3000` in Incognito

---

## Time-Coded Walkthrough

### 0:00 - 0:45: The Problem & Vision
- **Speaker:** "Today, AICTE collects aggregate student counts through yearly self-declarations. This creates huge blind spots: ghost enrolments, seat-cap breaches, and concurrent duplicate admissions across colleges that cannot be detected until it is too late. **VidyaSetu** solves this at the root by anchoring student records to the national **APAAR ID (Automated Permanent Academic Account Registry)**, creating a verifiable, real-time single source of truth with zero storage of raw Aadhaar numbers."

---

### 0:45 - 1:30: Institution Dashboard & Real-Time Capacity Tracking
1. In **Window 2**, go to `/login`.
2. Click the quick-fill chip: **Institution Admin (CIT - INST-TN-001)** (`admin.tn001@demo.vidyasetu.example` / `Demo@12345`).
3. View the **Institution Dashboard**:
   - Point out the **Intake Utilisation Meters**:
     - Approved regular intake: `120`
     - Current enrolled: `70 / 120` (58.3% filled).
     - Supernumerary quotas (TFW, OCI/FN) tracked separately from regular seats.

---

### 1:30 - 2:30: Frictionless Batch Upload & Real-Time Event Bus
1. Click **Data Ingestion** (`/inst/upload`).
2. Drag and drop `seed/demo_upload_clean.csv` (or click upload and select the file).
3. Observe:
   - File SHA-256 duplicate guard validates immediately.
   - Live Server-Sent Events (SSE) progress bar streams: **Staging -> Validation -> Ingestion**.
   - **50 rows processed: 50 Accepted, 0 Flagged, 0 Rejected**.
4. Return to **Institution Dashboard**:
   - The CSE meter updates instantly via SSE to **120 / 120 (100% capacity)**.
   - An instant toast notification announces **"Capacity Reached: BTECH-CSE has met 100% sanctioned intake"**.

---

### 2:30 - 3:45: AICTE National Oversight & Instant Breach Detection
1. In **Window 1**, login as **AICTE Super Admin** (`aicte.admin@demo.vidyasetu.example` / `Demo@12345`).
2. Show the **National Overview**:
   - Live Event Feed ticker on the right displaying uploads from institutions across India in real time.
3. Switch to **Window 2 (CIT)** and upload `seed/demo_upload_excess.csv` (15 additional admissions for CSE).
4. Watch **Window 1 (AICTE)** in real time:
   - An alert pops up instantly on the National Dashboard.
   - Under **Capacity Alerts**, CIT INST-TN-001 appears with **Intake Breach: 15 Excess Enrolments**.
5. Navigate to **Anomalies** (`/aicte/anomalies`):
   - Notice the AICTE Anomaly Engine (Seeded Isolation Forest + Deterministic Rule Rollup).
   - Click **"Explain"** on the excess enrolment anomaly to open the explainability drawer detailing the exact rule violations and risk score impact.

---

### 3:45 - 4:45: Dirty Data Ingestion, Worklist & CSV Error Export
1. Switch to **Window 2**, logout, and login as **Apex Institute (INST-RJ-001)** (`admin.rj001@demo.vidyasetu.example` / `Demo@12345`).
2. Go to **Data Ingestion** and upload `seed/demo_upload_dirty.csv` (60 synthetic rows).
3. Result:
   - **42 Accepted** (including 2 name mismatches flagged with warnings).
   - **3 Flagged (Critical)** (cross-institutional duplicate enrolments).
   - **15 Rejected** (invalid APAAR format, non-numeric, not in registry, missing OCI card).
4. Click **"Download Error Report (CSV)"**: Show how the institution gets row-by-row remediation hints.
5. Click **"Open Discrepancy Worklist"** (`/inst/worklist`):
   - Show inline correction workflow where the institution can edit and resolve individual errors without re-uploading the entire file.

---

### 4:45 - 5:30: Multi-Institution Duplicate Register & Immutable Audit Trail
1. In **Window 1 (AICTE Admin)**, go to **Issues & Duplicates** (`/aicte/issues`).
2. Click the **"Concurrent Active Enrolments"** tab:
   - Show students concurrently enrolled in two institutions (Scenario S3).
   - Highlight one student transferring from UP to Rajasthan; click **"Transfer"** to record an authorized migration.
3. Go to **Audit Log** (`/aicte/audit`):
   - Show the append-only SHA-256 cryptographic hash-chained audit log.
   - Click **"Verify Hash Chain"**:
     - System walks from genesis block to the tip in microseconds.
     - Green banner: **"Audit Hash Chain Intact ✔ — Zero Tampering Detected"**.

---

### 5:30 - 6:30: DPDP-Compliant Verification Portal & Tamper-Evident Certificates
1. In **Window 2**, go to the **Public / Employer Verify Portal** (`/verify`).
2. Fill in:
   - APAAR ID: `910000000001`
   - DOB: `2008-03-14`
   - Select Tier: **Tier L1 (Enrolment Status Only)**.
   - Click **"Verify Credentials"** -> Result displays immediately with zero student consent required.
3. Now change to **Tier L2 (Personal & Academic Credentials)** and click **Verify**:
   - System displays **"Student Consent Required under DPDP Act 2023"**.
4. Login as Student (`student.910000000001@demo.vidyasetu.example` or via student portal OTP):
   - Student sees the pending consent request from the employer.
   - Student clicks **"Approve Consent"**.
5. Employer clicks **"Re-check"** -> Tier L2 details unlock instantly!
6. Click **"Download Verifiable Certificate"**:
   - Open PDF: features high-resolution AICTE security border, official specimen watermark, student APAAR ID, Student ID, and high-density QR code.

---

### 6:30 - 7:00: M2M ERP Integration & Summary
1. Show terminal running `python scripts/hmac-client-example.py`:
   - Demonstrates institutional ERPs pushing student data via HMAC-SHA256 signed REST endpoints.
2. Conclusion:
   - Fulfills 100% of SIH problem statement requirements.
   - Production-ready, offline-first, compliant with DPDP Act 2023.
