// Formatting / masking helpers (pure, unit tested).

/** Strip spaces and hyphens. */
export function normalizeApaar(input: string | null | undefined): string {
  return (input || '').replace(/[\s-]/g, '');
}

export function isValidApaar(input: string | null | undefined): boolean {
  return /^\d{12}$/.test(normalizeApaar(input));
}

/** "910000000001" → "9100 0000 0001" (non-12-digit inputs returned trimmed). */
export function formatApaar(input: string | null | undefined): string {
  const n = normalizeApaar(input);
  if (!/^\d{12}$/.test(n)) return (input || '').trim();
  return `${n.slice(0, 4)} ${n.slice(4, 8)} ${n.slice(8)}`;
}

/** Mask to last 4: "910000000001" → "XXXX XXXX 0001". Already-masked values pass through. */
export function maskApaar(input: string | null | undefined): string {
  const raw = (input || '').trim();
  if (!raw) return '—';
  if (/x/i.test(raw)) return raw.toUpperCase();
  const n = normalizeApaar(raw);
  if (n.length < 4) return 'XXXX XXXX XXXX';
  return `XXXX XXXX ${n.slice(-4)}`;
}

const nf = new Intl.NumberFormat('en-IN');
export function fmtNum(n: number | null | undefined): string {
  if (n === null || n === undefined || Number.isNaN(n)) return '—';
  return nf.format(n);
}

/** Ratio (0..1+) → "87.5%". */
export function fmtPct(ratio: number | null | undefined, digits = 1): string {
  if (ratio === null || ratio === undefined || Number.isNaN(ratio)) return '—';
  return `${(ratio * 100).toFixed(digits)}%`;
}

/** Percentage already in 0..100 → "87.5%". Values ≤ 1.5 with fraction are treated as ratios. */
export function fmtPct100(v: number | null | undefined, digits = 0): string {
  if (v === null || v === undefined || Number.isNaN(v)) return '—';
  return `${v.toFixed(digits)}%`;
}

export function fmtDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

export function relTime(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) return '';
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return '';
  const s = Math.round((now - t) / 1000);
  if (s < 5) return 'just now';
  if (s < 60) return `${s}s ago`;
  const m = Math.round(s / 60);
  if (m < 60) return `${m}m ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h}h ago`;
  return `${Math.round(h / 24)}d ago`;
}

const SEV_RANK: Record<string, number> = { INFO: 0, LOW: 0, WARN: 1, MEDIUM: 1, ERROR: 2, HIGH: 2, CRITICAL: 3 };
export function severityRank(s: string | null | undefined): number {
  return SEV_RANK[(s || '').toUpperCase()] ?? 0;
}

/** Risk score 0..100 → level. */
export function riskLevel(score: number): 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' {
  if (score >= 75) return 'CRITICAL';
  if (score >= 50) return 'HIGH';
  if (score >= 25) return 'MEDIUM';
  return 'LOW';
}

/** Interpolated colour for a fill ratio: under-filled (amber) → healthy (teal/indigo) → over (red). */
export function fillColor(ratio: number | null | undefined): string {
  if (ratio === null || ratio === undefined || Number.isNaN(ratio)) return '#cbd5e1';
  if (ratio > 1.0001) return '#dc2626';
  if (ratio >= 0.9) return '#4338ca';
  if (ratio >= 0.7) return '#6366f1';
  if (ratio >= 0.5) return '#a5b4fc';
  if (ratio >= 0.3) return '#fcd34d';
  return '#f59e0b';
}

export function riskColor(score: number | null | undefined): string {
  if (score === null || score === undefined || Number.isNaN(score)) return '#cbd5e1';
  if (score >= 75) return '#b91c1c';
  if (score >= 50) return '#ea580c';
  if (score >= 25) return '#f59e0b';
  return '#16a34a';
}

/** Humanise ENUM_VALUES → "Enum values". */
export function humanize(s: string | null | undefined): string {
  if (!s) return '';
  const t = s.replace(/[_.-]+/g, ' ').toLowerCase().trim();
  return t.charAt(0).toUpperCase() + t.slice(1);
}

/** Template CSV header check: returns missing and unexpected columns. */
export function checkHeaders(actual: string[], expected: string[]): { missing: string[]; extra: string[]; ok: boolean } {
  const a = actual.map((h) => h.trim().toLowerCase());
  const missing = expected.filter((e) => !a.includes(e));
  const extra = a.filter((h) => h && !expected.includes(h));
  return { missing, extra, ok: missing.length === 0 };
}

export const TEMPLATE_COLUMNS = [
  'apaar_id', 'institute_student_id', 'full_name', 'dob', 'gender', 'nationality', 'residency_status',
  'admission_quota', 'social_category', 'program_code', 'entry_type', 'admission_date', 'academic_year',
  'passport_no', 'oci_card_no', 'mobile', 'email',
];
