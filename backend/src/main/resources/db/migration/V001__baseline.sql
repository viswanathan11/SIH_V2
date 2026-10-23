-- VidyaSetu baseline schema
DO $$ BEGIN
  BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pg_trgm unavailable, fuzzy matching falls back to Java';
  END;
END $$;

-- ---------- master data ----------
CREATE TABLE institutions (
  id uuid PRIMARY KEY,
  aicte_id varchar(32) NOT NULL UNIQUE,
  name varchar(200) NOT NULL,
  institution_type varchar(60) NOT NULL,
  state varchar(60) NOT NULL,
  state_code varchar(4) NOT NULL,
  district varchar(80),
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE programs (
  id uuid PRIMARY KEY,
  institution_id uuid NOT NULL REFERENCES institutions(id),
  program_code varchar(40) NOT NULL,
  level varchar(10) NOT NULL,
  name varchar(160) NOT NULL,
  academic_year varchar(7) NOT NULL,
  approved_intake int NOT NULL CHECK (approved_intake >= 0),
  approved_lateral_intake int NOT NULL DEFAULT 0,
  tfw_enabled boolean NOT NULL DEFAULT false,
  fn_oci_enabled boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (institution_id, program_code, academic_year)
);

CREATE TABLE policy_config (
  key varchar(80) PRIMARY KEY,
  value text NOT NULL,
  description text,
  updated_by varchar(120),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE enrolment_stats_history (
  institution_id uuid NOT NULL REFERENCES institutions(id),
  program_code varchar(40) NOT NULL,
  academic_year varchar(7) NOT NULL,
  approved_intake int NOT NULL,
  enrolled int NOT NULL,
  PRIMARY KEY (institution_id, program_code, academic_year)
);

-- ---------- identity & access ----------
CREATE TABLE roles (code varchar(40) PRIMARY KEY, description text);
CREATE TABLE permissions (code varchar(60) PRIMARY KEY, description text);
CREATE TABLE role_permissions (
  role_code varchar(40) REFERENCES roles(code),
  permission_code varchar(60) REFERENCES permissions(code),
  PRIMARY KEY (role_code, permission_code)
);

CREATE TABLE users (
  id uuid PRIMARY KEY,
  email varchar(160) NOT NULL UNIQUE,
  password_hash varchar(200) NOT NULL,
  full_name varchar(160) NOT NULL,
  org varchar(80),
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  institution_id uuid REFERENCES institutions(id),
  mfa_secret varchar(64),
  failed_attempts int NOT NULL DEFAULT 0,
  locked_until timestamptz,
  last_login_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE user_roles (
  user_id uuid REFERENCES users(id) ON DELETE CASCADE,
  role_code varchar(40) REFERENCES roles(code),
  PRIMARY KEY (user_id, role_code)
);

CREATE TABLE refresh_tokens (
  id uuid PRIMARY KEY,
  family_id uuid NOT NULL,
  subject varchar(80) NOT NULL,
  token_hash varchar(80) NOT NULL UNIQUE,
  used boolean NOT NULL DEFAULT false,
  revoked boolean NOT NULL DEFAULT false,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE api_clients (
  id uuid PRIMARY KEY,
  name varchar(120) NOT NULL,
  institution_id uuid REFERENCES institutions(id),
  key_id varchar(80) NOT NULL UNIQUE,
  secret_enc text NOT NULL,
  scopes text NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  webhook_url text,
  webhook_secret_enc text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE webhook_deliveries (
  id uuid PRIMARY KEY,
  client_id uuid REFERENCES api_clients(id),
  event varchar(60) NOT NULL,
  url text NOT NULL,
  payload text,
  status_code int,
  attempts int NOT NULL DEFAULT 0,
  success boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE idempotency_keys (
  key varchar(120) PRIMARY KEY,
  batch_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- ---------- students & enrolments ----------
CREATE TABLE students (
  id uuid PRIMARY KEY,
  apaar_id char(12) NOT NULL UNIQUE,
  full_name varchar(160) NOT NULL,
  name_norm varchar(160) NOT NULL,
  dob date NOT NULL,
  gender varchar(1),
  identity_key varchar(80),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_students_dob_gender ON students(dob, gender);

CREATE TABLE upload_batches (
  id uuid PRIMARY KEY,
  institution_id uuid NOT NULL REFERENCES institutions(id),
  uploaded_by varchar(160),
  source varchar(20) NOT NULL,
  file_name varchar(255),
  sha256 varchar(64),
  status varchar(20) NOT NULL,
  total_rows int NOT NULL DEFAULT 0,
  processed_rows int NOT NULL DEFAULT 0,
  accepted_rows int NOT NULL DEFAULT 0,
  flagged_rows int NOT NULL DEFAULT 0,
  rejected_rows int NOT NULL DEFAULT 0,
  error_message text,
  created_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz
);
CREATE INDEX idx_batches_inst ON upload_batches(institution_id, created_at DESC);
CREATE INDEX idx_batches_sha ON upload_batches(institution_id, sha256);

CREATE TABLE enrolments (
  id uuid PRIMARY KEY,
  student_id uuid NOT NULL REFERENCES students(id),
  institution_id uuid NOT NULL REFERENCES institutions(id),
  program_id uuid NOT NULL REFERENCES programs(id),
  academic_year varchar(7) NOT NULL,
  institute_student_id varchar(60),
  entry_type varchar(20) NOT NULL DEFAULT 'FIRST_YEAR',
  residency_status varchar(20) NOT NULL DEFAULT 'RESIDENT_INDIAN',
  admission_quota varchar(30) NOT NULL DEFAULT 'STATE_CAP',
  social_category varchar(10),
  nationality varchar(4),
  passport_no_enc text,
  oci_card_no_enc text,
  mobile_enc text,
  email_enc text,
  admission_date date,
  status varchar(20) NOT NULL DEFAULT 'SUBMITTED',
  seat_class varchar(20) NOT NULL DEFAULT 'REGULAR',
  apaar_verified boolean NOT NULL DEFAULT true,
  source_batch_id uuid REFERENCES upload_batches(id),
  version int NOT NULL DEFAULT 1,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (student_id, program_id, academic_year)
);
CREATE INDEX idx_enrol_inst_year ON enrolments(institution_id, academic_year);
CREATE INDEX idx_enrol_prog ON enrolments(program_id);
CREATE INDEX idx_enrol_student ON enrolments(student_id);

CREATE TABLE enrolment_history (
  id uuid PRIMARY KEY,
  enrolment_id uuid NOT NULL REFERENCES enrolments(id) ON DELETE CASCADE,
  version int NOT NULL,
  diff text,
  changed_by varchar(160),
  changed_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE staging_rows (
  id uuid PRIMARY KEY,
  batch_id uuid NOT NULL REFERENCES upload_batches(id) ON DELETE CASCADE,
  row_no int NOT NULL,
  raw text NOT NULL,
  normalized text,
  outcome varchar(20)
);
CREATE INDEX idx_staging_batch ON staging_rows(batch_id, row_no);

CREATE TABLE validation_issues (
  id uuid PRIMARY KEY,
  batch_id uuid REFERENCES upload_batches(id) ON DELETE CASCADE,
  staging_row_id uuid REFERENCES staging_rows(id) ON DELETE CASCADE,
  enrolment_id uuid REFERENCES enrolments(id) ON DELETE CASCADE,
  institution_id uuid NOT NULL REFERENCES institutions(id),
  rule_code varchar(20) NOT NULL,
  severity varchar(10) NOT NULL,
  field varchar(60),
  message text NOT NULL,
  hint text,
  context text,
  apaar_masked varchar(20),
  row_no int,
  status varchar(20) NOT NULL DEFAULT 'OPEN',
  resolved_by varchar(160),
  resolution_note text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_issues_inst ON validation_issues(institution_id, status);
CREATE INDEX idx_issues_rule ON validation_issues(rule_code);
CREATE INDEX idx_issues_batch ON validation_issues(batch_id);
CREATE INDEX idx_issues_enrol ON validation_issues(enrolment_id);

-- ---------- analytics ----------
CREATE TABLE anomalies (
  id uuid PRIMARY KEY,
  institution_id uuid NOT NULL REFERENCES institutions(id),
  program_id uuid REFERENCES programs(id),
  academic_year varchar(7) NOT NULL,
  kind varchar(60) NOT NULL,
  score double precision NOT NULL,
  severity varchar(10) NOT NULL,
  features text,
  reasons text,
  explanation text,
  status varchar(20) NOT NULL DEFAULT 'OPEN',
  review_note text,
  reviewed_by varchar(160),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_anom_inst ON anomalies(institution_id);

CREATE TABLE institution_risk (
  institution_id uuid NOT NULL REFERENCES institutions(id),
  academic_year varchar(7) NOT NULL,
  risk_score double precision NOT NULL,
  level varchar(10) NOT NULL,
  components text,
  top_reasons text,
  computed_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (institution_id, academic_year)
);

-- ---------- registry (MOCK) ----------
CREATE TABLE apaar_registry_mock (
  apaar_id char(12) PRIMARY KEY,
  full_name varchar(160) NOT NULL,
  dob date NOT NULL,
  gender varchar(1),
  status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  created_on date
);

-- ---------- consent & verification ----------
CREATE TABLE consents (
  id uuid PRIMARY KEY,
  student_id uuid NOT NULL REFERENCES students(id),
  grantee_type varchar(30) NOT NULL,
  grantee_id varchar(80) NOT NULL,
  grantee_name varchar(160),
  scope varchar(10) NOT NULL,
  purpose text,
  granted_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz,
  revoked_at timestamptz
);
CREATE TABLE consent_requests (
  id uuid PRIMARY KEY,
  student_id uuid NOT NULL REFERENCES students(id),
  requester_user_id uuid REFERENCES users(id),
  requester_org varchar(80),
  requested_scope varchar(10) NOT NULL,
  purpose text,
  status varchar(20) NOT NULL DEFAULT 'PENDING',
  created_at timestamptz NOT NULL DEFAULT now(),
  decided_at timestamptz
);
CREATE TABLE issued_records (
  id uuid PRIMARY KEY,
  student_id uuid NOT NULL REFERENCES students(id),
  enrolment_id uuid REFERENCES enrolments(id) ON DELETE SET NULL,
  record_type varchar(40) NOT NULL,
  jti varchar(64) NOT NULL UNIQUE,
  jws text NOT NULL,
  issued_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz,
  revoked_at timestamptz,
  revoke_reason text
);
CREATE TABLE verification_requests (
  id uuid PRIMARY KEY,
  requester_user_id uuid,
  requester_name varchar(160),
  requester_org varchar(80),
  student_id uuid,
  apaar_masked varchar(20),
  record_jti varchar(64),
  purpose text,
  scope_granted varchar(10),
  result varchar(30),
  ip varchar(64),
  created_at timestamptz NOT NULL DEFAULT now()
);

-- ---------- notifications, reports, events ----------
CREATE TABLE notifications (
  id uuid PRIMARY KEY,
  user_id uuid,
  institution_id uuid,
  audience varchar(20) NOT NULL DEFAULT 'USER',
  title varchar(200) NOT NULL,
  body text,
  severity varchar(10) NOT NULL DEFAULT 'INFO',
  read boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE reports (
  id uuid PRIMARY KEY,
  type varchar(40) NOT NULL,
  format varchar(10) NOT NULL,
  filters text,
  status varchar(20) NOT NULL,
  row_count int,
  content bytea,
  created_by varchar(160),
  institution_id uuid,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- ---------- audit ----------
CREATE TABLE audit_log (
  id bigserial PRIMARY KEY,
  ts timestamptz NOT NULL,
  actor_id varchar(80),
  actor_email varchar(160),
  actor_role varchar(40),
  action varchar(60) NOT NULL,
  entity_type varchar(40),
  entity_id varchar(80),
  institution_id uuid,
  ip varchar(64),
  user_agent varchar(255),
  details text,
  prev_hash varchar(64) NOT NULL,
  hash varchar(64) NOT NULL
);

CREATE OR REPLACE FUNCTION audit_log_append_only() RETURNS trigger AS $$
BEGIN
  IF current_setting('vidyasetu.allow_audit_reset', true) = 'on' THEN
    RETURN COALESCE(NEW, OLD);
  END IF;
  RAISE EXCEPTION 'audit_log is append-only';
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_no_update BEFORE UPDATE OR DELETE ON audit_log
  FOR EACH ROW EXECUTE FUNCTION audit_log_append_only();
