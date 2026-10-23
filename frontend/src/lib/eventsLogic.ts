// Pure event-handling logic (unit tested).
import type { VsEvent } from './types';
import type { ToastKind } from '../components/Toast';

export const TOAST_TYPES = ['VALIDATION_COMPLETED', 'INTAKE_THRESHOLD', 'ANOMALY_FLAGGED', 'ISSUE_RAISED'];

export function toastFor(e: VsEvent): { kind: ToastKind; title: string; body?: string } | null {
  switch (e.type) {
    case 'VALIDATION_COMPLETED':
      return { kind: 'success', title: 'Validation completed', body: e.message };
    case 'INTAKE_THRESHOLD':
      return {
        kind: e.severity === 'CRITICAL' || e.severity === 'ERROR' ? 'error' : e.severity === 'WARN' ? 'warn' : 'info',
        title: 'Intake threshold',
        body: e.message,
      };
    case 'ANOMALY_FLAGGED':
      return { kind: e.severity === 'CRITICAL' ? 'critical' : 'warn', title: 'Anomaly flagged', body: e.message };
    case 'ISSUE_RAISED':
      return e.severity === 'CRITICAL' ? { kind: 'critical', title: 'CRITICAL issue raised', body: e.message } : null;
    default:
      return null;
  }
}

/** TanStack query-key prefixes to invalidate for an event type. */
export function invalidationKeys(type: string): string[] {
  switch (type) {
    case 'UPLOAD_RECEIVED':
      return ['uploads'];
    case 'VALIDATION_COMPLETED':
      return ['uploads', 'upload', 'meters', 'quality', 'issues', 'national', 'institutions', 'enrolments', 'worklist', 'excess'];
    case 'ISSUE_RAISED':
      return ['issues', 'national', 'institutions', 'duplicates'];
    case 'INTAKE_THRESHOLD':
      return ['meters', 'national', 'excess'];
    case 'ANOMALY_FLAGGED':
      return ['anomalies', 'national', 'institutions', 'risk'];
    case 'RECORD_VERIFIED':
      return ['access-history', 'records'];
    default:
      return [];
  }
}

/** Merge new events into a list: dedupe by id, newest first, capped. */
export function mergeEvents(existing: VsEvent[], incoming: VsEvent[], cap = 100): VsEvent[] {
  const seen = new Set(existing.map((e) => e.id));
  const fresh = incoming.filter((e) => e && e.type !== 'HEARTBEAT' && !seen.has(e.id));
  if (fresh.length === 0) return existing;
  const all = [...fresh, ...existing];
  all.sort((a, b) => new Date(b.ts).getTime() - new Date(a.ts).getTime());
  return all.slice(0, cap);
}
