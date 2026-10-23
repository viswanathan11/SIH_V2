# Smart India Hackathon (SIH) — Technical Submission Dossier

## Project Name: VidyaSetu
**APAAR-Based Student Data Management, Verification & Anomaly Detection System for AICTE**  
**Ministry / Organization:** All India Council for Technical Education (AICTE), Ministry of Education  
**Track:** Smart Education / Governance  

---

## 1. Executive Summary & Problem Understanding
The All India Council for Technical Education oversees over 10,000 technical institutions admitting millions of students annually. However, the current regulatory paradigm relies on retrospective, aggregate, self-declared enrolment quotas. This structural limitation allows systemic vulnerabilities:
1. **Ghost Enrolments & Cap Breaches:** Institutions over-admitting students beyond approved AICTE intake caps.
2. **Concurrent Dual Enrolments:** Students simultaneously registered across multiple regular degree programs across state lines.
3. **Delayed Regulatory Visibility:** Discrepancies often remain undetected for months until end-of-year audits.
4. **Credential Forgery:** Physical degrees and paper certificates lack verifiable cryptographic linkage to the student's authentic national identity.

**VidyaSetu** addresses these challenges by anchoring institutional student admissions to the **Automated Permanent Academic Account Registry (APAAR ID)** — India's "One Nation, One Student ID" initiative — providing real-time verification, cryptographic auditability, and automated anomaly detection.

---

## 2. Key Innovations

| Innovation | Implementation in VidyaSetu | Regulatory Impact |
|---|---|---|
| **Intelligent Anomaly Engine** | Zero-dependency seeded Isolation Forest + heuristic rule aggregators. | Instantly detects batch clustering, unusual off-hour uploads, and intake over-fill patterns. |
| **Cryptographic Verifiable Records** | RFC 7515 Compact JWS signed with ES256 (ECDSA P-256) + JWKS export. | Enables offline, instant credential verification globally without querying central databases. |
| **DPDP-Compliant Selective Disclosure** | Tiered disclosure model (PUBLIC, L1, L2, L3) with digital consent workflow. | Full adherence to Digital Personal Data Protection Act 2023 principles. Zero Aadhaar storage. |
| **Tamper-Evident Audit Chain** | SHA-256 block-chained append-only ledger enforced by database triggers. | Mathematically proves zero unauthorized data tampering or administrative record alteration. |
| **Real-Time Event Stream** | Low-latency Server-Sent Events (SSE) with tenant and role-based filtering. | Real-time national monitoring feed for AICTE headquarters as data is ingested. |

---

## 3. Planted Demonstration Scenarios (S1 – S10)
VidyaSetu comes pre-seeded with 10 deterministic real-world test scenarios:

| Code | Institution | Planted Scenario & Pattern | Detection Mechanism |
|---|---|---|---|
| **S1** | INST-DL-001 | 100% admissions submitted in final 48 hours | Isolation Forest clustering flag |
| **S2** | INST-MH-001 | 80% admissions processed between 23:00 - 05:00 | Temporal anomaly detector |
| **S3** | INST-UP-001 | 3 students actively enrolled concurrently in INST-RJ-001 | Cross-institution dual enrolment register |
| **S4** | INST-KA-001 | Extreme intake under-fill (< 15% capacity) | At-risk institution watchlist |
| **S5** | INST-TN-001 | Sanctioned intake reached (120/120); excess batch rejected | Group D seat cap engine |
| **S6** | INST-GJ-001 | Supernumerary quota exceeded (TFW seats > 5%) | Quota allocation threshold validator |
| **S7** | INST-WB-001 | Foreign National quota claimed without valid passport | Group C quota compliance engine |
| **S8** | INST-AP-001 | High volume of duplicate student IDs within batch | Internal duplicate staging detector |
| **S9** | INST-KL-001 | Systemic name spelling mismatches vs APAAR registry | Trigram fuzzy Levenshtein distance check |
| **S10**| INST-TN-001 | Clean, compliant baseline (control case) | Control baseline (Risk Level: LOW) |

---

## 4. Technical Architecture & Stack

### Backend
- **Framework:** Spring Boot 3.5.6 on Java 21 (LTS).
- **Security:** Stateless JWT authentication, Spring Security, BCrypt, ES256 cryptographic signing.
- **Data Protection:** AES-256-GCM envelope encryption for sensitive database columns.
- **Database:** PostgreSQL 16 (production) with Zonky Embedded PostgreSQL fallback for zero-dependency local runs.
- **Database Migrations:** Flyway versioned migrations (`V001__baseline.sql`, `V002__rbac_and_policy.sql`).
- **PDF Generation:** OpenPDF with ZXing 2D Data Matrix / QR Code generation.

### Frontend
- **Framework:** React 18 with TypeScript and Vite.
- **Styling:** Tailwind CSS with custom government-grade accessible typography.
- **State & Query:** TanStack React Query v5 for cached server state and optimistic updates.
- **Real-Time:** Microsoft Fetch Event Source for resilient Server-Sent Events (SSE).
- **Internationalization:** Multi-language support (English, Hindi, Tamil).

---

## 5. Security & Privacy Compliance (DPDP Act 2023)
1. **Zero Raw Aadhaar Storage:** The system strictly rejects and never stores Aadhaar numbers. Student identities are keyed solely by their 12-digit APAAR ID.
2. **Purpose Limitation & Data Minimization:** Third-party verifiers are only granted access to the minimal required data attributes.
3. **Student Consent Vault:** Verifiers requesting personal credentials (Tier L2) trigger an asynchronous digital consent request that the student must approve via their student portal.
4. **Append-Only Immutability:** Audit records cannot be altered or purged, even by database superusers, providing forensic evidence for regulatory hearings.

---

## 6. Simulated vs Production Transition Strategy

| Component | Hackathon Demonstration | Production AICTE Roadmap |
|---|---|---|
| **APAAR Verification** | In-memory synthetic mock registry with ~6,000 records | National Academic Depository (NAD) / DigiLocker ABC API gateway |
| **Institutional ERP** | HMAC-SHA256 REST API + CSV/XLSX bulk uploads | AICTE Webhook Gateway & National Education Technology Forum (NETF) connectors |
| **Key Management** | Local ES256 key pair with JWKS export | Hardware Security Module (HSM) / India PKI e-Sign Infrastructure |
| **Database** | Embedded or Dockerized PostgreSQL 16 | High-Availability Managed PostgreSQL Cluster with Read Replicas |
