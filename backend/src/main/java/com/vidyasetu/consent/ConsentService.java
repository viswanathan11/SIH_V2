package com.vidyasetu.consent;

import com.vidyasetu.audit.AuditService;
import com.vidyasetu.common.ApiException;
import com.vidyasetu.common.Masking;
import com.vidyasetu.security.TenantScope;
import com.vidyasetu.security.UserPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ConsentService {
    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    public ConsentService(JdbcTemplate jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public record ConsentDto(
            UUID id,
            String granteeType,
            String granteeId,
            String granteeName,
            String scope,
            String purpose,
            String grantedAt,
            String expiresAt,
            String revokedAt,
            boolean active
    ) {}

    public record ConsentRequestDto(
            UUID id,
            String studentApaarMasked,
            String requesterName,
            String requesterOrg,
            String requestedScope,
            String purpose,
            String status,
            String createdAt
    ) {}

    public List<ConsentDto> listConsents() {
        UserPrincipal user = TenantScope.requireUser();
        UUID studentId = user.getStudentId();
        if (studentId == null) {
            return Collections.emptyList();
        }

        return jdbc.query("""
            SELECT id, grantee_type, grantee_id, grantee_name, scope, purpose,
                   granted_at, expires_at, revoked_at
            FROM consents WHERE student_id = ? ORDER BY granted_at DESC
        """, (rs, rowNum) -> {
            var exp = rs.getTimestamp("expires_at");
            var rev = rs.getTimestamp("revoked_at");
            boolean active = rev == null && (exp == null || exp.toInstant().isAfter(Instant.now()));
            return new ConsentDto(
                    (UUID) rs.getObject("id"),
                    rs.getString("grantee_type"),
                    rs.getString("grantee_id"),
                    rs.getString("grantee_name"),
                    rs.getString("scope"),
                    rs.getString("purpose"),
                    rs.getTimestamp("granted_at").toInstant().toString(),
                    exp != null ? exp.toInstant().toString() : null,
                    rev != null ? rev.toInstant().toString() : null,
                    active
            );
        }, studentId);
    }

    @Transactional
    public ConsentDto grantConsent(String granteeOrg, String scope, String purpose, int days) {
        UserPrincipal user = TenantScope.requireUser();
        UUID studentId = user.getStudentId();
        if (studentId == null) throw ApiException.badRequest("Only students can grant consent");

        UUID id = UUID.randomUUID();
        Instant exp = Instant.now().plus(days > 0 ? days : 30, ChronoUnit.DAYS);

        jdbc.update("""
            INSERT INTO consents (id, student_id, grantee_type, grantee_id, grantee_name, scope, purpose, granted_at, expires_at)
            VALUES (?, ?, 'ORGANIZATION', ?, ?, ?, ?, now(), ?)
        """, id, studentId, granteeOrg, granteeOrg, scope != null ? scope : "L2", purpose, java.sql.Timestamp.from(exp));

        auditService.record("CONSENT_GRANTED", "CONSENT", id.toString(), null,
                Map.of("grantee", granteeOrg, "scope", scope != null ? scope : "L2"));

        return new ConsentDto(id, "ORGANIZATION", granteeOrg, granteeOrg, scope != null ? scope : "L2", purpose,
                Instant.now().toString(), exp.toString(), null, true);
    }

    @Transactional
    public void revokeConsent(UUID consentId) {
        UserPrincipal user = TenantScope.requireUser();
        jdbc.update("UPDATE consents SET revoked_at = now() WHERE id = ? AND student_id = ?", consentId, user.getStudentId());
        auditService.record("CONSENT_REVOKED", "CONSENT", consentId.toString(), null, Map.of("consentId", consentId.toString()));
    }

    public List<ConsentRequestDto> listConsentRequests() {
        UserPrincipal user = TenantScope.requireUser();
        if (user.getStudentId() != null) {
            // Student sees requests for themselves
            return jdbc.query("""
                SELECT cr.id, s.apaar_id, u.full_name, cr.requester_org, cr.requested_scope, cr.purpose, cr.status, cr.created_at
                FROM consent_requests cr
                JOIN students s ON s.id = cr.student_id
                LEFT JOIN users u ON u.id = cr.requester_user_id
                WHERE cr.student_id = ? ORDER BY cr.created_at DESC
            """, (rs, rowNum) -> new ConsentRequestDto(
                    (UUID) rs.getObject("id"),
                    Masking.maskApaar(rs.getString("apaar_id")),
                    rs.getString("full_name"),
                    rs.getString("requester_org"),
                    rs.getString("requested_scope"),
                    rs.getString("purpose"),
                    rs.getString("status"),
                    rs.getTimestamp("created_at").toInstant().toString()
            ), user.getStudentId());
        } else {
            // Verifier sees requests made by themselves
            return jdbc.query("""
                SELECT cr.id, s.apaar_id, u.full_name, cr.requester_org, cr.requested_scope, cr.purpose, cr.status, cr.created_at
                FROM consent_requests cr
                JOIN students s ON s.id = cr.student_id
                LEFT JOIN users u ON u.id = cr.requester_user_id
                WHERE cr.requester_user_id = ? ORDER BY cr.created_at DESC
            """, (rs, rowNum) -> new ConsentRequestDto(
                    (UUID) rs.getObject("id"),
                    Masking.maskApaar(rs.getString("apaar_id")),
                    rs.getString("full_name"),
                    rs.getString("requester_org"),
                    rs.getString("requested_scope"),
                    rs.getString("purpose"),
                    rs.getString("status"),
                    rs.getTimestamp("created_at").toInstant().toString()
            ), user.getId());
        }
    }

    @Transactional
    public void decideConsentRequest(UUID requestId, boolean approve, int days) {
        UserPrincipal user = TenantScope.requireUser();
        Map<String, Object> req = jdbc.queryForMap("SELECT * FROM consent_requests WHERE id = ? AND student_id = ?",
                requestId, user.getStudentId());

        String status = approve ? "APPROVED" : "DENIED";
        jdbc.update("UPDATE consent_requests SET status = ?, decided_at = now() WHERE id = ?", status, requestId);

        if (approve) {
            grantConsent((String) req.get("requester_org"), (String) req.get("requested_scope"), (String) req.get("purpose"), days > 0 ? days : 30);
        }

        auditService.record("CONSENT_REQUEST_DECIDED", "CONSENT_REQUEST", requestId.toString(), null,
                Map.of("decision", status));
    }

    public List<Map<String, Object>> getStudentAccessHistory() {
        UserPrincipal user = TenantScope.requireUser();
        if (user.getStudentId() == null) return Collections.emptyList();

        return jdbc.query("""
            SELECT id, requester_name, requester_org, purpose, scope_granted, result, created_at
            FROM verification_requests
            WHERE student_id = ? OR apaar_masked = ?
            ORDER BY created_at DESC LIMIT 50
        """, (rs, rowNum) -> Map.of(
                "ts", rs.getTimestamp("created_at").toInstant().toString(),
                "requesterName", rs.getString("requester_name") != null ? rs.getString("requester_name") : "External Verifier",
                "requesterOrg", rs.getString("requester_org") != null ? rs.getString("requester_org") : "Organization",
                "purpose", rs.getString("purpose") != null ? rs.getString("purpose") : "Employment/Academic Verification",
                "scopeGranted", rs.getString("scope_granted"),
                "result", rs.getString("result")
        ), user.getStudentId(), user.getApaarMasked());
    }
}
