// Role → route mapping and nav filtering (pure, unit tested).

export interface RoleSubject {
  roles: string[];
  permissions?: string[];
}

export const AICTE_ROLES = ['SUPER_ADMIN', 'AICTE_ADMIN', 'AICTE_ANALYST', 'AICTE_AUDITOR'];
export const NATIONAL_ROLES = [...AICTE_ROLES, 'REGULATOR'];
export const INST_ROLES = ['INSTITUTION_ADMIN', 'INSTITUTION_OPERATOR'];

export interface NavItem {
  path: string;
  /** i18n key */
  label: string;
  icon: string;
  roles: string[];
  /** Any of these permissions also grants access (optional). */
  permissions?: string[];
  section: 'national' | 'institution' | 'verify' | 'student';
  /** exact match for active highlighting */
  end?: boolean;
}

export const NAV_ITEMS: NavItem[] = [
  { path: '/aicte', label: 'nav.national', icon: 'dashboard', roles: NATIONAL_ROLES, section: 'national', end: true },
  { path: '/aicte/institutions', label: 'nav.institutions', icon: 'building', roles: NATIONAL_ROLES, section: 'national' },
  { path: '/aicte/anomalies', label: 'nav.anomalies', icon: 'radar', roles: NATIONAL_ROLES, section: 'national' },
  { path: '/aicte/issues', label: 'nav.issues', icon: 'alert', roles: AICTE_ROLES, section: 'national' },
  { path: '/aicte/reports', label: 'nav.reports', icon: 'report', roles: NATIONAL_ROLES, section: 'national' },
  { path: '/aicte/audit', label: 'nav.audit', icon: 'shield', roles: ['SUPER_ADMIN', 'AICTE_ADMIN', 'AICTE_AUDITOR'], section: 'national' },
  { path: '/aicte/admin', label: 'nav.admin', icon: 'settings', roles: ['SUPER_ADMIN', 'AICTE_ADMIN'], section: 'national' },
  { path: '/inst', label: 'nav.instDashboard', icon: 'dashboard', roles: INST_ROLES, section: 'institution', end: true },
  { path: '/inst/upload', label: 'nav.upload', icon: 'upload', roles: INST_ROLES, section: 'institution' },
  { path: '/inst/worklist', label: 'nav.worklist', icon: 'wrench', roles: INST_ROLES, section: 'institution' },
  { path: '/inst/students', label: 'nav.students', icon: 'users', roles: INST_ROLES, section: 'institution' },
  { path: '/inst/integration', label: 'nav.integration', icon: 'plug', roles: ['INSTITUTION_ADMIN'], section: 'institution' },
  { path: '/verify', label: 'nav.verify', icon: 'badge', roles: ['EMPLOYER_VERIFIER', 'REGULATOR', 'AICTE_ADMIN', 'SUPER_ADMIN', 'INSTITUTION_ADMIN'], section: 'verify' },
  { path: '/me', label: 'nav.me', icon: 'user', roles: ['STUDENT'], section: 'student' },
];

export function hasRole(u: RoleSubject | null | undefined, ...roles: string[]): boolean {
  if (!u) return false;
  return u.roles.some((r) => roles.includes(r));
}

export function hasPerm(u: RoleSubject | null | undefined, perm: string): boolean {
  return !!u?.permissions?.includes(perm);
}

export function navFor(u: RoleSubject | null | undefined): NavItem[] {
  if (!u) return [];
  return NAV_ITEMS.filter(
    (n) => hasRole(u, ...n.roles) || (n.permissions ?? []).some((p) => hasPerm(u, p)),
  );
}

/** Landing route after login. */
export function homeFor(u: RoleSubject | null | undefined): string {
  if (!u) return '/login';
  if (hasRole(u, ...NATIONAL_ROLES)) return '/aicte';
  if (hasRole(u, ...INST_ROLES)) return '/inst';
  if (hasRole(u, 'EMPLOYER_VERIFIER')) return '/verify';
  if (hasRole(u, 'STUDENT')) return '/me';
  return '/login';
}

/** Find the most specific nav item covering a path, then check roles. */
export function canAccess(u: RoleSubject | null | undefined, path: string): boolean {
  if (!u) return false;
  const candidates = NAV_ITEMS.filter((n) => path === n.path || path.startsWith(n.path + '/'));
  if (candidates.length === 0) return true;
  const best = candidates.sort((a, b) => b.path.length - a.path.length)[0];
  return navFor(u).some((n) => n.path === best.path);
}

export function canSearch(u: RoleSubject | null | undefined): boolean {
  return hasRole(u, ...NATIONAL_ROLES, ...INST_ROLES);
}

/** Roles allowed to act (vs read-only) on anomalies. */
export function canReviewAnomaly(u: RoleSubject | null | undefined): boolean {
  return hasPerm(u, 'anomaly:review') || hasRole(u, 'SUPER_ADMIN', 'AICTE_ADMIN', 'AICTE_ANALYST');
}

export function canWaive(u: RoleSubject | null | undefined): boolean {
  return hasPerm(u, 'validation:waive') || hasRole(u, 'SUPER_ADMIN', 'AICTE_ADMIN');
}

export function canResolve(u: RoleSubject | null | undefined): boolean {
  return hasPerm(u, 'validation:resolve') || hasRole(u, 'SUPER_ADMIN', 'AICTE_ADMIN', ...INST_ROLES);
}

export function primaryRole(u: RoleSubject | null | undefined): string {
  return u?.roles?.[0] ?? '';
}
