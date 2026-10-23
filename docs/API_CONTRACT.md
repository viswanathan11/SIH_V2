# VidyaSetu — Frontend ⇄ Backend API contract

Base URL: `/api/v1` (Vite dev server proxies `/api`, `/.well-known`, `/v3`, `/swagger-ui` to `http://localhost:8080`).
All JSON is camelCase. Errors are RFC 7807 `application/problem+json`: `{ "title", "status", "detail" }`.
Auth: `Authorization: Bearer <accessToken>`. Refresh token in HttpOnly cookie `vs_refresh` (path `/api/v1/auth`).
Where a browser must open a URL directly (PDF, CSV download, SSE fallback) `?access_token=<jwt>` is also accepted.

Paged responses: `{ content: T[], page: number, size: number, totalElements: number, totalPages: number }` (query `page` 0-based, `size`).

Academic year of record: `2026-27`.

## Enums
- Roles: `SUPER_ADMIN, AICTE_ADMIN, AICTE_ANALYST, AICTE_AUDITOR, INSTITUTION_ADMIN, INSTITUTION_OPERATOR, REGULATOR, EMPLOYER_VERIFIER, STUDENT, API_CLIENT`
- Permissions: `institution:read institution:manage student:read_masked student:read_full student:write upload:create upload:read validation:read validation:resolve validation:waive anomaly:read anomaly:review dashboard:national dashboard:institution report:generate report:export record:issue record:revoke verify:perform consent:manage_own record:read_own audit:read user:manage apiclient:manage policy:manage registry:lookup`
- Severity: `INFO | WARN | ERROR | CRITICAL`
- Row outcome: `ACCEPTED | FLAGGED | REJECTED | RESUBMITTED`
- Enrolment status: `SUBMITTED | VALIDATED | FLAGGED | VERIFIED | WITHDRAWN | TRANSFERRED | GRADUATED`
- Seat class: `REGULAR | SUPERNUMERARY | EXCESS`
- Issue status: `OPEN | ACKNOWLEDGED | RESOLVED | WAIVED`
- Anomaly status: `OPEN | UNDER_REVIEW | CONFIRMED | DISMISSED`; risk level `LOW | MEDIUM | HIGH | CRITICAL`
- Batch status: `QUEUED | PROCESSING | COMPLETED | FAILED`
- Tiers: `PUBLIC | L1 | L2 | L3`

## Public / config
- `GET /public/config` → `{ demoMode: boolean, academicYear: "2026-27", appName: "VidyaSetu" }`
- `GET /public/demo-accounts` (demo mode only) → `[{ role, label, email, password, group }]` (students: `{ role:"STUDENT", label, apaarId, otp:"123456" }`)

## Auth
- `POST /auth/login` `{ email, password }` → `AuthResponse` or `{ mfaRequired: true, mfaToken }`
- `POST /auth/mfa/verify` `{ mfaToken, code }` → `AuthResponse`
- `POST /auth/student/otp-request` `{ apaarId }` → `{ sent: true, demoOtp?: "123456" }`
- `POST /auth/student/otp-verify` `{ apaarId, otp }` → `AuthResponse`
- `POST /auth/refresh` (cookie) → `AuthResponse`
- `POST /auth/logout` → 204
- `GET /me` → `User`

`AuthResponse = { accessToken, expiresIn, user: User }`
`User = { id, email, fullName, roles: string[], permissions: string[], institutionId?: string, institutionCode?: string, institutionName?: string, studentId?: string, apaarMasked?: string, org?: string }`

## Master data
- `GET /institutions` → `InstitutionSummary[]` (institution roles get only their own)
  `InstitutionSummary = { id, aicteId, name, state, district, institutionType, approvedIntake, enrolled, fillRatio, riskScore, riskLevel, openIssues, openCritical }`
- `GET /institutions/{id}` → `InstitutionSummary & { programs: Program[] }`
- `GET /institutions/{id}/programs` → `Program[]`
  `Program = { id, institutionId, programCode, name, level ("DIPLOMA"|"UG"|"PG"|"PGDM"), academicYear, approvedIntake, approvedLateralIntake, tfwEnabled, fnOciEnabled }`
- `PUT /programs/{id}` `{ approvedIntake?, approvedLateralIntake?, tfwEnabled?, fnOciEnabled? }` → `Program`

## Ingestion
- `POST /uploads` multipart: `file` (CSV/XLSX), optional `institutionId` (required for AICTE users) → `Batch` (202; processing is async)
- `POST /students/batch` JSON (see master plan Appendix A), header `Idempotency-Key` → `Batch`
- `GET /uploads?institutionId=&page=&size=` → `Page<Batch>`
- `GET /uploads/{id}` → `Batch & { issueSummary: [{ ruleCode, severity, count }] }`
- `GET /uploads/{id}/rows?outcome=REJECTED|FLAGGED|ACCEPTED` → `StagingRow[]`
- `GET /uploads/{id}/errors.csv` → CSV download
- `POST /uploads/rows/{rowId}/resubmit` `{ raw: Record<string,string> }` → `{ batch: Batch, outcome, issues: Issue[] }`
- `GET /uploads/template.csv` → template CSV

`Batch = { id, institutionId, institutionCode, institutionName, source ("UI"|"API"|"RESUBMIT"|"SEED"), fileName, status, totalRows, processedRows, acceptedRows, flaggedRows, rejectedRows, createdAt, completedAt, uploadedBy }`
`StagingRow = { id, batchId, rowNo, raw: Record<string,string>, outcome, issues: Issue[] }`

Upload columns: `apaar_id,institute_student_id,full_name,dob,gender,nationality,residency_status,admission_quota,social_category,program_code,entry_type,admission_date,academic_year,passport_no,oci_card_no,mobile,email`

- `GET /enrolments?institutionId=&programCode=&status=&seatClass=&q=&page=&size=` → `Page<Enrolment>`
  `Enrolment = { id, studentId, apaarId (masked unless student:read_full: "XXXX XXXX 1234"), apaarMasked, fullName, dob, gender, instituteStudentId, institutionId, institutionCode, institutionName, programId, programCode, programName, academicYear, entryType, residencyStatus, admissionQuota, socialCategory, nationality, status, seatClass, admissionDate, version }`
- `PUT /enrolments/{id}` partial `Enrolment` fields → `Enrolment`
- `POST /enrolments/{id}/status` `{ status, note }` → `Enrolment`
- `GET /students/{apaarId}` → `{ student: { id, apaarMasked, fullName, dob, gender }, enrolments: Enrolment[], records: IssuedRecord[] }`
- `GET /search?q=` → `[{ type: "STUDENT"|"INSTITUTION", id, label, sub, link }]`

## Validation
- `GET /issues?institutionId=&severity=&ruleCode=&status=&batchId=&page=&size=` → `Page<Issue>`
  `Issue = { id, batchId, stagingRowId, enrolmentId, institutionId, institutionCode, ruleCode, severity, field, message, hint, context, status, resolutionNote, resolvedBy, createdAt, apaarMasked, rowNo }`
- `GET /issues/{id}` → `Issue`
- `POST /issues/{id}/acknowledge|resolve|waive` `{ note }` → `Issue`
- `GET /issues/rules` → `[{ code, group, severity, description, hint }]`
- `GET /issues/duplicates` → `[{ apaarMasked, studentName, issueIds: string[], status: "OPEN"|"RESOLVED", enrolments: [{ enrolmentId, institutionId, institutionCode, institutionName, programCode, status, admissionDate }] }]`
- `POST /validate/preview` multipart `file` + `institutionId?` → `{ summary: { total, accepted, flagged, rejected }, rows: [{ rowNo, outcome, issues: Issue[] }] }`

## Anomalies & risk
- `GET /anomalies?status=&severity=&institutionId=` → `Anomaly[]`
  `Anomaly = { id, institutionId, institutionCode, institutionName, programCode?, academicYear, kind, score (0..1), severity, reasons: [{ feature, value, peerMedian?, z?, text }], explanation, status, reviewNote, reviewedBy, createdAt }`
- `GET /anomalies/{id}` → `Anomaly`
- `POST /anomalies/{id}/review` `{ status, note }` → `Anomaly`
- `POST /anomalies/recompute` → `{ anomalies: number }`
- `GET /institutions/{id}/risk` → `{ institutionId, riskScore (0..100), level, components: { rules, isolation, stats }, topReasons: string[], computedAt }`

## Analytics
- `GET /analytics/national/summary` → `{ institutionsReporting, totalInstitutions, totalEnrolment, totalApprovedIntake, fillRatio, openCritical, openIssues, anomaliesOpen, anomaliesUnderReview, validationPassRate, excessSeats, supernumeraryTotal }`
- `GET /analytics/national/by-state` → `[{ state, stateCode, institutions, approvedIntake, enrolled, fillRatio, riskAvg, riskMax }]`
- `GET /analytics/national/by-level` → `[{ level, approvedIntake, enrolled }]`
- `GET /analytics/national/trend` → `[{ academicYear, approvedIntake, enrolled }]`
- `GET /analytics/national/supernumerary` → `[{ category ("FN_OCI_PIO"|"CIWGC"|"TFW"|"NRI"), used, cap }]`
- `GET /analytics/national/issues-by-rule` → `[{ ruleCode, severity, count }]`
- `GET /analytics/national/top-risk` → `[{ institutionId, institutionCode, institutionName, state, riskScore, level, topReasons }]`
- `GET /analytics/excess-register` → `[{ institutionId, institutionCode, institutionName, programCode, programName, approvedIntake, regular, excess }]`
- `GET /analytics/institution/{id}/meters` → `[{ programId, programCode, programName, level, approvedIntake, regular, supernumerary, excess, lateral, approvedLateralIntake, fnOci, ciwgc, tfw, nri, fillPct }]`
- `GET /analytics/institution/{id}/quality` → `{ submitted, accepted, flagged, rejected, qualityScore (0..100), issuesPer100, openIssues, trend: [{ batchId, date, score }] }`

## Real-time
- `GET /stream/events` SSE (use `@microsoft/fetch-event-source` with Authorization header). Each message: `event: <type>`, `data: Event`.
  `Event = { id, type, ts, institutionId?, institutionCode?, severity, message, payload }`
  Types: `UPLOAD_RECEIVED, VALIDATION_PROGRESS, VALIDATION_COMPLETED, ISSUE_RAISED, INTAKE_THRESHOLD, ANOMALY_FLAGGED, RECORD_VERIFIED, HEARTBEAT`
- `GET /events/recent` → `Event[]` (last 50, scope filtered; polling fallback every 5 s)

## Verification, records, consent
- `POST /records/issue` `{ enrolmentId }` → `IssuedRecord`
  `IssuedRecord = { id, jti, enrolmentId, recordType, jws, verifyUrl ("/v/<jti>"), issuedAt, expiresAt, revokedAt?, revokeReason? }`
- `GET /records?enrolmentId=` → `IssuedRecord[]`
- `POST /records/{jti}/revoke` `{ reason }` → `IssuedRecord`
- `GET /verify/{jti}` (public, no auth) → `{ status: "VALID"|"REVOKED"|"EXPIRED"|"NOT_FOUND", signatureValid, issuer, issuedAt, expiresAt, apaarMasked, institutionAicteId?, programCode?, academicYear? }` (PUBLIC tier only shows apaarMasked/issuer/dates; institution/program shown if caller authenticated L1+)
- `POST /verify/jws` (public) `{ jws }` → same as above
- `POST /verify/lookup` `{ apaarId, dob, requestedTier: "L1"|"L2"|"L3", purpose }` →
  `{ result: "FOUND"|"NOT_FOUND"|"DOB_MISMATCH", tierRequested, tierGranted, consentRequired, consentRequest?: { id, status }, data?: { apaarMasked, institutionAicteId, institutionName, programCode, programName, academicYear, status, fullName?, instituteStudentId?, dob?, gender? }, records: IssuedRecord[] }`
- `GET /.well-known/jwks.json` (root path, no /api/v1)
- `GET /consents` (student) → `[{ id, granteeType, granteeId, granteeName, scope, purpose, grantedAt, expiresAt, revokedAt, active }]`
- `POST /consents` `{ granteeOrg, scope, purpose, days }` → consent
- `DELETE /consents/{id}` → 204
- `GET /consent-requests` → student: requests to them; verifier: requests they made. `[{ id, studentApaarMasked, requesterName, requesterOrg, requestedScope, purpose, status ("PENDING"|"APPROVED"|"DENIED"), createdAt }]`
- `POST /consent-requests/{id}/approve` `{ days? }` / `POST /consent-requests/{id}/deny`
- `GET /me/record` (student) → `{ student: {...}, enrolments: Enrolment[], records: IssuedRecord[] }`
- `GET /me/access-history` → `[{ ts, requesterName, requesterOrg, purpose, scopeGranted, result }]`

## Certificates
- `GET /certificates/preview?enrolmentId=` → `application/pdf`
- `POST /certificates/render` `{ enrolmentId, template }` → `application/pdf`

## Reports
- `GET /reports/types` → `[{ type, name, description }]` types: `ENROLMENT_SUMMARY, INTAKE_UTILISATION, EXCESS_REGISTER, SUPERNUMERARY, DUPLICATE_REGISTER, DATA_QUALITY, RISK_WATCHLIST, AUDIT_EXPORT, VERIFICATION_ACTIVITY`
- `POST /reports` `{ type, format: "CSV"|"XLSX"|"PDF", filters?: { institutionId?, state? } }` → `Report`
- `GET /reports` → `Report[]`; `GET /reports/{id}` → `Report`; `GET /reports/{id}/download` → file
  `Report = { id, type, format, status, createdAt, createdBy, rowCount, downloadUrl }`

## Admin, audit, integration
- `GET /users` → `[{ id, email, fullName, roles, institutionCode, status, lastLoginAt }]`; `POST /users` `{ email, fullName, password, role, institutionId? }`; `PUT /users/{id}` `{ status?, role? }`
- `GET /api-clients` → `[{ id, name, institutionId, institutionCode, keyId, scopes, status, webhookUrl }]`
- `POST /api-clients` `{ name, institutionId, scopes, webhookUrl? }` → client + `{ secret, webhookSecret }` (shown once)
- `PUT /api-clients/{id}` `{ webhookUrl?, status? }`; `POST /api-clients/{id}/test-webhook` → `{ delivered, statusCode }`
- `GET /webhook-deliveries?clientId=` → `[{ id, clientId, event, url, statusCode, attempts, success, createdAt }]`
- `GET /policy` → `[{ key, value, description, updatedBy, updatedAt }]`; `PUT /policy/{key}` `{ value }`
- `GET /audit?page=&size=&action=&actor=` → `Page<{ id, ts, actorId, actorEmail, actorRole, action, entityType, entityId, institutionId, ip, details, hash, prevHash }>`
- `GET /audit/verify-chain` → `{ intact, checked, firstBrokenId? }`
- `GET /notifications` → `[{ id, ts, title, body, severity, read }]`
- `POST /demo/reset` (AICTE_ADMIN, demo mode) → `{ ok: true, ms }`
