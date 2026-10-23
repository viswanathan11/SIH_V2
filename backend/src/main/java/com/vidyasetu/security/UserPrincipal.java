package com.vidyasetu.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;

public class UserPrincipal implements UserDetails {
    private final UUID id;
    private final String email;
    private final String password;
    private final String fullName;
    private final Set<String> roles;
    private final Set<String> permissions;
    private final UUID institutionId;
    private final String institutionCode;
    private final String institutionName;
    private final UUID studentId;
    private final String apaarMasked;
    private final String org;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(UUID id, String email, String password, String fullName,
                         Set<String> roles, Set<String> permissions,
                         UUID institutionId, String institutionCode, String institutionName,
                         UUID studentId, String apaarMasked, String org, boolean active) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.roles = roles != null ? roles : Collections.emptySet();
        this.permissions = permissions != null ? permissions : Collections.emptySet();
        this.institutionId = institutionId;
        this.institutionCode = institutionCode;
        this.institutionName = institutionName;
        this.studentId = studentId;
        this.apaarMasked = apaarMasked;
        this.org = org;
        this.active = active;

        Set<GrantedAuthority> auths = new HashSet<>();
        for (String r : this.roles) {
            auths.add(new SimpleGrantedAuthority("ROLE_" + r));
        }
        for (String p : this.permissions) {
            auths.add(new SimpleGrantedAuthority(p));
        }
        this.authorities = Collections.unmodifiableSet(auths);
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public Set<String> getRoles() { return roles; }
    public Set<String> getPermissions() { return permissions; }
    public UUID getInstitutionId() { return institutionId; }
    public String getInstitutionCode() { return institutionCode; }
    public String getInstitutionName() { return institutionName; }
    public UUID getStudentId() { return studentId; }
    public String getApaarMasked() { return apaarMasked; }
    public String getOrg() { return org; }

    public boolean hasRole(String role) { return roles.contains(role); }
    public boolean hasPermission(String permission) { return permissions.contains(permission); }

    public boolean isNationalAdmin() {
        return roles.contains("SUPER_ADMIN") || roles.contains("AICTE_ADMIN") ||
                roles.contains("AICTE_ANALYST") || roles.contains("AICTE_AUDITOR") || roles.contains("REGULATOR");
    }

    public boolean isInstitutionUser() {
        return roles.contains("INSTITUTION_ADMIN") || roles.contains("INSTITUTION_OPERATOR") || roles.contains("API_CLIENT");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override
    public String getPassword() { return password; }
    @Override
    public String getUsername() { return email; }
    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return true; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return active; }
}
