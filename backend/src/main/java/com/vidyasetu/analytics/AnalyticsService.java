package com.vidyasetu.analytics;

import com.vidyasetu.common.ApiException;
import com.vidyasetu.security.TenantScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AnalyticsService {
    private final JdbcTemplate jdbc;

    public AnalyticsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> getNationalSummary() {
        int totalInsts = jdbc.queryForObject("SELECT count(*) FROM institutions", Integer.class);

        Integer reportingInstsObj = jdbc.queryForObject("""
            SELECT count(DISTINCT institution_id) FROM enrolments WHERE academic_year = '2026-27'
        """, Integer.class);
        int reportingInsts = reportingInstsObj != null ? reportingInstsObj : 0;

        Integer totalEnrolObj = jdbc.queryForObject("""
            SELECT count(*) FROM enrolments WHERE academic_year = '2026-27' AND status NOT IN ('WITHDRAWN')
        """, Integer.class);
        int totalEnrol = totalEnrolObj != null ? totalEnrolObj : 0;

        Integer totalApprovedObj = jdbc.queryForObject("""
            SELECT COALESCE(sum(approved_intake), 0) FROM programs WHERE academic_year = '2026-27'
        """, Integer.class);
        int totalApproved = totalApprovedObj != null ? totalApprovedObj : 0;

        double fillRatio = totalApproved > 0 ? (double) totalEnrol / totalApproved : 0.0;

        Integer openCriticalObj = jdbc.queryForObject("""
            SELECT count(*) FROM validation_issues WHERE severity = 'CRITICAL' AND status = 'OPEN'
        """, Integer.class);
        int openCritical = openCriticalObj != null ? openCriticalObj : 0;

        Integer openIssuesObj = jdbc.queryForObject("""
            SELECT count(*) FROM validation_issues WHERE status = 'OPEN'
        """, Integer.class);
        int openIssues = openIssuesObj != null ? openIssuesObj : 0;

        Integer anomOpenObj = jdbc.queryForObject("""
            SELECT count(*) FROM anomalies WHERE status = 'OPEN' AND academic_year = '2026-27'
        """, Integer.class);
        int anomOpen = anomOpenObj != null ? anomOpenObj : 0;

        Integer anomReviewObj = jdbc.queryForObject("""
            SELECT count(*) FROM anomalies WHERE status = 'UNDER_REVIEW' AND academic_year = '2026-27'
        """, Integer.class);
        int anomReview = anomReviewObj != null ? anomReviewObj : 0;

        Integer totalSubmittedRowsObj = jdbc.queryForObject("SELECT COALESCE(sum(total_rows), 0) FROM upload_batches", Integer.class);
        int totalRows = totalSubmittedRowsObj != null ? totalSubmittedRowsObj : 0;

        Integer acceptedRowsObj = jdbc.queryForObject("SELECT COALESCE(sum(accepted_rows), 0) FROM upload_batches", Integer.class);
        int acceptedRows = acceptedRowsObj != null ? acceptedRowsObj : 0;

        double passRate = totalRows > 0 ? (double) acceptedRows / totalRows : 0.94;

        Integer excessSeatsObj = jdbc.queryForObject("""
            SELECT count(*) FROM enrolments WHERE seat_class = 'EXCESS' AND academic_year = '2026-27'
        """, Integer.class);
        int excessSeats = excessSeatsObj != null ? excessSeatsObj : 0;

        Integer supernumObj = jdbc.queryForObject("""
            SELECT count(*) FROM enrolments WHERE seat_class = 'SUPERNUMERARY' AND academic_year = '2026-27'
        """, Integer.class);
        int supernumTotal = supernumObj != null ? supernumObj : 0;

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("institutionsReporting", reportingInsts);
        map.put("totalInstitutions", totalInsts);
        map.put("totalEnrolment", totalEnrol);
        map.put("totalApprovedIntake", totalApproved);
        map.put("fillRatio", Math.round(fillRatio * 100.0) / 100.0);
        map.put("openCritical", openCritical);
        map.put("openIssues", openIssues);
        map.put("anomaliesOpen", anomOpen);
        map.put("anomaliesUnderReview", anomReview);
        map.put("validationPassRate", Math.round(passRate * 1000.0) / 10.0);
        map.put("excessSeats", excessSeats);
        map.put("supernumeraryTotal", supernumTotal);
        return map;
    }

    public List<Map<String, Object>> getByState() {
        return jdbc.query("""
            SELECT i.state, i.state_code,
                   COUNT(DISTINCT i.id) as inst_count,
                   COALESCE(SUM(p.approved_intake), 0) as approved_intake,
                   COALESCE(e.enrolled, 0) as enrolled,
                   COALESCE(AVG(r.risk_score), 0) as risk_avg,
                   COALESCE(MAX(r.risk_score), 0) as risk_max
            FROM institutions i
            LEFT JOIN programs p ON p.institution_id = i.id AND p.academic_year = '2026-27'
            LEFT JOIN (
                SELECT institution_id, count(*) as enrolled
                FROM enrolments WHERE academic_year = '2026-27' AND status NOT IN ('WITHDRAWN') GROUP BY institution_id
            ) e ON e.institution_id = i.id
            LEFT JOIN institution_risk r ON r.institution_id = i.id AND r.academic_year = '2026-27'
            GROUP BY i.state, i.state_code, e.enrolled
            ORDER BY approved_intake DESC
        """, (rs, rowNum) -> {
            int intake = rs.getInt("approved_intake");
            int enrolled = rs.getInt("enrolled");
            double fill = intake > 0 ? (double) enrolled / intake : 0.0;
            return Map.of(
                    "state", rs.getString("state"),
                    "stateCode", rs.getString("state_code"),
                    "institutions", rs.getInt("inst_count"),
                    "approvedIntake", intake,
                    "enrolled", enrolled,
                    "fillRatio", Math.round(fill * 100.0) / 100.0,
                    "riskAvg", Math.round(rs.getDouble("risk_avg") * 10.0) / 10.0,
                    "riskMax", Math.round(rs.getDouble("risk_max") * 10.0) / 10.0
            );
        });
    }

    public List<Map<String, Object>> getByLevel() {
        return jdbc.query("""
            SELECT p.level,
                   COALESCE(SUM(p.approved_intake), 0) as approved_intake,
                   COUNT(e.id) as enrolled
            FROM programs p
            LEFT JOIN enrolments e ON e.program_id = p.id AND e.academic_year = '2026-27' AND e.status NOT IN ('WITHDRAWN')
            WHERE p.academic_year = '2026-27'
            GROUP BY p.level
            ORDER BY approved_intake DESC
        """, (rs, rowNum) -> Map.of(
                "level", rs.getString("level"),
                "approvedIntake", rs.getInt("approved_intake"),
                "enrolled", rs.getInt("enrolled")
        ));
    }

    public List<Map<String, Object>> get5YearTrend() {
        return jdbc.query("""
            SELECT academic_year,
                   SUM(approved_intake) as approved_intake,
                   SUM(enrolled) as enrolled
            FROM enrolment_stats_history
            GROUP BY academic_year
            UNION ALL
            SELECT '2026-27' as academic_year,
                   COALESCE((SELECT SUM(approved_intake) FROM programs WHERE academic_year = '2026-27'), 0) as approved_intake,
                   COALESCE((SELECT COUNT(*) FROM enrolments WHERE academic_year = '2026-27' AND status NOT IN ('WITHDRAWN')), 0) as enrolled
            ORDER BY academic_year ASC
        """, (rs, rowNum) -> Map.of(
                "academicYear", rs.getString("academic_year"),
                "approvedIntake", rs.getInt("approved_intake"),
                "enrolled", rs.getInt("enrolled")
        ));
    }

    public List<Map<String, Object>> getSupernumeraryBreakdown() {
        Integer fnUsed = jdbc.queryForObject("SELECT count(*) FROM enrolments WHERE admission_quota = 'FN_OCI_SUPERNUM' AND academic_year = '2026-27'", Integer.class);
        Integer ciwgcUsed = jdbc.queryForObject("SELECT count(*) FROM enrolments WHERE admission_quota = 'CIWGC_SUPERNUM' AND academic_year = '2026-27'", Integer.class);
        Integer tfwUsed = jdbc.queryForObject("SELECT count(*) FROM enrolments WHERE admission_quota = 'TFW_SUPERNUM' AND academic_year = '2026-27'", Integer.class);
        Integer nriUsed = jdbc.queryForObject("SELECT count(*) FROM enrolments WHERE admission_quota = 'NRI' AND academic_year = '2026-27'", Integer.class);

        int totalApproved = jdbc.queryForObject("SELECT COALESCE(sum(approved_intake), 0) FROM programs WHERE academic_year = '2026-27'", Integer.class);
        int fnPoolCap = (int) Math.round(totalApproved * 0.15);
        int tfwCap = (int) Math.round(totalApproved * 0.05);
        int nriCap = (int) Math.round(totalApproved * 0.15);

        return List.of(
                Map.of("category", "FN_OCI_PIO", "used", fnUsed != null ? fnUsed : 0, "cap", fnPoolCap),
                Map.of("category", "CIWGC", "used", ciwgcUsed != null ? ciwgcUsed : 0, "cap", (int) Math.round(fnPoolCap / 3.0)),
                Map.of("category", "TFW", "used", tfwUsed != null ? tfwUsed : 0, "cap", tfwCap),
                Map.of("category", "NRI", "used", nriUsed != null ? nriUsed : 0, "cap", nriCap)
        );
    }

    public List<Map<String, Object>> getIssuesByRule() {
        return jdbc.query("""
            SELECT rule_code, severity, count(*) as count
            FROM validation_issues GROUP BY rule_code, severity ORDER BY count DESC
        """, (rs, rowNum) -> Map.of(
                "ruleCode", rs.getString("rule_code"),
                "severity", rs.getString("severity"),
                "count", rs.getInt("count")
        ));
    }

    public List<Map<String, Object>> getTopRiskInstitutions() {
        return jdbc.query("""
            SELECT i.id, i.aicte_id, i.name, i.state, r.risk_score, r.level, r.top_reasons
            FROM institutions i
            JOIN institution_risk r ON r.institution_id = i.id AND r.academic_year = '2026-27'
            ORDER BY r.risk_score DESC LIMIT 10
        """, (rs, rowNum) -> Map.of(
                "institutionId", rs.getObject("id").toString(),
                "institutionCode", rs.getString("aicte_id"),
                "institutionName", rs.getString("name"),
                "state", rs.getString("state"),
                "riskScore", rs.getDouble("risk_score"),
                "level", rs.getString("level"),
                "topReasons", rs.getString("top_reasons") != null ? rs.getString("top_reasons") : "[]"
        ));
    }

    public List<Map<String, Object>> getExcessRegister() {
        return jdbc.query("""
            SELECT i.id as institution_id, i.aicte_id, i.name as inst_name,
                   p.program_code, p.name as prog_name, p.approved_intake,
                   COUNT(e.id) FILTER (WHERE e.seat_class = 'REGULAR') as regular_count,
                   COUNT(e.id) FILTER (WHERE e.seat_class = 'EXCESS') as excess_count
            FROM programs p
            JOIN institutions i ON i.id = p.institution_id
            JOIN enrolments e ON e.program_id = p.id AND e.academic_year = '2026-27' AND e.status NOT IN ('WITHDRAWN')
            WHERE p.academic_year = '2026-27'
            GROUP BY i.id, i.aicte_id, i.name, p.program_code, p.name, p.approved_intake
            HAVING COUNT(e.id) FILTER (WHERE e.seat_class = 'EXCESS') > 0
               OR COUNT(e.id) FILTER (WHERE e.seat_class = 'REGULAR') > p.approved_intake
            ORDER BY excess_count DESC
        """, (rs, rowNum) -> Map.of(
                "institutionId", rs.getObject("institution_id").toString(),
                "institutionCode", rs.getString("aicte_id"),
                "institutionName", rs.getString("inst_name"),
                "programCode", rs.getString("program_code"),
                "programName", rs.getString("prog_name"),
                "approvedIntake", rs.getInt("approved_intake"),
                "regular", rs.getInt("regular_count"),
                "excess", rs.getInt("excess_count")
        ));
    }

    public List<Map<String, Object>> getInstitutionMeters(UUID institutionId) {
        TenantScope.assertCanAccessInstitution(institutionId);
        return jdbc.query("""
            SELECT p.id, p.program_code, p.name as prog_name, p.level,
                   p.approved_intake, p.approved_lateral_intake,
                   COUNT(e.id) FILTER (WHERE e.seat_class = 'REGULAR' AND e.entry_type = 'FIRST_YEAR') as regular_count,
                   COUNT(e.id) FILTER (WHERE e.seat_class = 'SUPERNUMERARY') as supernum_count,
                   COUNT(e.id) FILTER (WHERE e.seat_class = 'EXCESS') as excess_count,
                   COUNT(e.id) FILTER (WHERE e.entry_type = 'LATERAL') as lateral_count,
                   COUNT(e.id) FILTER (WHERE e.admission_quota = 'FN_OCI_SUPERNUM') as fn_count,
                   COUNT(e.id) FILTER (WHERE e.admission_quota = 'CIWGC_SUPERNUM') as ciwgc_count,
                   COUNT(e.id) FILTER (WHERE e.admission_quota = 'TFW_SUPERNUM') as tfw_count,
                   COUNT(e.id) FILTER (WHERE e.admission_quota = 'NRI') as nri_count
            FROM programs p
            LEFT JOIN enrolments e ON e.program_id = p.id AND e.academic_year = '2026-27' AND e.status NOT IN ('WITHDRAWN')
            WHERE p.institution_id = ? AND p.academic_year = '2026-27'
            GROUP BY p.id, p.program_code, p.name, p.level, p.approved_intake, p.approved_lateral_intake
            ORDER BY p.program_code ASC
        """, (rs, rowNum) -> {
            int approved = rs.getInt("approved_intake");
            int regular = rs.getInt("regular_count");
            double fill = approved > 0 ? (double) regular / approved * 100.0 : 0.0;

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("programId", rs.getObject("id").toString());
            map.put("programCode", rs.getString("program_code"));
            map.put("programName", rs.getString("prog_name"));
            map.put("level", rs.getString("level"));
            map.put("approvedIntake", approved);
            map.put("approvedLateralIntake", rs.getInt("approved_lateral_intake"));
            map.put("regular", regular);
            map.put("supernumerary", rs.getInt("supernum_count"));
            map.put("excess", rs.getInt("excess_count"));
            map.put("lateral", rs.getInt("lateral_count"));
            map.put("fnOci", rs.getInt("fn_count"));
            map.put("ciwgc", rs.getInt("ciwgc_count"));
            map.put("tfw", rs.getInt("tfw_count"));
            map.put("nri", rs.getInt("nri_count"));
            map.put("fillPct", Math.round(fill * 10.0) / 10.0);
            return map;
        }, institutionId);
    }

    public Map<String, Object> getInstitutionQuality(UUID institutionId) {
        TenantScope.assertCanAccessInstitution(institutionId);

        Integer submittedObj = jdbc.queryForObject("SELECT COALESCE(SUM(total_rows), 0) FROM upload_batches WHERE institution_id = ?", Integer.class, institutionId);
        int submitted = submittedObj != null ? submittedObj : 0;

        Integer acceptedObj = jdbc.queryForObject("SELECT COALESCE(SUM(accepted_rows), 0) FROM upload_batches WHERE institution_id = ?", Integer.class, institutionId);
        int accepted = acceptedObj != null ? acceptedObj : 0;

        Integer flaggedObj = jdbc.queryForObject("SELECT COALESCE(SUM(flagged_rows), 0) FROM upload_batches WHERE institution_id = ?", Integer.class, institutionId);
        int flagged = flaggedObj != null ? flaggedObj : 0;

        Integer rejectedObj = jdbc.queryForObject("SELECT COALESCE(SUM(rejected_rows), 0) FROM upload_batches WHERE institution_id = ?", Integer.class, institutionId);
        int rejected = rejectedObj != null ? rejectedObj : 0;

        Integer openIssObj = jdbc.queryForObject("SELECT count(*) FROM validation_issues WHERE institution_id = ? AND status = 'OPEN'", Integer.class, institutionId);
        int openIssues = openIssObj != null ? openIssObj : 0;

        double qScore = submitted > 0 ? ((double) accepted / submitted) * 100.0 : 100.0;
        double issuesPer100 = accepted > 0 ? ((double) openIssues / accepted) * 100.0 : 0.0;

        List<Map<String, Object>> trend = jdbc.query("""
            SELECT id, created_at, accepted_rows, total_rows
            FROM upload_batches WHERE institution_id = ? ORDER BY created_at ASC LIMIT 10
        """, (rs, rowNum) -> {
            int tot = rs.getInt("total_rows");
            int acc = rs.getInt("accepted_rows");
            double sc = tot > 0 ? (double) acc / tot * 100.0 : 100.0;
            return Map.of(
                    "batchId", rs.getObject("id").toString(),
                    "date", rs.getTimestamp("created_at").toInstant().toString(),
                    "score", Math.round(sc * 10.0) / 10.0
            );
        }, institutionId);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("submitted", submitted);
        res.put("accepted", accepted);
        res.put("flagged", flagged);
        res.put("rejected", rejected);
        res.put("qualityScore", Math.round(qScore * 10.0) / 10.0);
        res.put("issuesPer100", Math.round(issuesPer100 * 10.0) / 10.0);
        res.put("openIssues", openIssues);
        res.put("trend", trend);
        return res;
    }
}
