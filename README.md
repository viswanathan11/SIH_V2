# VidyaSetu (विद्यासेतु)

> **AICTE Student Data Management & Verification System Anchored to APAAR ID**  
> *Developed for the Smart India Hackathon (SIH) — Smart Education & Governance Track*

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React 18](https://img.shields.io/badge/React-18-blue.svg)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-blue.svg)](https://www.typescriptlang.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 📌 Problem Overview
Technical institutions across India admit millions of students every academic cycle. Current regulatory oversight relies on retrospective annual self-declarations, leading to:
- **Ghost enrolments** and institutions quietly breaching sanctioned seat intakes.
- **Concurrent duplicate enrolments** across different universities in separate states.
- Lack of instant, tamper-proof credential verification for employers and regulatory bodies.

**VidyaSetu** solves this by establishing a real-time, privacy-preserving single source of truth for technical education enrolments, anchored securely to the student's **Automated Permanent Academic Account Registry (APAAR ID)** with **zero storage of raw Aadhaar numbers**.

---

## 🚀 Key Innovations & Features
1. **Intelligent Anomaly Detection:** In-memory, zero-dependency seeded **Isolation Forest** coupled with deterministic rule detectors to uncover off-hour admissions, batch clustering, and seat-cap breaches.
2. **Cryptographic Verifiable Credentials:** Verifiable Student Records signed via **ES256 (RFC 7515 Compact JWS)** with public **JWKS** export for offline, global verification.
3. **Tamper-Evident Audit Ledger:** Cryptographic **SHA-256 hash-chained append-only audit trail** enforced at the database trigger layer (`verify-chain` tamper detector).
4. **DPDP Act 2023 Compliance:** Tiered selective disclosure (**PUBLIC**, **L1**, **L2**, **L3**) gated by asynchronous student digital consent.
5. **Real-Time Monitoring:** Low-latency **Server-Sent Events (SSE)** broadcasting live ingestion metrics, capacity notifications, and national risk alerts.
6. **M2M ERP Integration:** REST API with **HMAC-SHA256 request signatures** and configurable webhooks with retries.

---

## ⚡ Quick Start

### Option A: Complete Docker Compose (Production Stack)
```bash
# Clone and launch all services (PostgreSQL 16, Backend, Frontend)
docker compose up -d --build

# Open frontend in browser:
http://localhost:3000
```

### Option B: Local Development (Embedded Zero-Config Mode)
*Requires zero external databases or Docker daemons. Runs entirely self-contained.*

#### 1. Start Backend:
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local-embedded
# Backend boots at http://localhost:8080 with in-memory PostgreSQL and seeds 12 institutions
```

#### 2. Start Frontend:
```bash
cd frontend
npm install
npm run dev
# Frontend boots at http://localhost:5173
```

---

## 🔑 Pre-Seeded Demo Credentials

All accounts use the universal demo password: **`Demo@12345`**

| Role | Email / Identifier | Purpose / View |
|---|---|---|
| **AICTE Super Admin** | `aicte.admin@demo.vidyasetu.example` | National overview, cross-institutional duplicates, audit ledger, policy configuration |
| **Institution Admin (CIT - TN)** | `admin.tn001@demo.vidyasetu.example` | Sanctioned seat meters (120 cap), batch upload, worklist |
| **Institution Admin (Apex - RJ)** | `admin.rj001@demo.vidyasetu.example` | Discrepancy worklist, dirty upload demo, remediation |
| **Student Portal** | `student.910000000001@demo.vidyasetu.example` *(or APAAR: `910000000001` + OTP `123456`)* | DPDP consent management, digital verifiable certificate download with QR |
| **Public Verifier** | *No login required* (`/verify`) | Instant tiered credential check |

---

## 🧪 Testing & Verification

### 1. Run Backend Tests (6/6 Passed):
```bash
cd backend
mvn test
```
*Validates APAAR normalization, isolation forest determinism, and OpenPDF certificate generation with ZXing QR decoding.*

### 2. Run Frontend Tests:
```bash
cd frontend
npm test
```
*Validates RBAC permission matrices, navigation routes, and tenant access rules.*

### 3. Run Automated Smoke Test Suite:
```bash
# On Linux / macOS / Git Bash:
bash scripts/smoke.sh http://localhost:8080

# On Windows PowerShell:
.\scripts\smoke.ps1 -BaseUrl http://localhost:8080
```

### 4. Reset Demo State (Restores in < 20s):
```bash
# On Linux / macOS / Git Bash:
bash scripts/reset-demo.sh http://localhost:8080

# On Windows PowerShell:
.\scripts\reset-demo.ps1 -BaseUrl http://localhost:8080
```

---

## 📂 Project Structure

```text
SIH_V2/
├── backend/                  # Spring Boot 3.5.6 (Java 21) modular monolith
│   ├── src/main/java/        # Security, Ingestion, Anomaly, Verification, Consent, Audit
│   └── src/main/resources/   # Flyway migrations (V001, V002) and application profiles
├── frontend/                 # React 18, Vite, TypeScript, Tailwind CSS
│   ├── src/pages/            # AICTE, Institution, Verification & Student portals
│   ├── src/components/       # Handcrafted UI primitives, toasts, tables, modals
│   └── src/lib/              # API client, RBAC, SSE events bus, i18n
├── seed/                     # Test data & live-demo upload files
│   ├── demo_upload_clean.csv # 50 valid rows (fills intake to 100%)
│   ├── demo_upload_dirty.csv # 60 rows (demonstrating all 15 error codes)
│   ├── demo_upload_excess.csv# 15 rows (triggers over-intake breach)
│   └── templates/            # CSV download template for institutions
├── scripts/                  # Automated smoke testing, reset, and HMAC client
│   ├── smoke.sh / smoke.ps1
│   ├── reset-demo.sh / reset-demo.ps1
│   └── hmac-client-example.py / hmac-client-example.sh
└── docs/                     # Comprehensive architectural documentation
    ├── MASTER_PLAN.md        # Complete implementation specifications
    ├── DEMO_SCRIPT.md        # 7-minute time-coded presentation script
    ├── ARCHITECTURE.md       # C4 diagrams, cryptographic algorithms, pipeline
    └── SUBMISSION.md         # SIH competition submission dossier
```

---

## 📜 Documentation Reference
- **[7-Minute Live Demo Script](docs/DEMO_SCRIPT.md)**: Time-coded walkthrough for jury presentations.
- **[System Architecture](docs/ARCHITECTURE.md)**: C4 container diagrams, data pipeline, and cryptographic proofs.
- **[SIH Technical Submission](docs/SUBMISSION.md)**: Detailed problem breakdown, impact metrics, and production roadmap.

---

## ⚖️ License
Released under the MIT License. Developed for the AICTE Smart India Hackathon.
