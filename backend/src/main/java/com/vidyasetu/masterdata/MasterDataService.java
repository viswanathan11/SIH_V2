package com.vidyasetu.masterdata;

import com.vidyasetu.audit.AuditService;
import com.vidyasetu.common.ApiException;
import com.vidyasetu.security.TenantScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class MasterDataService {
    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    public MasterDataService(JdbcTemplate jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public record InstitutionSummary(
            UUID id,
            String aicteId,
            String name,
            String state,
            String stateCode,
            String district,
            String institutionType,
            String status,
            int approvedIntake,
            int enrolled,
            double fillRatio,
            double riskScore,
            String riskLevel,
            long openIssues,
            long openCritical
    ) {}

    public record ProgramDto(
            UUID id,
            UUID institutionId,
            String programCode,
            String level,
            String name,
            String academicYear,
            int approvedIntake,
            int approvedLateralIntake,
            boolean tfwEnabled,
            boolean fnOciEnabled
    ) {}

    public record PolicyDto(
            String key,
            String value,
            String description,
            String updatedBy,
            String updatedAt
    ) {}

    public List<InstitutionSummary> listInstitutions() {
        UUID tenantInst = TenantScope.resolveInstitutionFilter(null);
        String sql = """
            SELECT i.id, i.aicte_id, i.name, i.state, i.state_code, i.district, i.institution_type, i.status,
                   COALESCE(p.approved_intake, 0) as approved_intake,
                   COALESCE(e.enrolled, 0) as enrolled,
                   COALESCE(r.risk_score, 0) as risk_score,
                   COALESCE(r.level, 'LOW') as risk_level,
                   COALESCE(iss.open_issues, 0) as open_issues,
                   COALESCE(iss.open_critical, 0) as open_critical
            FROM institutions i
            LEFT JOIN (
                SELECT institution_id, SUM(approved_intake) as approved_intake
                FROM programs WHERE academic_year = '2026-27' GROUP BY institution_id
            ) p ON p.institution_id = i.id
            LEFT JOIN (
                SELECT institution_id, COUNT(*) as enrolled
                FROM enrolments WHERE academic_year = '2026-27' AND status NOT IN ('WITHDRAWN') GROUP BY institution_id
            ) e ON e.institution_id = i.id
            LEFT JOIN (
                SELECT institution_id, risk_score, level
                FROM institution_risk WHERE academic_year = '2026-27'
            ) r ON r.institution_id = i.id
            LEFT JOIN (
                SELECT institution_id,
                       COUNT(*) as open_issues,
                       COUNT(*) FILTER (WHERE severity = 'CRITICAL') as open_critical
                FROM validation_issues WHERE status = 'OPEN' GROUP BY institution_id
            ) iss ON iss.institution_id = i.id
            """ + (tenantInst != null ? " WHERE i.id = ? " : "") +
            " ORDER BY i.aicte_id ASC ";

        Object[] params = tenantInst != null ? new Object[]{tenantInst} : new Object[0];

        return jdbc.query(sql, (rs, rowNum) -> {
            int intake = rs.getInt("approved_intake");
            int enrolled = rs.getInt("enrolled");
            double fill = intake > 0 ? (double) enrolled / intake : 0.0;
            return new InstitutionSummary(
                    (UUID) rs.getObject("id"),
                    rs.getString("aicte_id"),
                    rs.getString("name"),
                    rs.getString("state"),
                    rs.getString("state_code"),
                    rs.getString("district"),
                    rs.getString("institution_type"),
                    rs.getString("status"),
                    intake,
                    enrolled,
                    Math.round(fill * 100.0) / 100.0,
                    rs.getDouble("risk_score"),
                    rs.getString("risk_level"),
                    rs.getLong("open_issues"),
                    rs.getLong("open_critical")
            );
        }, params);
    }

    public Map<String, Object> getInstitutionDetail(UUID id) {
        TenantScope.assertCanAccessInstitution(id);
        List<InstitutionSummary> list = listInstitutions();
        InstitutionSummary sum = list.stream().filter(i -> i.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Institution"));
        List<ProgramDto> progs = getPrograms(id);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", sum.id());
        map.put("aicteId", sum.aicteId());
        map.put("name", sum.name());
        map.put("state", sum.state());
        map.put("stateCode", sum.stateCode());
        map.put("district", sum.district());
        map.put("institutionType", sum.institutionType());
        map.put("status", sum.status());
        map.put("approvedIntake", sum.approvedIntake());
        map.put("enrolled", sum.enrolled());
        map.put("fillRatio", sum.fillRatio());
        map.put("riskScore", sum.riskScore());
        map.put("riskLevel", sum.riskLevel());
        map.put("openIssues", sum.openIssues());
        map.put("openCritical", sum.openCritical());
        map.put("programs", progs);
        return map;
    }

    public List<ProgramDto> getPrograms(UUID institutionId) {
        TenantScope.assertCanAccessInstitution(institutionId);
        return jdbc.query("""
            SELECT id, institution_id, program_code, level, name, academic_year,
                   approved_intake, approved_lateral_intake, tfw_enabled, fn_oci_enabled
            FROM programs WHERE institution_id = ? ORDER BY program_code ASC
        """, (rs, rowNum) -> new ProgramDto(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("institution_id"),
                rs.getString("program_code"),
                rs.getString("level"),
                rs.getString("name"),
                rs.getString("academic_year"),
                rs.getInt("approved_intake"),
                rs.getInt("approved_lateral_intake"),
                rs.getBoolean("tfw_enabled"),
                rs.getBoolean("fn_oci_enabled")
        ), institutionId);
    }

    @Transactional
    public ProgramDto updateProgram(UUID id, Integer approvedIntake, Integer approvedLateralIntake,
                                    Boolean tfwEnabled, Boolean fnOciEnabled) {
        ProgramDto existing = jdbc.query("SELECT * FROM programs WHERE id = ?", (rs, rowNum) -> new ProgramDto(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("institution_id"),
                rs.getString("program_code"),
                rs.getString("level"),
                rs.getString("name"),
                rs.getString("academic_year"),
                rs.getInt("approved_intake"),
                rs.getInt("approved_lateral_intake"),
                rs.getBoolean("tfw_enabled"),
                rs.getBoolean("fn_oci_enabled")
        ), id).stream().findFirst().orElseThrow(() -> ApiException.notFound("Program"));

        TenantScope.assertCanAccessInstitution(existing.institutionId());

        int newIntake = approvedIntake != null ? approvedIntake : existing.approvedIntake();
        int newLat = approvedLateralIntake != null ? approvedLateralIntake : existing.approvedLateralIntake();
        boolean newTfw = tfwEnabled != null ? tfwEnabled : existing.tfwEnabled();
        boolean newFn = fnOciEnabled != null ? fnOciEnabled : existing.fnOciEnabled();

        jdbc.update("""
            UPDATE programs SET approved_intake = ?, approved_lateral_intake = ?,
                                tfw_enabled = ?, fn_oci_enabled = ?, updated_at = now()
            WHERE id = ?
        """, newIntake, newLat, newTfw, newFn, id);

        auditService.record("PROGRAM_INTAKE_UPDATED", "PROGRAM", id.toString(), existing.institutionId(),
                Map.of("oldIntake", existing.approvedIntake(), "newIntake", newIntake, "programCode", existing.programCode()));

        return new ProgramDto(existing.id(), existing.institutionId(), existing.programCode(), existing.level(),
                existing.name(), existing.academicYear(), newIntake, newLat, newTfw, newFn);
    }

    public List<PolicyDto> listPolicy() {
        return jdbc.query("SELECT key, value, description, updated_by, updated_at FROM policy_config ORDER BY key",
                (rs, rowNum) -> new PolicyDto(
                        rs.getString("key"),
                        rs.getString("value"),
                        rs.getString("description"),
                        rs.getString("updated_by"),
                        rs.getTimestamp("updated_at").toInstant().toString()
                ));
    }

    public String getPolicy(String key, String defaultValue) {
        List<String> list = jdbc.query("SELECT value FROM policy_config WHERE key = ?",
                (rs, rowNum) -> rs.getString("value"), key);
        return list.isEmpty() ? defaultValue : list.get(0);
    }

    public double getPolicyDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(getPolicy(key, String.valueOf(defaultValue)));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Transactional
    public PolicyDto updatePolicy(String key, String value) {
        String actor = TenantScope.currentUser() != null ? TenantScope.currentUser().getEmail() : "admin";
        int rows = jdbc.update("UPDATE policy_config SET value = ?, updated_by = ?, updated_at = now() WHERE key = ?",
                value, actor, key);
        if (rows == 0) throw ApiException.notFound("Policy " + key);

        auditService.record("POLICY_UPDATED", "POLICY", key, null, Map.of("key", key, "value", value));
        return jdbc.query("SELECT key, value, description, updated_by, updated_at FROM policy_config WHERE key = ?",
                (rs, rowNum) -> new PolicyDto(
                        rs.getString("key"),
                        rs.getString("value"),
                        rs.getString("description"),
                        rs.getString("updated_by"),
                        rs.getTimestamp("updated_at").toInstant().toString()
                ), key).get(0);
    }
}
