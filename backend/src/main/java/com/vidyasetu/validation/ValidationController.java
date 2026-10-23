package com.vidyasetu.validation;

import com.vidyasetu.audit.AuditService;
import com.vidyasetu.common.ApiException;
import com.vidyasetu.common.Json;
import com.vidyasetu.common.PageResponse;
import com.vidyasetu.ingestion.IngestionService;
import com.vidyasetu.security.TenantScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class ValidationController {
    private final JdbcTemplate jdbc;
    private final AuditService auditService;
    private final ValidationEngine validationEngine;
    private final IngestionService ingestionService;

    public ValidationController(JdbcTemplate jdbc, AuditService auditService,
                                ValidationEngine validationEngine, IngestionService ingestionService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.validationEngine = validationEngine;
        this.ingestionService = ingestionService;
    }

    @GetMapping("/issues")
    @PreAuthorize("hasAuthority('validation:read')")
    public PageResponse<ValidationIssue> listIssues(
            @RequestParam(value = "institutionId", required = false) UUID institutionId,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "ruleCode", required = false) String ruleCode,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "batchId", required = false) UUID batchId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        UUID inst = TenantScope.resolveInstitutionFilter(institutionId);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT iss.id, iss.batch_id, iss.staging_row_id, iss.enrolment_id, iss.institution_id,
                   i.aicte_id as inst_code, iss.rule_code, iss.severity, iss.field, iss.message,
                   iss.hint, iss.context, iss.apaar_masked, iss.row_no, iss.status, iss.resolved_by, iss.resolution_note
            FROM validation_issues iss
            JOIN institutions i ON i.id = iss.institution_id
            WHERE 1=1
        """);

        if (inst != null) {
            sql.append(" AND iss.institution_id = ? ");
            params.add(inst);
        }
        if (severity != null && !severity.isBlank()) {
            sql.append(" AND iss.severity = ? ");
            params.add(severity.trim());
        }
        if (ruleCode != null && !ruleCode.isBlank()) {
            sql.append(" AND iss.rule_code = ? ");
            params.add(ruleCode.trim());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND iss.status = ? ");
            params.add(status.trim());
        }
        if (batchId != null) {
            sql.append(" AND iss.batch_id = ? ");
            params.add(batchId);
        }

        Long total = jdbc.queryForObject("SELECT count(*) FROM (" + sql + ") t", Long.class, params.toArray());

        sql.append(" ORDER BY iss.created_at DESC LIMIT ? OFFSET ? ");
        params.add(size);
        params.add(page * size);

        List<ValidationIssue> list = jdbc.query(sql.toString(), (rs, rowNum) -> new ValidationIssue(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("batch_id"),
                (UUID) rs.getObject("staging_row_id"),
                (UUID) rs.getObject("enrolment_id"),
                (UUID) rs.getObject("institution_id"),
                rs.getString("inst_code"),
                rs.getString("rule_code"),
                rs.getString("severity"),
                rs.getString("field"),
                rs.getString("message"),
                rs.getString("hint"),
                Json.readMap(rs.getString("context")),
                rs.getString("apaar_masked"),
                rs.getInt("row_no"),
                rs.getString("status"),
                rs.getString("resolved_by"),
                rs.getString("resolution_note")
        ), params.toArray());

        return PageResponse.of(list, page, size, total != null ? total : 0);
    }

    @PostMapping("/issues/{id}/acknowledge")
    @PreAuthorize("hasAuthority('validation:resolve')")
    public ValidationIssue acknowledgeIssue(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body) {
        String actor = TenantScope.requireUser().getEmail();
        String note = body != null ? body.get("note") : "Acknowledged";
        jdbc.update("UPDATE validation_issues SET status = 'ACKNOWLEDGED', resolved_by = ?, resolution_note = ? WHERE id = ?",
                actor, note, id);
        return getIssue(id);
    }

    @PostMapping("/issues/{id}/resolve")
    @PreAuthorize("hasAuthority('validation:resolve')")
    public ValidationIssue resolveIssue(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body) {
        String actor = TenantScope.requireUser().getEmail();
        String note = body != null ? body.get("note") : "Resolved";
        jdbc.update("UPDATE validation_issues SET status = 'RESOLVED', resolved_by = ?, resolution_note = ? WHERE id = ?",
                actor, note, id);
        return getIssue(id);
    }

    @PostMapping("/issues/{id}/waive")
    @PreAuthorize("hasAuthority('validation:waive')")
    public ValidationIssue waiveIssue(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String actor = TenantScope.requireUser().getEmail();
        String note = body.get("note");
        if (note == null || note.isBlank()) throw ApiException.badRequest("Waiver rationale note is required");
        jdbc.update("UPDATE validation_issues SET status = 'WAIVED', resolved_by = ?, resolution_note = ? WHERE id = ?",
                actor, note, id);
        auditService.record("ISSUE_WAIVED", "VALIDATION_ISSUE", id.toString(), null, Map.of("note", note));
        return getIssue(id);
    }

    @GetMapping("/issues/{id}")
    @PreAuthorize("hasAuthority('validation:read')")
    public ValidationIssue getIssue(@PathVariable UUID id) {
        List<ValidationIssue> list = jdbc.query("""
            SELECT iss.id, iss.batch_id, iss.staging_row_id, iss.enrolment_id, iss.institution_id,
                   i.aicte_id as inst_code, iss.rule_code, iss.severity, iss.field, iss.message,
                   iss.hint, iss.context, iss.apaar_masked, iss.row_no, iss.status, iss.resolved_by, iss.resolution_note
            FROM validation_issues iss
            JOIN institutions i ON i.id = iss.institution_id
            WHERE iss.id = ?
        """, (rs, rowNum) -> new ValidationIssue(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("batch_id"),
                (UUID) rs.getObject("staging_row_id"),
                (UUID) rs.getObject("enrolment_id"),
                (UUID) rs.getObject("institution_id"),
                rs.getString("inst_code"),
                rs.getString("rule_code"),
                rs.getString("severity"),
                rs.getString("field"),
                rs.getString("message"),
                rs.getString("hint"),
                Json.readMap(rs.getString("context")),
                rs.getString("apaar_masked"),
                rs.getInt("row_no"),
                rs.getString("status"),
                rs.getString("resolved_by"),
                rs.getString("resolution_note")
        ), id);
        if (list.isEmpty()) throw ApiException.notFound("Issue");
        return list.get(0);
    }

    @GetMapping("/issues/duplicates")
    @PreAuthorize("hasAuthority('validation:read')")
    public List<Map<String, Object>> getDuplicateRegister() {
        return jdbc.query("""
            SELECT s.apaar_id, s.full_name,
                   json_agg(json_build_object(
                       'enrolmentId', e.id,
                       'institutionId', i.id,
                       'institutionCode', i.aicte_id,
                       'institutionName', i.name,
                       'programCode', p.program_code,
                       'status', e.status,
                       'admissionDate', e.admission_date
                   )) as enrolments,
                   COUNT(DISTINCT e.institution_id) as inst_count
            FROM enrolments e
            JOIN students s ON s.id = e.student_id
            JOIN institutions i ON i.id = e.institution_id
            JOIN programs p ON p.id = e.program_id
            WHERE e.academic_year = '2026-27' AND e.status NOT IN ('WITHDRAWN')
            GROUP BY s.apaar_id, s.full_name
            HAVING COUNT(DISTINCT e.institution_id) > 1
        """, (rs, rowNum) -> {
            String apaar = rs.getString("apaar_id");
            String enrolsJson = rs.getString("enrolments");
            return Map.of(
                    "apaarId", apaar,
                    "apaarMasked", com.vidyasetu.common.Masking.maskApaar(apaar),
                    "studentName", rs.getString("full_name"),
                    "enrolments", Json.readList(enrolsJson),
                    "status", "OPEN"
            );
        });
    }

    @GetMapping("/issues/rules")
    public List<Map<String, Object>> getRulesCatalog() {
        return List.of(
                Map.of("code", "V-APAAR-001", "group", "Group A - APAAR Identity", "severity", "ERROR", "description", "APAAR ID missing in record", "hint", "Provide 12-digit APAAR ID"),
                Map.of("code", "V-APAAR-002", "group", "Group A - APAAR Identity", "severity", "ERROR", "description", "APAAR ID format invalid (must be 12 numeric digits)", "hint", "Check digits"),
                Map.of("code", "V-APAAR-003", "group", "Group A - APAAR Identity", "severity", "ERROR", "description", "Suspicious pattern (all-same or sequential digits)", "hint", "Use authentic APAAR"),
                Map.of("code", "V-APAAR-004", "group", "Group A - APAAR Identity", "severity", "ERROR", "description", "APAAR ID not found or deactivated in national registry", "hint", "Verify student profile"),
                Map.of("code", "V-APAAR-005", "group", "Group A - APAAR Identity", "severity", "ERROR / WARN", "description", "Identity mismatch against registry (DOB, name or gender)", "hint", "Compare entered vs registry"),
                Map.of("code", "V-DUP-001", "group", "Group B - Duplicates", "severity", "ERROR", "description", "Duplicate APAAR in same upload batch", "hint", "Remove repeated row"),
                Map.of("code", "V-DUP-003", "group", "Group B - Duplicates", "severity", "CRITICAL", "description", "Active enrolment of student at another institution", "hint", "Resolve legitimate transfer"),
                Map.of("code", "V-DUP-004", "group", "Group B - Duplicates", "severity", "WARN", "description", "Ghost student: identical DOB and near-identical name with different APAAR", "hint", "Verify identity papers"),
                Map.of("code", "V-CAT-001", "group", "Group C - Data Consistency", "severity", "ERROR", "description", "Nationality, quota or passport document mismatch", "hint", "Foreign students must have passport"),
                Map.of("code", "V-INT-001", "group", "Group D - Seat & Intake Caps", "severity", "CRITICAL", "description", "Regular admissions exceed approved intake", "hint", "Admissions cannot exceed approved intake"),
                Map.of("code", "V-INT-002a", "group", "Group D - Seat & Intake Caps", "severity", "ERROR", "description", "FN/OCI/CIWGC supernumerary cap (15%) breached", "hint", "Check pool quota"),
                Map.of("code", "V-INT-002c", "group", "Group D - Seat & Intake Caps", "severity", "ERROR", "description", "Tuition Fee Waiver supernumerary cap (5%) breached", "hint", "Check TFW quota")
        );
    }
}
