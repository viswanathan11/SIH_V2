package com.vidyasetu.anomaly;

import com.vidyasetu.audit.AuditService;
import com.vidyasetu.common.ApiException;
import com.vidyasetu.common.Json;
import com.vidyasetu.masterdata.MasterDataService;
import com.vidyasetu.realtime.EventBusService;
import com.vidyasetu.security.TenantScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AnomalyService {
    private static final Logger log = LoggerFactory.getLogger(AnomalyService.class);

    private final JdbcTemplate jdbc;
    private final MasterDataService masterDataService;
    private final AuditService auditService;
    private final EventBusService eventBusService;

    public AnomalyService(JdbcTemplate jdbc, MasterDataService masterDataService,
                          AuditService auditService, EventBusService eventBusService) {
        this.jdbc = jdbc;
        this.masterDataService = masterDataService;
        this.auditService = auditService;
        this.eventBusService = eventBusService;
    }

    public record AnomalyDto(
            UUID id,
            UUID institutionId,
            String institutionCode,
            String institutionName,
            String programCode,
            String academicYear,
            String kind,
            double score,
            String severity,
            List<Map<String, Object>> reasons,
            String explanation,
            String status,
            String reviewNote,
            String reviewedBy,
            String createdAt
    ) {}

    public record RiskDto(
            UUID institutionId,
            double riskScore,
            String level,
            Map<String, Object> components,
            List<String> topReasons,
            String computedAt
    ) {}

    @Transactional
    public int recomputeAnomalies() {
        log.info("Recomputing anomalies and institution risk scores...");

        // 1. Fetch institutions and programs
        List<Map<String, Object>> progs = jdbc.query("""
            SELECT p.id as program_id, p.program_code, p.level, p.approved_intake,
                   i.id as institution_id, i.aicte_id, i.name as institution_name
            FROM programs p
            JOIN institutions i ON i.id = p.institution_id
            WHERE p.academic_year = '2026-27'
        """, (rs, rowNum) -> Map.of(
                "programId", rs.getObject("program_id"),
                "programCode", rs.getString("program_code"),
                "level", rs.getString("level"),
                "approvedIntake", rs.getInt("approved_intake"),
                "institutionId", rs.getObject("institution_id"),
                "aicteId", rs.getString("aicte_id"),
                "instName", rs.getString("institution_name")
        ));

        // Delete existing un-reviewed anomalies for 2026-27
        jdbc.update("DELETE FROM anomalies WHERE academic_year = '2026-27' AND status = 'OPEN'");

        List<double[]> featureList = new ArrayList<>();
        List<Map<String, Object>> rowMetadata = new ArrayList<>();
        int countAnomalies = 0;

        for (Map<String, Object> p : progs) {
            UUID progId = (UUID) p.get("programId");
            UUID instId = (UUID) p.get("institutionId");
            String progCode = (String) p.get("programCode");
            String aicteId = (String) p.get("aicteId");
            int approved = (int) p.get("approvedIntake");

            // Aggregate current enrolments
            Map<String, Object> stats = jdbc.query("""
                SELECT
                    COUNT(*) as total_enrolled,
                    COUNT(*) FILTER (WHERE seat_class = 'REGULAR') as regular_enrolled,
                    COUNT(*) FILTER (WHERE seat_class = 'SUPERNUMERARY') as supernum_enrolled,
                    COUNT(*) FILTER (WHERE seat_class = 'EXCESS') as excess_enrolled,
                    COUNT(*) FILTER (WHERE admission_quota = 'NRI') as nri_enrolled,
                    COUNT(*) FILTER (WHERE admission_quota IN ('FN_OCI_SUPERNUM','CIWGC_SUPERNUM')) as fn_enrolled,
                    COUNT(*) FILTER (WHERE apaar_verified = false) as unverified_enrolled
                FROM enrolments
                WHERE program_id = ? AND academic_year = '2026-27' AND status NOT IN ('WITHDRAWN')
            """, rs -> {
                rs.next();
                return Map.of(
                        "total", rs.getInt("total_enrolled"),
                        "regular", rs.getInt("regular_enrolled"),
                        "supernum", rs.getInt("supernum_enrolled"),
                        "excess", rs.getInt("excess_enrolled"),
                        "nri", rs.getInt("nri_enrolled"),
                        "fn", rs.getInt("fn_enrolled"),
                        "unverified", rs.getInt("unverified_enrolled")
                );
            }, progId);

            int total = (int) stats.get("total");
            int regular = (int) stats.get("regular");
            int supernum = (int) stats.get("supernum");
            int excess = (int) stats.get("excess");
            int nri = (int) stats.get("nri");
            int fn = (int) stats.get("fn");
            int unverified = (int) stats.get("unverified");

            double fillRatio = approved > 0 ? (double) regular / approved : 0.0;
            double superShare = approved > 0 ? (double) supernum / approved : 0.0;
            double nriShare = approved > 0 ? (double) nri / approved : 0.0;
            double fnShare = approved > 0 ? (double) fn / approved : 0.0;

            // Day burst
            Integer maxDayAdmissions = jdbc.queryForObject("""
                SELECT COALESCE(MAX(cnt), 0) FROM (
                    SELECT COUNT(*) as cnt FROM enrolments
                    WHERE program_id = ? AND academic_year = '2026-27' AND status NOT IN ('WITHDRAWN')
                    GROUP BY admission_date
                ) t
            """, Integer.class, progId);
            int burstCount = maxDayAdmissions != null ? maxDayAdmissions : 0;
            double burstShare = regular > 0 ? (double) burstCount / regular : 0.0;

            // Late admissions after 10-31
            Integer lateAdmissions = jdbc.queryForObject("""
                SELECT COUNT(*) FROM enrolments
                WHERE program_id = ? AND academic_year = '2026-27' AND admission_date > '2026-10-31'
                  AND status NOT IN ('WITHDRAWN')
            """, Integer.class, progId);
            int lateCount = lateAdmissions != null ? lateAdmissions : 0;

            // Ghost student flags for this program
            Integer ghostCountObj = jdbc.queryForObject("""
                SELECT COUNT(*) FROM validation_issues
                WHERE institution_id = ? AND rule_code = 'V-DUP-004'
            """, Integer.class, instId);
            int ghostCount = ghostCountObj != null ? ghostCountObj : 0;

            // Name mismatch count
            Integer mismatchObj = jdbc.queryForObject("""
                SELECT COUNT(*) FROM validation_issues
                WHERE institution_id = ? AND rule_code = 'V-APAAR-005'
            """, Integer.class, instId);
            int mismatchCount = mismatchObj != null ? mismatchObj : 0;

            // Feature vector for Isolation Forest
            double[] vec = new double[]{
                    fillRatio, superShare, nriShare, fnShare, burstShare, (double) lateCount,
                    (double) ghostCount, (double) excess, (double) unverified, (double) mismatchCount
            };
            featureList.add(vec);

            Map<String, Object> meta = new HashMap<>(p);
            meta.put("fillRatio", fillRatio);
            meta.put("burstCount", burstCount);
            meta.put("burstShare", burstShare);
            meta.put("lateCount", lateCount);
            meta.put("ghostCount", ghostCount);
            meta.put("excess", excess);
            meta.put("supernum", supernum);
            meta.put("regular", regular);
            meta.put("approved", approved);
            meta.put("vec", vec);
            meta.put("mismatchCount", mismatchCount);
            rowMetadata.add(meta);
        }

        // Run Isolation Forest on all feature vectors
        double[][] data = featureList.toArray(new double[0][]);
        IsolationForest iforest = new IsolationForest(200, 256, 20261023L);
        iforest.fit(data);

        // Check chronic under-enrolment from history table (5 consecutive years <= 30%)
        List<Map<String, Object>> underEnrolList = jdbc.query("""
            SELECT institution_id, program_code,
                   COUNT(*) as under_years,
                   AVG(CASE WHEN approved_intake > 0 THEN (enrolled::float / approved_intake) ELSE 0 END) as avg_fill
            FROM enrolment_stats_history
            WHERE enrolled::float / NULLIF(approved_intake, 0) <= 0.30
            GROUP BY institution_id, program_code
            HAVING COUNT(*) >= 5
        """, (rs, rowNum) -> Map.of(
                "institutionId", rs.getObject("institution_id"),
                "programCode", rs.getString("program_code"),
                "years", rs.getInt("under_years"),
                "avgFill", rs.getDouble("avg_fill")
        ));

        Set<String> chronicSet = new HashSet<>();
        for (Map<String, Object> u : underEnrolList) {
            chronicSet.add(u.get("institutionId") + "|" + u.get("programCode"));
        }

        // Detect anomalies per program
        for (int i = 0; i < rowMetadata.size(); i++) {
            Map<String, Object> m = rowMetadata.get(i);
            UUID instId = (UUID) m.get("institutionId");
            UUID progId = (UUID) m.get("programId");
            String progCode = (String) m.get("programCode");
            String aicteId = (String) m.get("aicteId");
            int approved = (int) m.get("approved");
            int regular = (int) m.get("regular");
            int excess = (int) m.get("excess");
            int lateCount = (int) m.get("lateCount");
            int burstCount = (int) m.get("burstCount");
            double burstShare = (double) m.get("burstShare");
            int ghostCount = (int) m.get("ghostCount");
            int mismatchCount = (int) m.get("mismatchCount");
            double[] vec = (double[]) m.get("vec");
            double iScore = iforest.score(vec);

            // Rule 1: Excess regular seats beyond approved intake
            if (excess > 0 || (approved > 0 && regular > approved)) {
                int exc = excess > 0 ? excess : (regular - approved);
                List<Map<String, Object>> reasons = List.of(
                        Map.of("feature", "regular_enrolment", "value", regular, "text", regular + " regular enrolments vs " + approved + " approved seats (" + exc + " excess seats)")
                );
                String expl = "Approved intake breach: " + exc + " students enrolled beyond the approved capacity of " + approved + " seats.";
                insertAnomaly(instId, progId, "2026-27", "excess_enrolment", 0.95, "CRITICAL", vec, reasons, expl);
                countAnomalies++;
            }

            // Rule 2: Chronic under-enrolment (5 years <= 30%)
            if (chronicSet.contains(instId + "|" + progCode)) {
                List<Map<String, Object>> reasons = List.of(
                        Map.of("feature", "chronic_under_enrolment", "value", 5, "text", "Enrolment has remained <= 30% of approved intake for 5 consecutive years")
                );
                String expl = "Chronic under-enrolment detected: admission rate has been below 30% for 5 consecutive years. AICTE intake-reduction policy applies.";
                insertAnomaly(instId, progId, "2026-27", "chronic_under_enrolment", 0.85, "HIGH", vec, reasons, expl);
                countAnomalies++;
            }

            // Rule 3: Burst admissions (>= 40% admitted on 1 day OR >= 25 on one day after cut-off)
            if (burstShare >= 0.40 && burstCount >= 10) {
                List<Map<String, Object>> reasons = List.of(
                        Map.of("feature", "burst_index", "value", Math.round(burstShare * 100), "text", burstCount + " students (" + Math.round(burstShare * 100) + "%) admitted on a single day")
                );
                String expl = "Abnormal admission spike: " + burstCount + " admissions occurred in a single calendar day.";
                insertAnomaly(instId, progId, "2026-27", "burst_admissions", 0.80, "HIGH", vec, reasons, expl);
                countAnomalies++;
            } else if (lateCount >= 25) {
                List<Map<String, Object>> reasons = List.of(
                        Map.of("feature", "late_admissions", "value", lateCount, "text", lateCount + " students admitted after the national cut-off date")
                );
                String expl = "Late admission surge: " + lateCount + " enrolments recorded after the official cut-off date.";
                insertAnomaly(instId, progId, "2026-27", "burst_admissions", 0.78, "HIGH", vec, reasons, expl);
                countAnomalies++;
            }

            // Rule 4: Ghost student cluster (>= 3 V-DUP-004 hits in institution)
            if (ghostCount >= 3) {
                List<Map<String, Object>> reasons = List.of(
                        Map.of("feature", "ghost_cluster", "value", ghostCount, "text", ghostCount + " potential duplicate identities / ghost student flags detected")
                );
                String expl = "Ghost student pattern: " + ghostCount + " records share identical DOB and near-identical names with different APAAR IDs.";
                insertAnomaly(instId, progId, "2026-27", "ghost_student_cluster", 0.88, "CRITICAL", vec, reasons, expl);
                countAnomalies++;
            }

            // High Isolation Forest multi-dimensional outlier (score > 0.65)
            if (iScore > 0.65) {
                List<Map<String, Object>> reasons = List.of(
                        Map.of("feature", "isolation_forest", "value", Math.round(iScore * 100), "text", "Multi-dimensional anomaly score: " + Math.round(iScore * 100) + "/100")
                );
                String expl = "Multi-feature outlier identified by Isolation Forest model (score " + Math.round(iScore * 100) + "/100).";
                insertAnomaly(instId, progId, "2026-27", "multivariate_outlier", iScore, iScore > 0.8 ? "HIGH" : "MEDIUM", vec, reasons, expl);
                countAnomalies++;
            }
        }

        // 3. Roll up to Institution Risk Score (0..100) per institution
        List<UUID> instIds = jdbc.query("SELECT id FROM institutions", (rs, rowNum) -> (UUID) rs.getObject("id"));
        for (UUID iId : instIds) {
            computeInstitutionRisk(iId, "2026-27");
        }

        return countAnomalies;
    }

    private void insertAnomaly(UUID instId, UUID progId, String acadYear, String kind, double score,
                               String severity, double[] vec, List<Map<String, Object>> reasons, String explanation) {
        jdbc.update("""
            INSERT INTO anomalies (id, institution_id, program_id, academic_year, kind, score, severity,
                                  features, reasons, explanation, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', now(), now())
        """, UUID.randomUUID(), instId, progId, acadYear, kind, score, severity,
                Json.write(vec), Json.write(reasons), explanation);
    }

    public void computeInstitutionRisk(UUID institutionId, String academicYear) {
        List<Map<String, Object>> anoms = jdbc.query("""
            SELECT kind, score, severity, explanation, reasons FROM anomalies
            WHERE institution_id = ? AND academic_year = ? AND status != 'DISMISSED'
        """, (rs, rowNum) -> Map.of(
                "kind", rs.getString("kind"),
                "score", rs.getDouble("score"),
                "severity", rs.getString("severity"),
                "explanation", rs.getString("explanation"),
                "reasons", rs.getString("reasons") != null ? rs.getString("reasons") : "[]"
        ), institutionId, academicYear);

        long criticalCount = anoms.stream().filter(a -> "CRITICAL".equals(a.get("severity"))).count();
        long highCount = anoms.stream().filter(a -> "HIGH".equals(a.get("severity"))).count();
        long medCount = anoms.stream().filter(a -> "MEDIUM".equals(a.get("severity"))).count();

        double ruleComponent = Math.min(100.0, criticalCount * 45.0 + highCount * 25.0 + medCount * 10.0);
        double maxScore = anoms.stream().mapToDouble(a -> (double) a.get("score")).max().orElse(0.0);
        double isoComponent = maxScore * 100.0;
        double statsComponent = Math.min(100.0, anoms.size() * 18.0);

        // Combined risk = 0.45 * rules + 0.30 * isolation + 0.25 * stats
        double riskScore = 0.45 * ruleComponent + 0.30 * isoComponent + 0.25 * statsComponent;
        riskScore = Math.min(100.0, Math.round(riskScore * 10.0) / 10.0);

        String level = "LOW";
        if (riskScore >= 75.0 || criticalCount > 0) level = "CRITICAL";
        else if (riskScore >= 50.0 || highCount > 0) level = "HIGH";
        else if (riskScore >= 25.0) level = "MEDIUM";

        List<String> topReasons = new ArrayList<>();
        for (Map<String, Object> a : anoms) {
            String exp = (String) a.get("explanation");
            if (exp != null && !topReasons.contains(exp)) {
                topReasons.add(exp);
                if (topReasons.size() >= 3) break;
            }
        }
        if (topReasons.isEmpty()) {
            topReasons.add("Clean profile: zero critical discrepancies detected across approved intake and identity verifications.");
        }

        Map<String, Object> comp = Map.of(
                "rules", Math.round(ruleComponent),
                "isolation", Math.round(isoComponent),
                "stats", Math.round(statsComponent)
        );

        jdbc.update("""
            INSERT INTO institution_risk (institution_id, academic_year, risk_score, level, components, top_reasons, computed_at)
            VALUES (?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (institution_id, academic_year)
            DO UPDATE SET risk_score = EXCLUDED.risk_score, level = EXCLUDED.level,
                          components = EXCLUDED.components, top_reasons = EXCLUDED.top_reasons, computed_at = now()
        """, institutionId, academicYear, riskScore, level, Json.write(comp), Json.write(topReasons));
    }

    public List<AnomalyDto> listAnomalies(UUID institutionId, String status, String severity) {
        StringBuilder sql = new StringBuilder("""
            SELECT a.id, a.institution_id, i.aicte_id, i.name as inst_name,
                   p.program_code, a.academic_year, a.kind, a.score, a.severity,
                   a.reasons, a.explanation, a.status, a.review_note, a.reviewed_by, a.created_at
            FROM anomalies a
            JOIN institutions i ON i.id = a.institution_id
            LEFT JOIN programs p ON p.id = a.program_id
            WHERE 1=1
        """);
        List<Object> params = new ArrayList<>();

        UUID inst = TenantScope.resolveInstitutionFilter(institutionId);
        if (inst != null) {
            sql.append(" AND a.institution_id = ? ");
            params.add(inst);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND a.status = ? ");
            params.add(status.trim());
        }
        if (severity != null && !severity.isBlank()) {
            sql.append(" AND a.severity = ? ");
            params.add(severity.trim());
        }

        sql.append(" ORDER BY a.score DESC, a.created_at DESC ");

        return jdbc.query(sql.toString(), (rs, rowNum) -> new AnomalyDto(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("institution_id"),
                rs.getString("aicte_id"),
                rs.getString("inst_name"),
                rs.getString("program_code"),
                rs.getString("academic_year"),
                rs.getString("kind"),
                rs.getDouble("score"),
                rs.getString("severity"),
                Json.readList(rs.getString("reasons")).stream().map(o -> (Map<String, Object>) o).toList(),
                rs.getString("explanation"),
                rs.getString("status"),
                rs.getString("review_note"),
                rs.getString("reviewed_by"),
                rs.getTimestamp("created_at").toInstant().toString()
        ), params.toArray());
    }

    public AnomalyDto getAnomaly(UUID id) {
        return listAnomalies(null, null, null).stream().filter(a -> a.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Anomaly"));
    }

    @Transactional
    public AnomalyDto reviewAnomaly(UUID id, String status, String note) {
        String actor = TenantScope.requireUser().getEmail();
        int rows = jdbc.update("""
            UPDATE anomalies SET status = ?, review_note = ?, reviewed_by = ?, updated_at = now()
            WHERE id = ?
        """, status, note, actor, id);
        if (rows == 0) throw ApiException.notFound("Anomaly");

        AnomalyDto a = getAnomaly(id);
        auditService.record("ANOMALY_REVIEWED", "ANOMALY", id.toString(), a.institutionId(),
                Map.of("status", status, "note", note != null ? note : ""));

        computeInstitutionRisk(a.institutionId(), a.academicYear());
        return getAnomaly(id);
    }

    public RiskDto getInstitutionRisk(UUID institutionId) {
        TenantScope.assertCanAccessInstitution(institutionId);
        return jdbc.query("""
            SELECT institution_id, risk_score, level, components, top_reasons, computed_at
            FROM institution_risk WHERE institution_id = ? AND academic_year = '2026-27'
        """, (rs, rowNum) -> new RiskDto(
                (UUID) rs.getObject("institution_id"),
                rs.getDouble("risk_score"),
                rs.getString("level"),
                Json.readMap(rs.getString("components")),
                Json.readList(rs.getString("top_reasons")).stream().map(Object::toString).toList(),
                rs.getTimestamp("computed_at").toInstant().toString()
        ), institutionId).stream().findFirst().orElseGet(() -> new RiskDto(
                institutionId, 0.0, "LOW",
                Map.of("rules", 0, "isolation", 0, "stats", 0),
                List.of("No anomaly records"), ""
        ));
    }
}
