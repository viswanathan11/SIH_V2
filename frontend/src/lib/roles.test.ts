import { describe, it, expect } from 'vitest';
import { homeFor, navFor, canAccess } from './roles';

describe('Roles and Navigation', () => {
  it('redirects AICTE roles to /aicte', () => {
    expect(homeFor({ roles: ['AICTE_ADMIN'] })).toBe('/aicte');
    expect(homeFor({ roles: ['SUPER_ADMIN'] })).toBe('/aicte');
    expect(homeFor({ roles: ['REGULATOR'] })).toBe('/aicte');
  });

  it('redirects Institution roles to /inst', () => {
    expect(homeFor({ roles: ['INSTITUTION_ADMIN'] })).toBe('/inst');
    expect(homeFor({ roles: ['INSTITUTION_OPERATOR'] })).toBe('/inst');
  });

  it('redirects Employer and Student roles correctly', () => {
    expect(homeFor({ roles: ['EMPLOYER_VERIFIER'] })).toBe('/verify');
    expect(homeFor({ roles: ['STUDENT'] })).toBe('/me');
  });

  it('filters navigation items strictly by role', () => {
    const studentNav = navFor({ roles: ['STUDENT'] });
    expect(studentNav.map(n => n.path)).toEqual(['/me']);

    const instNav = navFor({ roles: ['INSTITUTION_ADMIN'] });
    expect(instNav.some(n => n.path.startsWith('/aicte'))).toBe(false);
    expect(instNav.some(n => n.path === '/inst')).toBe(true);
    expect(instNav.some(n => n.path === '/inst/upload')).toBe(true);
  });

  it('enforces route access authorization', () => {
    expect(canAccess({ roles: ['STUDENT'] }, '/aicte')).toBe(false);
    expect(canAccess({ roles: ['STUDENT'] }, '/me')).toBe(true);
    expect(canAccess({ roles: ['INSTITUTION_ADMIN'] }, '/inst/upload')).toBe(true);
    expect(canAccess({ roles: ['INSTITUTION_ADMIN'] }, '/aicte/admin')).toBe(false);
  });
});
