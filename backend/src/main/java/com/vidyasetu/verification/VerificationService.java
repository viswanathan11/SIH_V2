package com.vidyasetu.verification;

import com.nimbusds.jwt.JWTClaimsSet;
import com.vidyasetu.audit.AuditService;
import com.vidyasetu.common.ApiException;
import com.vidyasetu.common.Masking;
import com.vidyasetu.security.CryptoService;
import com.vidyasetu.security.JwtService;
import com.vidyasetu.security.TenantScope;
import com.vidyasetu.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class VerificationService {
    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    private final JdbcTemplate jdbc;
    private final JwtService jwtService;
    private final CryptoService cryptoService;
    private final AuditService auditService;

    public VerificationService(JdbcTemplate jdbc, JwtService jwtService,
                               CryptoService cryptoService, AuditService auditService) {
        this.jdbc = jdbc;
        this.jwtService = jwtService;
        this.cryptoService = cryptoService;
        this.auditService = auditService;
    }

    public record IssuedRecordDto(
            UUID id,
            String jti,
            UUID studentId,
            UUID enrolmentId,
            String recordType,
            String jws,
            String verifyUrl,
            String issuedAt,
            String expiresAt,
            String revokedAt,
            String revokeReason
    ) {}

    public record VerificationLookupResult(
            String result, // FOUND, NOT_FOUND, DOB_MISMATCH
            String tierRequested,
            String tierGranted,
            boolean consentRequired,
            Map<String, Object> consentRequest,
            Map<String, Object> data,
            List<IssuedRecordDto> records
    ) {}

    @Transactional
    public IssuedRecordDto issueRecord(UUID enrolmentId) {
        Map<String, Object> row = jdbc.queryForMap("""
            SELECT e.id as enrolment_id, e.institution_id, e.status as enrol_status, e.academic_year,
                   s.id as student_id, s.apaar_id, s.full_name, s.dob, s.gender,
                   i.aicte_id, i.name as inst_name,
                   p.program_code, p.name as prog_name, e.institute_student_id
            FROM enrolments e
            JOIN students s ON s.id = e.student_id
            JOIN institutions i ON i.id = e.institution_id
            JOIN programs p ON p.id = e.program_id
            WHERE e.id = ?
        """, enrolmentId);

        UUID instId = (UUID) row.get("institution_id");
        TenantScope.assertCanAccessInstitution(instId);

        UUID studentId = (UUID) row.get("student_id");
        String apaar = (String) row.get("apaar_id");
        String apaarMasked = Masking.maskApaar(apaar);
        String subHmac = cryptoService.hmacSha256Hex("vidyasetu-record-sub", apaar);

        String jti = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        Instant exp = now.plus(365 * 3, ChronoUnit.DAYS); // 3-year validity

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("apaar_masked", apaarMasked);
        claims.put("student_id", studentId.toString());
        claims.put("institution_aicte_id", row.get("aicte_id"));
        claims.put("institution_name", row.get("inst_name"));
        claims.put("program_code", row.get("program_code"));
        claims.put("program_name", row.get("prog_name"));
        claims.put("academic_year", row.get("academic_year"));
        claims.put("status", row.get("enrol_status"));
        claims.put("institute_student_id", row.get("institute_student_id"));

        String jws = jwtService.issueVerifiableRecord(jti, subHmac, claims, exp);

        UUID recId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO issued_records (id, student_id, enrolment_id, record_type, jti, jws, issued_at, expires_at)
            VALUES (?, ?, ?, 'STUDENT_ENROLMENT_RECORD', ?, ?, now(), ?)
        """, recId, studentId, enrolmentId, jti, jws, java.sql.Timestamp.from(exp));

        auditService.record("RECORD_ISSUED", "ISSUED_RECORD", recId.toString(), instId,
                Map.of("jti", jti, "enrolmentId", enrolmentId.toString()));

        return new IssuedRecordDto(
                recId, jti, studentId, enrolmentId, "STUDENT_ENROLMENT_RECORD",
                jws, "/v/" + jti, now.toString(), exp.toString(), null, null
        );
    }

    @Transactional
    public IssuedRecordDto revokeRecord(String jti, String reason) {
        Map<String, Object> rec = jdbc.queryForMap("""
            SELECT r.id, r.student_id, r.enrolment_id, r.record_type, r.jws, r.issued_at, r.expires_at,
                   e.institution_id
            FROM issued_records r
            LEFT JOIN enrolments e ON e.id = r.enrolment_id
            WHERE r.jti = ?
        """, jti);

        UUID instId = (UUID) rec.get("institution_id");
        TenantScope.assertCanAccessInstitution(instId);

        Instant now = Instant.now();
        jdbc.update("UPDATE issued_records SET revoked_at = now(), revoke_reason = ? WHERE jti = ?", reason, jti);

        auditService.record("RECORD_REVOKED", "ISSUED_RECORD", rec.get("id").toString(), instId,
                Map.of("jti", jti, "reason", reason != null ? reason : ""));

        return new IssuedRecordDto(
                (UUID) rec.get("id"), jti, (UUID) rec.get("student_id"), (UUID) rec.get("enrolment_id"),
                (String) rec.get("record_type"), (String) rec.get("jws"), "/v/" + jti,
                rec.get("issued_at").toString(), rec.get("expires_at").toString(), now.toString(), reason
        );
    }

    public List<IssuedRecordDto> getRecordsByEnrolment(UUID enrolmentId) {
        return jdbc.query("""
            SELECT id, jti, student_id, enrolment_id, record_type, jws, issued_at, expires_at, revoked_at, revoke_reason
            FROM issued_records WHERE enrolment_id = ? ORDER BY issued_at DESC
        """, (rs, rowNum) -> new IssuedRecordDto(
                (UUID) rs.getObject("id"),
                rs.getString("jti"),
                (UUID) rs.getObject("student_id"),
                (UUID) rs.getObject("enrolment_id"),
                rs.getString("record_type"),
                rs.getString("jws"),
                "/v/" + rs.getString("jti"),
                rs.getTimestamp("issued_at").toInstant().toString(),
                rs.getTimestamp("expires_at") != null ? rs.getTimestamp("expires_at").toInstant().toString() : null,
                rs.getTimestamp("revoked_at") != null ? rs.getTimestamp("revoked_at").toInstant().toString() : null,
                rs.getString("revoke_reason")
        ), enrolmentId);
    }

    /** PUBLIC tier verification by JTI (no login required) */
    public Map<String, Object> verifyPublicJti(String jti) {
        List<Map<String, Object>> list = jdbc.query("""
            SELECT r.id, r.jti, r.jws, r.issued_at, r.expires_at, r.revoked_at, r.revoke_reason,
                   s.apaar_id, i.aicte_id, p.program_code, e.academic_year, e.status
            FROM issued_records r
            JOIN students s ON s.id = r.student_id
            LEFT JOIN enrolments e ON e.id = r.enrolment_id
            LEFT JOIN institutions i ON i.id = e.institution_id
            LEFT JOIN programs p ON p.id = e.program_id
            WHERE r.jti = ?
        """, (rs, rowNum) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", rs.getObject("id"));
            map.put("jti", rs.getString("jti"));
            map.put("jws", rs.getString("jws"));
            map.put("issuedAt", rs.getTimestamp("issued_at").toInstant().toString());
            map.put("expiresAt", rs.getTimestamp("expires_at") != null ? rs.getTimestamp("expires_at").toInstant().toString() : null);
            map.put("revokedAt", rs.getTimestamp("revoked_at") != null ? rs.getTimestamp("revoked_at").toInstant().toString() : null);
            map.put("revokeReason", rs.getString("revoke_reason"));
            map.put("apaarMasked", Masking.maskApaar(rs.getString("apaar_id")));
            map.put("aicteId", rs.getString("aicte_id"));
            map.put("programCode", rs.getString("program_code"));
            map.put("academicYear", rs.getString("academic_year"));
            map.put("status", rs.getString("status"));
            return map;
        }, jti);

        if (list.isEmpty()) {
            return Map.of("status", "NOT_FOUND", "signatureValid", false, "message", "Record not found");
        }

        Map<String, Object> row = list.get(0);
        String jws = (String) row.get("jws");
        JWTClaimsSet claims = jwtService.verifyVerifiableRecord(jws);
        boolean sigValid = claims != null;

        String status = "VALID";
        if (row.get("revokedAt") != null) {
            status = "REVOKED";
        } else if (!sigValid) {
            status = "INVALID_SIGNATURE";
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("jti", jti);
        res.put("status", status);
        res.put("signatureValid", sigValid);
        res.put("issuer", "vidyasetu-record-authority");
        res.put("issuedAt", row.get("issuedAt"));
        res.put("expiresAt", row.get("expiresAt"));
        res.put("apaarMasked", row.get("apaarMasked"));
        res.put("revokedAt", row.get("revokedAt"));
        res.put("revokeReason", row.get("revokeReason"));

        // If caller is authenticated at L1+, disclose program & institution
        UserPrincipal user = TenantScope.currentUser();
        if (user != null && (user.hasRole("EMPLOYER_VERIFIER") || user.hasRole("REGULATOR") || user.isNationalAdmin() || user.isInstitutionUser())) {
            res.put("institutionAicteId", row.get("aicteId"));
            res.put("programCode", row.get("programCode"));
            res.put("academicYear", row.get("academicYear"));
            res.put("enrolmentStatus", row.get("status"));
        }

        return res;
    }

    /**
     * Tiered verification by APAAR + DOB challenge
     */
    @Transactional
    public VerificationLookupResult lookupVerify(String rawApaar, LocalDate dob, String requestedTier, String purpose) {
        UserPrincipal user = TenantScope.requireUser();
        String apaar = Masking.normalizeApaar(rawApaar);
        String reqTier = (requestedTier != null && !requestedTier.isBlank()) ? requestedTier.toUpperCase() : "L1";

        List<Map<String, Object>> students = jdbc.query("""
            SELECT s.id as student_id, s.apaar_id, s.full_name, s.dob, s.gender,
                   e.id as enrolment_id, e.institute_student_id, e.academic_year, e.status as enrol_status,
                   i.aicte_id, i.name as inst_name, p.program_code, p.name as prog_name
            FROM students s
            LEFT JOIN enrolments e ON e.student_id = s.id AND e.academic_year = '2026-27'
            LEFT JOIN institutions i ON i.id = e.institution_id
            LEFT JOIN programs p ON p.id = e.program_id
            WHERE s.apaar_id = ?
        """, (rs, rowNum) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("studentId", rs.getObject("student_id"));
            map.put("apaarId", rs.getString("apaar_id"));
            map.put("fullName", rs.getString("full_name"));
            map.put("dob", rs.getDate("dob") != null ? rs.getDate("dob").toLocalDate() : null);
            map.put("gender", rs.getString("gender"));
            map.put("enrolmentId", rs.getObject("enrolment_id"));
            map.put("instituteStudentId", rs.getString("institute_student_id"));
            map.put("academicYear", rs.getString("academic_year"));
            map.put("enrolStatus", rs.getString("enrol_status"));
            map.put("aicteId", rs.getString("aicte_id"));
            map.put("instName", rs.getString("inst_name"));
            map.put("progCode", rs.getString("program_code"));
            map.put("progName", rs.getString("prog_name"));
            return map;
        }, apaar);

        if (students.isEmpty()) {
            logVerificationRequest(user, apaar, null, purpose, reqTier, "NOT_FOUND");
            return new VerificationLookupResult("NOT_FOUND", reqTier, "NONE", false, null, null, Collections.emptyList());
        }

        Map<String, Object> st = students.get(0);
        LocalDate actualDob = (LocalDate) st.get("dob");

        if (dob != null && actualDob != null && !dob.equals(actualDob)) {
            logVerificationRequest(user, apaar, null, purpose, reqTier, "DOB_MISMATCH");
            return new VerificationLookupResult("DOB_MISMATCH", reqTier, "NONE", false, null, null, Collections.emptyList());
        }

        UUID studentId = (UUID) st.get("studentId");
        String grantedTier = "L1";
        boolean consentRequired = false;
        Map<String, Object> consentReqMap = null;

        // Check if higher tier requested (L2 or L3)
        if ("L2".equals(reqTier) || "L3".equals(reqTier)) {
            if (user.isNationalAdmin() || user.hasRole("REGULATOR")) {
                grantedTier = reqTier; // Regulators and AICTE have L2 clearance
            } else {
                // Check active consent
                String org = user.getOrg() != null ? user.getOrg() : user.getEmail();
                List<UUID> activeConsents = jdbc.query("""
                    SELECT id FROM consents
                    WHERE student_id = ? AND grantee_id = ? AND scope IN (?, 'L3')
                      AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > now())
                """, (rs, rowNum) -> (UUID) rs.getObject("id"), studentId, org, reqTier);

                if (!activeConsents.isEmpty()) {
                    grantedTier = reqTier;
                } else {
                    consentRequired = true;
                    // Create or find pending consent request
                    List<Map<String, Object>> pending = jdbc.query("""
                        SELECT id, status FROM consent_requests
                        WHERE student_id = ? AND requester_user_id = ? AND status = 'PENDING'
                    """, (rs, rowNum) -> Map.of("id", rs.getObject("id"), "status", rs.getString("status")), studentId, user.getId());

                    UUID crId;
                    if (pending.isEmpty()) {
                        crId = UUID.randomUUID();
                        jdbc.update("""
                            INSERT INTO consent_requests (id, student_id, requester_user_id, requester_org, requested_scope, purpose, status, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, 'PENDING', now())
                        """, crId, studentId, user.getId(), org, reqTier, purpose);
                    } else {
                        crId = (UUID) pending.get(0).get("id");
                    }
                    consentReqMap = Map.of("id", crId.toString(), "status", "PENDING", "requestedScope", reqTier);
                }
            }
        }

        // Build scoped response data based on grantedTier
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apaarMasked", Masking.maskApaar(apaar));
        data.put("institutionAicteId", st.get("aicteId"));
        data.put("institutionName", st.get("instName"));
        data.put("programCode", st.get("progCode"));
        data.put("programName", st.get("progName"));
        data.put("academicYear", st.get("academicYear"));
        data.put("status", st.get("enrolStatus"));

        if ("L2".equals(grantedTier) || "L3".equals(grantedTier)) {
            data.put("fullName", st.get("fullName"));
            data.put("instituteStudentId", st.get("instituteStudentId"));
        }
        if ("L3".equals(grantedTier)) {
            data.put("dob", actualDob != null ? actualDob.toString() : null);
            data.put("gender", st.get("gender"));
        }

        List<IssuedRecordDto> recs = Collections.emptyList();
        if (st.get("enrolmentId") != null) {
            recs = getRecordsByEnrolment((UUID) st.get("enrolmentId"));
        }

        logVerificationRequest(user, apaar, recs.isEmpty() ? null : recs.get(0).jti(), purpose, grantedTier, "SUCCESS");

        return new VerificationLookupResult("FOUND", reqTier, grantedTier, consentRequired, consentReqMap, data, recs);
    }

    private void logVerificationRequest(UserPrincipal user, String apaar, String jti, String purpose, String scope, String result) {
        jdbc.update("""
            INSERT INTO verification_requests (id, requester_user_id, requester_name, requester_org,
                                              apaar_masked, record_jti, purpose, scope_granted, result, ip, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '127.0.0.1', now())
        """, UUID.randomUUID(), user.getId(), user.getFullName(), user.getOrg(),
                Masking.maskApaar(apaar), jti, purpose, scope, result);

        auditService.record("VERIFICATION_REQUESTED", "VERIFICATION", jti != null ? jti : Masking.maskApaar(apaar),
                user.getInstitutionId(), Map.of("apaarMasked", Masking.maskApaar(apaar), "result", result, "scope", scope));
    }
}
