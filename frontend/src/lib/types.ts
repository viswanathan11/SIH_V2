// Types mirroring docs/API_CONTRACT.md

export type Severity = 'INFO' | 'WARN' | 'ERROR' | 'CRITICAL';
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type RowOutcome = 'ACCEPTED' | 'FLAGGED' | 'REJECTED' | 'RESUBMITTED';
export type IssueStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED' | 'WAIVED';
export type AnomalyStatus = 'OPEN' | 'UNDER_REVIEW' | 'CONFIRMED' | 'DISMISSED';
export type BatchStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
export type Tier = 'PUBLIC' | 'L1' | 'L2' | 'L3';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PublicConfig {
  demoMode: boolean;
  academicYear: string;
  appName: string;
}

export interface DemoAccount {
  role: string;
  label: string;
  email?: string;
  password?: string;
  group?: string;
  apaarId?: string;
  otp?: string;
}

export interface User {
  id: string;
  email: string;
  fullName: string;
  roles: string[];
  permissions: string[];
  institutionId?: string;
  institutionCode?: string;
  institutionName?: string;
  studentId?: string;
  apaarMasked?: string;
  org?: string;
}

export interface AuthResponse {
  accessToken: string;
  expiresIn: number;
  user: User;
}

export interface MfaChallenge {
  mfaRequired: true;
  mfaToken: string;
}

export interface InstitutionSummary {
  id: string;
  aicteId: string;
  name: string;
  state: string;
  stateCode?: string;
  district: string;
  institutionType: string;
  approvedIntake: number;
  enrolled: number;
  fillRatio: number;
  riskScore: number;
  riskLevel: RiskLevel;
  openIssues: number;
  openCritical: number;
}

export interface Program {
  id: string;
  institutionId: string;
  programCode: string;
  name: string;
  level: 'DIPLOMA' | 'UG' | 'PG' | 'PGDM';
  academicYear: string;
  approvedIntake: number;
  approvedLateralIntake: number;
  tfwEnabled: boolean;
  fnOciEnabled: boolean;
}

export type InstitutionDetail = InstitutionSummary & { programs: Program[] };

export interface Batch {
  id: string;
  institutionId: string;
  institutionCode: string;
  institutionName: string;
  source: 'UI' | 'API' | 'RESUBMIT' | 'SEED';
  fileName: string;
  status: BatchStatus;
  totalRows: number;
  processedRows: number;
  acceptedRows: number;
  flaggedRows: number;
  rejectedRows: number;
  createdAt: string;
  completedAt?: string;
  uploadedBy?: string;
  issueSummary?: IssueSummaryItem[];
}

export interface IssueSummaryItem {
  ruleCode: string;
  severity: Severity;
  count: number;
}

export type BatchDetail = Batch & { issueSummary?: IssueSummaryItem[] };

export interface Issue {
  id: string;
  batchId?: string;
  stagingRowId?: string;
  enrolmentId?: string;
  institutionId?: string;
  institutionCode?: string;
  ruleCode: string;
  severity: Severity;
  field?: string;
  message: string;
  hint?: string;
  context?: unknown;
  status: IssueStatus;
  resolutionNote?: string;
  resolvedBy?: string;
  createdAt: string;
  apaarMasked?: string;
  rowNo?: number;
}

export interface StagingRow {
  id: string;
  batchId: string;
  rowNo: number;
  raw: Record<string, string>;
  outcome: RowOutcome;
  issues: Issue[];
}

export interface Enrolment {
  id: string;
  studentId: string;
  apaarId: string;
  apaarMasked: string;
  fullName: string;
  dob: string;
  gender: string;
  instituteStudentId: string;
  institutionId: string;
  institutionCode: string;
  institutionName: string;
  programId: string;
  programCode: string;
  programName: string;
  academicYear: string;
  entryType: string;
  residencyStatus: string;
  admissionQuota: string;
  socialCategory: string;
  nationality: string;
  status: string;
  seatClass: 'REGULAR' | 'SUPERNUMERARY' | 'EXCESS';
  admissionDate: string;
  version: number;
}

export interface SearchResult {
  type: 'STUDENT' | 'INSTITUTION';
  id: string;
  label: string;
  sub?: string;
  link?: string;
}

export interface RuleDef {
  code: string;
  group: string;
  severity: Severity;
  description: string;
  hint?: string;
}

export interface DuplicateEntry {
  apaarMasked: string;
  studentName: string;
  issueIds: string[];
  status: 'OPEN' | 'RESOLVED';
  enrolments: {
    enrolmentId: string;
    institutionId: string;
    institutionCode: string;
    institutionName: string;
    programCode: string;
    status: string;
    admissionDate: string;
  }[];
}

export interface AnomalyReason {
  feature: string;
  value: number | string;
  peerMedian?: number;
  z?: number;
  text: string;
}

export interface Anomaly {
  id: string;
  institutionId: string;
  institutionCode: string;
  institutionName: string;
  programCode?: string;
  academicYear: string;
  kind: string;
  score: number;
  severity: Severity | RiskLevel;
  reasons: AnomalyReason[];
  explanation: string;
  status: AnomalyStatus;
  reviewNote?: string;
  reviewedBy?: string;
  createdAt: string;
}

export interface RiskInfo {
  institutionId: string;
  riskScore: number;
  level: RiskLevel;
  components: { rules: number; isolation: number; stats: number };
  topReasons: string[];
  computedAt: string;
}

export interface NationalSummary {
  institutionsReporting: number;
  totalInstitutions: number;
  totalEnrolment: number;
  totalApprovedIntake: number;
  fillRatio: number;
  openCritical: number;
  openIssues: number;
  anomaliesOpen: number;
  anomaliesUnderReview: number;
  validationPassRate: number;
  excessSeats: number;
  supernumeraryTotal: number;
}

export interface StateStat {
  state: string;
  stateCode: string;
  institutions: number;
  approvedIntake: number;
  enrolled: number;
  fillRatio: number;
  riskAvg: number;
  riskMax: number;
}

export interface TopRisk {
  institutionId: string;
  institutionCode: string;
  institutionName: string;
  state: string;
  riskScore: number;
  level: RiskLevel;
  topReasons: string[];
}

export interface ExcessRow {
  institutionId: string;
  institutionCode: string;
  institutionName: string;
  programCode: string;
  programName: string;
  approvedIntake: number;
  regular: number;
  excess: number;
}

export interface Meter {
  programId: string;
  programCode: string;
  programName: string;
  level: string;
  approvedIntake: number;
  regular: number;
  supernumerary: number;
  excess: number;
  lateral: number;
  approvedLateralIntake: number;
  fnOci: number;
  ciwgc: number;
  tfw: number;
  nri: number;
  fillPct: number;
}

export interface Quality {
  submitted: number;
  accepted: number;
  flagged: number;
  rejected: number;
  qualityScore: number;
  issuesPer100: number;
  openIssues: number;
  trend: { batchId: string; date: string; score: number }[];
}

export interface VsEvent {
  id: string;
  type: string;
  ts: string;
  institutionId?: string;
  institutionCode?: string;
  severity: Severity;
  message: string;
  payload?: any;
}

export interface IssuedRecord {
  id: string;
  jti: string;
  enrolmentId: string;
  recordType: string;
  jws: string;
  verifyUrl: string;
  issuedAt: string;
  expiresAt: string;
  revokedAt?: string;
  revokeReason?: string;
}

export interface VerifyResult {
  status: 'VALID' | 'REVOKED' | 'EXPIRED' | 'NOT_FOUND';
  signatureValid: boolean;
  issuer: string;
  issuedAt: string;
  expiresAt: string;
  apaarMasked: string;
  institutionAicteId?: string;
  programCode?: string;
  academicYear?: string;
}

export interface LookupResult {
  result: 'FOUND' | 'NOT_FOUND' | 'DOB_MISMATCH';
  tierRequested: Tier;
  tierGranted: Tier;
  consentRequired: boolean;
  consentRequest?: { id: string; status: string };
  data?: {
    apaarMasked: string;
    institutionAicteId: string;
    institutionName: string;
    programCode: string;
    programName: string;
    academicYear: string;
    status: string;
    fullName?: string;
    instituteStudentId?: string;
    dob?: string;
    gender?: string;
  };
  records: IssuedRecord[];
}

export interface Consent {
  id: string;
  granteeType: string;
  granteeId: string;
  granteeName: string;
  scope: string;
  purpose: string;
  grantedAt: string;
  expiresAt: string;
  revokedAt?: string;
  active: boolean;
}

export interface ConsentRequest {
  id: string;
  studentApaarMasked: string;
  requesterName: string;
  requesterOrg: string;
  requestedScope: string;
  purpose: string;
  status: 'PENDING' | 'APPROVED' | 'DENIED';
  createdAt: string;
}

export interface StudentRecord {
  student: { id: string; apaarMasked: string; fullName: string; dob?: string; gender?: string };
  enrolments: Enrolment[];
  records: IssuedRecord[];
}

export interface AccessHistory {
  ts: string;
  requesterName: string;
  requesterOrg: string;
  purpose: string;
  scopeGranted: string;
  result: string;
}

export interface ReportType {
  type: string;
  name: string;
  description: string;
}

export interface Report {
  id: string;
  type: string;
  format: 'CSV' | 'XLSX' | 'PDF';
  status: string;
  createdAt: string;
  createdBy: string;
  rowCount?: number;
  downloadUrl?: string;
}

export interface AdminUser {
  id: string;
  email: string;
  fullName: string;
  roles: string[];
  institutionCode?: string;
  status: string;
  lastLoginAt?: string;
}

export interface ApiClient {
  id: string;
  name: string;
  institutionId: string;
  institutionCode: string;
  keyId: string;
  scopes: string[];
  status: string;
  webhookUrl?: string;
  secret?: string;
  webhookSecret?: string;
}

export interface WebhookDelivery {
  id: string;
  clientId: string;
  event: string;
  url: string;
  statusCode: number;
  attempts: number;
  success: boolean;
  createdAt: string;
}

export interface PolicyEntry {
  key: string;
  value: unknown;
  description?: string;
  updatedBy?: string;
  updatedAt?: string;
}

export type PolicyConfig = PolicyEntry;

export interface VerificationLookupResult {
  result: 'FOUND' | 'NOT_FOUND' | 'DOB_MISMATCH' | 'CONSENT_REQUIRED' | 'CONSENT_PENDING' | 'REVOKED';
  tierRequested?: string;
  tierGranted?: string;
  consentRequired?: boolean;
  consentId?: string;
  data?: {
    apaarMasked?: string;
    fullName?: string;
    dob?: string;
    status?: string;
    institutionName?: string;
    institutionAicteId?: string;
    programName?: string;
    programCode?: string;
    instituteStudentId?: string;
    [key: string]: any;
  };
  records?: Array<{
    id: string;
    jti: string;
    issuedAt: string;
    [key: string]: any;
  }>;
}

export interface DuplicateGroup {
  studentName: string;
  apaarMasked: string;
  enrolments: any[];
  [key: string]: any;
}

export interface ChainVerificationResult {
  intact: boolean;
  checked?: number;
  totalChecked?: number;
  firstBrokenId?: string | number | null;
  [key: string]: any;
}

export interface AuditEntry {
  id: string | number;
  ts: string;
  actorId?: string;
  actorEmail?: string;
  actorRole?: string;
  action: string;
  entityType?: string;
  entityId?: string;
  institutionId?: string;
  ip?: string;
  details?: unknown;
  hash: string;
  prevHash?: string;
}
