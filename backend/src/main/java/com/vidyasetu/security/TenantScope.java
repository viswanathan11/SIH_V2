package com.vidyasetu.security;

import com.vidyasetu.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class TenantScope {
    private TenantScope() {}

    public static UserPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal up) {
            return up;
        }
        return null;
    }

    public static UserPrincipal requireUser() {
        UserPrincipal up = currentUser();
        if (up == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        return up;
    }

    /**
     * Enforces tenant isolation:
     * - National roles (AICTE_*, SUPER_ADMIN, REGULATOR) can view any institution
     * - Institution roles (INSTITUTION_ADMIN, INSTITUTION_OPERATOR, API_CLIENT) can ONLY access their own institution
     */
    public static void assertCanAccessInstitution(UUID institutionId) {
        if (institutionId == null) return;
        UserPrincipal user = requireUser();
        if (user.isNationalAdmin()) {
            return;
        }
        if (user.getInstitutionId() != null && user.getInstitutionId().equals(institutionId)) {
            return;
        }
        throw ApiException.forbidden("You do not have access to institution data for " + institutionId);
    }

    /** Returns effective institution filter for lists */
    public static UUID resolveInstitutionFilter(UUID requested) {
        UserPrincipal user = currentUser();
        if (user == null) return requested;
        if (!user.isNationalAdmin() && user.getInstitutionId() != null) {
            return user.getInstitutionId();
        }
        return requested;
    }
}
