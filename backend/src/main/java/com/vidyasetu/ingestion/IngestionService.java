package com.vidyasetu.ingestion;

import com.vidyasetu.anomaly.AnomalyService;
import com.vidyasetu.audit.AuditService;
import com.vidyasetu.common.ApiException;
import com.vidyasetu.common.Json;
import com.vidyasetu.common.Masking;
import com.vidyasetu.common.PageResponse;
import com.vidyasetu.masterdata.MasterDataService;
import com.vidyasetu.realtime.EventBusService;
import com.vidyasetu.security.CryptoService;
import com.vidyasetu.security.TenantScope;
import com.vidyasetu.security.UserPrincipal;
import com.vidyasetu.validation.ValidationEngine;
import com.vidyasetu.validation.ValidationIssue;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class IngestionService {
    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final JdbcTemplate jdbc;
    private final ValidationEngine validationEngine;
    private final AnomalyService anomalyService;
    private final EventBusService eventBusService;
    private final AuditService auditService;
    private final CryptoService cryptoService;

    public IngestionService(JdbcTemplate jdbc, ValidationEngine validationEngine,
                            AnomalyService anomalyService, EventBusService eventBusService,
                            AuditService auditService, CryptoService cryptoService) {
        this.jdbc = jdbc;
        this.validationEngine = validationEngine;
        this.anomalyService = anomalyService;
        this.eventBusService = eventBusService;
        this.auditService = auditService;
        this.cryptoService = cryptoService;
    }

    public record BatchDto(
            UUID id,
            UUID institutionId,
            String institutionCode,
            String institutionName,
            String source,
            String fileName,
            String status,
            int totalRows,
            int processedRows,
            int acceptedRows,
            int flaggedRows,
            int rejectedRows,
            String createdAt,
            String completedAt,
            String uploadedBy,
            List<Map<String, Object>> issueSummary
    ) {}

    public record StagingRowDto(
            UUID id,
            UUID batchId,
            int rowNo,
            Map<String, String> raw,
            String outcome,
            List<ValidationIssue> issues
    ) {}

    public record EnrolmentDto(
            UUID id,
            UUID studentId,
            String apaarId,
            String apaarMasked,
            String fullName,
            String dob,
            String gender,
            String instituteStudentId,
            UUID institutionId,
            String institutionCode,
            String institutionName,
            UUID programId,
            String programCode,
            String programName,
            String academicYear,
            String entryType,
            String residencyStatus,
            String admissionQuota,
            String socialCategory,
            String nationality,
            String status,
            String seatClass,
            String admissionDate,
            int version
    ) {}

    public static final String TEMPLATE_CSV =
            "apaar_id,institute_student_id,full_name,dob,gender,nationality,residency_status,admission_quota,social_category,program_code,entry_type,admission_date,academic_year,passport_no,oci_card_no,mobile,email\n" +
            "910000000101,CIT26CS0101,Asha Raman,2008-03-14,F,IN,RESIDENT_INDIAN,STATE_CAP,OBC,BTECH-CSE,FIRST_YEAR,2026-08-12,2026-27,,,9876543210,asha.r@example.com\n";

    public BatchDto createUploadBatch(UUID institutionId, String fileName, byte[] content, String source, String uploader) {
        TenantScope.assertCanAccessInstitution(institutionId);

        String sha256 = cryptoService.sha256Hex(new String(Base64.getEncoder().encode(content)));

        // Guard exact re-submission check
        List<String> dupBatches = jdbc.query("SELECT id FROM upload_batches WHERE institution_id = ? AND sha256 = ? AND status = 'COMPLETED'",
                (rs, rowNum) -> rs.getString("id"), institutionId, sha256);
        if (!dupBatches.isEmpty()) {
            throw ApiException.conflict("This exact file has already been uploaded and processed (Batch ID: " + dupBatches.get(0) + ")");
        }

        UUID batchId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO upload_batches (id, institution_id, uploaded_by, source, file_name, sha256, status, total_rows, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 'QUEUED', 0, now())
        """, batchId, institutionId, uploader, source, fileName, sha256);

        eventBusService.publish("UPLOAD_RECEIVED", institutionId, null, "INFO",
                "Upload batch initiated: " + fileName, Map.of("batchId", batchId.toString(), "fileName", fileName));

        return getBatch(batchId);
    }

    public List<Map<String, String>> parseFile(String fileName, byte[] bytes) {
        String fn = fileName != null ? fileName.toLowerCase() : "";
        if (fn.endsWith(".xlsx")) {
            return parseXlsx(bytes);
        } else {
            return parseCsv(bytes);
        }
    }

    private List<Map<String, String>> parseCsv(byte[] bytes) {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord record : parser) {
                Map<String, String> map = new LinkedHashMap<>();
                for (String header : parser.getHeaderNames()) {
                    map.put(header.toLowerCase().trim(), record.get(header));
                }
                rows.add(map);
            }
        } catch (Exception e) {
            throw ApiException.badRequest("CSV parsing failed: " + e.getMessage());
        }
        return rows;
    }

    private List<Map<String, String>> parseXlsx(byte[] bytes) {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return rows;

            List<String> headers = new ArrayList<>();
            for (Cell c : headerRow) {
                headers.add(c.getStringCellValue().toLowerCase().trim());
            }

            DataFormatter formatter = new DataFormatter();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> map = new LinkedHashMap<>();
                boolean allEmpty = true;
                for (int col = 0; col < headers.size(); col++) {
                    Cell c = row.getCell(col);
                    String v = formatter.formatCellValue(c).trim();
                    if (!v.isEmpty()) allEmpty = false;
                    map.put(headers.get(col), v);
                }
                if (!allEmpty) rows.add(map);
            }
        } catch (Exception e) {
            throw ApiException.badRequest("XLSX parsing failed: " + e.getMessage());
        }
        return rows;
    }

    /** Asynchronous or synchronous execution of batch processing */
    @Transactional
    public BatchDto processBatch(UUID batchId, List<Map<String, String>> rawRows) {
        UUID instId = jdbc.queryForObject("SELECT institution_id FROM upload_batches WHERE id = ?", UUID.class, batchId);
        String instCode = jdbc.queryForObject("SELECT aicte_id FROM institutions WHERE id = ?", String.class, instId);

        jdbc.update("UPDATE upload_batches SET status = 'PROCESSING', total_rows = ? WHERE id = ?", rawRows.size(), batchId);

        List<ValidationEngine.NormalizedRow> parsed = new ArrayList<>();
        for (int i = 0; i < rawRows.size(); i++) {
            parsed.add(validationEngine.parseRaw(i + 1, rawRows.get(i)));
        }

        // 1. Group A, B, C checks
        List<ValidationEngine.NormalizedRow> validated = validationEngine.validateRows(instId, parsed);

        // 2. Group D batch-level seat caps
        List<ValidationEngine.NormalizedRow> finalRows = validationEngine.validateIntakeCaps(instId, validated);

        int accepted = 0;
        int flagged = 0;
        int rejected = 0;

        for (ValidationEngine.NormalizedRow r : finalRows) {
            UUID stagingRowId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO staging_rows (id, batch_id, row_no, raw, normalized, outcome)
                VALUES (?, ?, ?, ?, ?, ?)
            """, stagingRowId, batchId, r.rowNo(), Json.write(r.raw()), Json.write(r), r.outcome());

            UUID enrolmentId = null;

            if ("ACCEPTED".equals(r.outcome()) || "FLAGGED".equals(r.outcome())) {
                enrolmentId = upsertEnrolment(instId, batchId, r);
                if ("ACCEPTED".equals(r.outcome())) accepted++;
                else flagged++;
            } else {
                rejected++;
            }

            // Save issues
            for (ValidationIssue issue : r.issues()) {
                jdbc.update("""
                    INSERT INTO validation_issues (id, batch_id, staging_row_id, enrolment_id, institution_id,
                                                   rule_code, severity, field, message, hint, context,
                                                   apaar_masked, row_no, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', now())
                """, UUID.randomUUID(), batchId, stagingRowId, enrolmentId, instId,
                        issue.ruleCode(), issue.severity(), issue.field(), issue.message(), issue.hint(),
                        Json.write(issue.context()), r.apaarMasked(), r.rowNo());
            }
        }

        jdbc.update("""
            UPDATE upload_batches SET status = 'COMPLETED', processed_rows = ?, accepted_rows = ?,
                                      flagged_rows = ?, rejected_rows = ?, completed_at = now()
            WHERE id = ?
        """, finalRows.size(), accepted, flagged, rejected, batchId);

        // Refresh anomalies and risk for institution
        anomalyService.recomputeAnomalies();

        auditService.record("BATCH_PROCESSED", "UPLOAD_BATCH", batchId.toString(), instId,
                Map.of("total", finalRows.size(), "accepted", accepted, "flagged", flagged, "rejected", rejected));

        eventBusService.publish("VALIDATION_COMPLETED", instId, instCode, flagged > 0 ? "WARN" : "INFO",
                "Batch processing completed for " + instCode + ": " + accepted + " accepted, " + flagged + " flagged, " + rejected + " rejected",
                Map.of("batchId", batchId.toString(), "accepted", accepted, "flagged", flagged, "rejected", rejected));

        return getBatch(batchId);
    }

    private UUID upsertEnrolment(UUID instId, UUID batchId, ValidationEngine.NormalizedRow r) {
        // Upsert student
        UUID studentId = jdbc.query("SELECT id FROM students WHERE apaar_id = ?",
                rs -> rs.next() ? (UUID) rs.getObject(1) : null, r.apaarId());
        if (studentId == null) {
            studentId = UUID.randomUUID();
            String idKey = cryptoService.hmacSha256Hex("vidyasetu-id-secret", r.nameNorm() + "|" + r.dob() + "|" + r.gender());
            jdbc.update("""
                INSERT INTO students (id, apaar_id, full_name, name_norm, dob, gender, identity_key, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now())
            """, studentId, r.apaarId(), r.fullName(), r.nameNorm(), java.sql.Date.valueOf(r.dob()), r.gender(), idKey);
        }

        UUID progId = jdbc.queryForObject("SELECT id FROM programs WHERE institution_id = ? AND program_code = ? AND academic_year = ?",
                UUID.class, instId, r.programCode(), r.academicYear());

        // Check if enrolment exists for student + program + year
        List<UUID> existing = jdbc.query("""
            SELECT id, version FROM enrolments WHERE student_id = ? AND program_id = ? AND academic_year = ?
        """, (rs, rowNum) -> (UUID) rs.getObject("id"), studentId, progId, r.academicYear());

        String passEnc = cryptoService.encrypt(r.passportNo());
        String ociEnc = cryptoService.encrypt(r.ociCardNo());
        String mobileEnc = cryptoService.encrypt(r.mobile());
        String emailEnc = cryptoService.encrypt(r.email());

        String enrolStatus = "ACCEPTED".equals(r.outcome()) ? "VALIDATED" : "FLAGGED";

        if (existing.isEmpty()) {
            UUID enrolId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO enrolments (id, student_id, institution_id, program_id, academic_year,
                                        institute_student_id, entry_type, residency_status, admission_quota,
                                        social_category, nationality, passport_no_enc, oci_card_no_enc,
                                        mobile_enc, email_enc, admission_date, status, seat_class,
                                        apaar_verified, source_batch_id, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, now(), now())
            """, enrolId, studentId, instId, progId, r.academicYear(),
                    r.instituteStudentId(), r.entryType(), r.residencyStatus(), r.admissionQuota(),
                    r.socialCategory(), r.nationality(), passEnc, ociEnc, mobileEnc, emailEnc,
                    r.admissionDate() != null ? java.sql.Date.valueOf(r.admissionDate()) : null,
                    enrolStatus, r.seatClass(), r.apaarVerified(), batchId);
            return enrolId;
        } else {
            UUID enrolId = existing.get(0);
            jdbc.update("""
                UPDATE enrolments SET institute_student_id = ?, entry_type = ?, residency_status = ?,
                                      admission_quota = ?, social_category = ?, nationality = ?,
                                      passport_no_enc = ?, oci_card_no_enc = ?, mobile_enc = ?,
                                      email_enc = ?, admission_date = ?, status = ?, seat_class = ?,
                                      apaar_verified = ?, source_batch_id = ?, version = version + 1, updated_at = now()
                WHERE id = ?
            """, r.instituteStudentId(), r.entryType(), r.residencyStatus(), r.admissionQuota(),
                    r.socialCategory(), r.nationality(), passEnc, ociEnc, mobileEnc, emailEnc,
                    r.admissionDate() != null ? java.sql.Date.valueOf(r.admissionDate()) : null,
                    enrolStatus, r.seatClass(), r.apaarVerified(), batchId, enrolId);

            jdbc.update("""
                INSERT INTO enrolment_history (id, enrolment_id, version, diff, changed_by, changed_at)
                VALUES (?, ?, (SELECT version FROM enrolments WHERE id = ?), ?, ?, now())
            """, UUID.randomUUID(), enrolId, enrolId, Json.write(r.raw()), "batch_update");

            return enrolId;
        }
    }

    public BatchDto getBatch(UUID id) {
        List<BatchDto> list = jdbc.query("""
            SELECT b.id, b.institution_id, i.aicte_id, i.name as inst_name,
                   b.source, b.file_name, b.status, b.total_rows, b.processed_rows,
                   b.accepted_rows, b.flagged_rows, b.rejected_rows, b.created_at, b.completed_at, b.uploaded_by
            FROM upload_batches b
            JOIN institutions i ON i.id = b.institution_id
            WHERE b.id = ?
        """, (rs, rowNum) -> new BatchDto(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("institution_id"),
                rs.getString("aicte_id"),
                rs.getString("inst_name"),
                rs.getString("source"),
                rs.getString("file_name"),
                rs.getString("status"),
                rs.getInt("total_rows"),
                rs.getInt("processed_rows"),
                rs.getInt("accepted_rows"),
                rs.getInt("flagged_rows"),
                rs.getInt("rejected_rows"),
                rs.getTimestamp("created_at").toInstant().toString(),
                rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant().toString() : null,
                rs.getString("uploaded_by"),
                Collections.emptyList()
        ), id);

        if (list.isEmpty()) throw ApiException.notFound("Batch");
        BatchDto b = list.get(0);
        TenantScope.assertCanAccessInstitution(b.institutionId());

        List<Map<String, Object>> summary = jdbc.query("""
            SELECT rule_code, severity, count(*) as cnt
            FROM validation_issues WHERE batch_id = ? GROUP BY rule_code, severity
        """, (rs, rowNum) -> Map.of(
                "ruleCode", rs.getString("rule_code"),
                "severity", rs.getString("severity"),
                "count", rs.getInt("cnt")
        ), id);

        return new BatchDto(b.id(), b.institutionId(), b.institutionCode(), b.institutionName(),
                b.source(), b.fileName(), b.status(), b.totalRows(), b.processedRows(),
                b.acceptedRows(), b.flaggedRows(), b.rejectedRows(), b.createdAt(), b.completedAt(),
                b.uploadedBy(), summary);
    }

    public PageResponse<BatchDto> listBatches(UUID institutionId, int page, int size) {
        UUID inst = TenantScope.resolveInstitutionFilter(institutionId);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT b.id, b.institution_id, i.aicte_id, i.name as inst_name,
                   b.source, b.file_name, b.status, b.total_rows, b.processed_rows,
                   b.accepted_rows, b.flagged_rows, b.rejected_rows, b.created_at, b.completed_at, b.uploaded_by
            FROM upload_batches b
            JOIN institutions i ON i.id = b.institution_id
            WHERE 1=1
        """);
        if (inst != null) {
            sql.append(" AND b.institution_id = ? ");
            params.add(inst);
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM (" + sql + ") t", Long.class, params.toArray());

        sql.append(" ORDER BY b.created_at DESC LIMIT ? OFFSET ? ");
        params.add(size);
        params.add(page * size);

        List<BatchDto> list = jdbc.query(sql.toString(), (rs, rowNum) -> new BatchDto(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("institution_id"),
                rs.getString("aicte_id"),
                rs.getString("inst_name"),
                rs.getString("source"),
                rs.getString("file_name"),
                rs.getString("status"),
                rs.getInt("total_rows"),
                rs.getInt("processed_rows"),
                rs.getInt("accepted_rows"),
                rs.getInt("flagged_rows"),
                rs.getInt("rejected_rows"),
                rs.getTimestamp("created_at").toInstant().toString(),
                rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant().toString() : null,
                rs.getString("uploaded_by"),
                Collections.emptyList()
        ), params.toArray());

        return PageResponse.of(list, page, size, total != null ? total : 0);
    }

    public List<StagingRowDto> getBatchRows(UUID batchId, String outcome) {
        BatchDto b = getBatch(batchId);
        TenantScope.assertCanAccessInstitution(b.institutionId());

        StringBuilder sql = new StringBuilder("SELECT id, batch_id, row_no, raw, outcome FROM staging_rows WHERE batch_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(batchId);
        if (outcome != null && !outcome.isBlank()) {
            sql.append(" AND outcome = ? ");
            params.add(outcome.trim());
        }
        sql.append(" ORDER BY row_no ASC ");

        return jdbc.query(sql.toString(), (rs, rowNum) -> {
            UUID sId = (UUID) rs.getObject("id");
            List<ValidationIssue> issues = jdbc.query("""
                SELECT id, batch_id, staging_row_id, enrolment_id, institution_id, rule_code, severity, field, message, hint, context, apaar_masked, row_no, status, resolved_by, resolution_note
                FROM validation_issues WHERE staging_row_id = ?
            """, (irs, inum) -> new ValidationIssue(
                    (UUID) irs.getObject("id"),
                    (UUID) irs.getObject("batch_id"),
                    (UUID) irs.getObject("staging_row_id"),
                    (UUID) irs.getObject("enrolment_id"),
                    (UUID) irs.getObject("institution_id"),
                    null,
                    irs.getString("rule_code"),
                    irs.getString("severity"),
                    irs.getString("field"),
                    irs.getString("message"),
                    irs.getString("hint"),
                    Json.readMap(irs.getString("context")),
                    irs.getString("apaar_masked"),
                    irs.getInt("row_no"),
                    irs.getString("status"),
                    irs.getString("resolved_by"),
                    irs.getString("resolution_note")
            ), sId);

            return new StagingRowDto(
                    sId,
                    (UUID) rs.getObject("batch_id"),
                    rs.getInt("row_no"),
                    Json.readStringMap(rs.getString("raw")),
                    rs.getString("outcome"),
                    issues
            );
        }, params.toArray());
    }

    public byte[] generateErrorReportCsv(UUID batchId) {
        List<StagingRowDto> rows = getBatchRows(batchId, null);
        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setHeader(
                "row_no", "outcome", "rule_code", "severity", "field", "message", "hint",
                "apaar_id", "institute_student_id", "full_name", "program_code"
        ).build())) {
            for (StagingRowDto r : rows) {
                if (r.issues().isEmpty()) {
                    printer.printRecord(
                            r.rowNo(), r.outcome(), "", "INFO", "", "Row accepted", "",
                            Masking.csvSafe(r.raw().get("apaar_id")),
                            Masking.csvSafe(r.raw().get("institute_student_id")),
                            Masking.csvSafe(r.raw().get("full_name")),
                            Masking.csvSafe(r.raw().get("program_code"))
                    );
                } else {
                    for (ValidationIssue iss : r.issues()) {
                        printer.printRecord(
                                r.rowNo(), r.outcome(), iss.ruleCode(), iss.severity(), iss.field(),
                                iss.message(), iss.hint(),
                                Masking.csvSafe(r.raw().get("apaar_id")),
                                Masking.csvSafe(r.raw().get("institute_student_id")),
                                Masking.csvSafe(r.raw().get("full_name")),
                                Masking.csvSafe(r.raw().get("program_code"))
                        );
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error report generation failed", e);
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public Map<String, Object> resubmitRow(UUID stagingRowId, Map<String, String> updatedRaw) {
        Map<String, Object> sRow = jdbc.queryForMap("SELECT id, batch_id, row_no FROM staging_rows WHERE id = ?", stagingRowId);
        UUID batchId = (UUID) sRow.get("batch_id");
        int rowNo = (int) sRow.get("row_no");

        BatchDto batch = getBatch(batchId);
        TenantScope.assertCanAccessInstitution(batch.institutionId());

        ValidationEngine.NormalizedRow parsed = validationEngine.parseRaw(rowNo, updatedRaw);
        List<ValidationEngine.NormalizedRow> val = validationEngine.validateRows(batch.institutionId(), List.of(parsed));
        List<ValidationEngine.NormalizedRow> finalRows = validationEngine.validateIntakeCaps(batch.institutionId(), val);
        ValidationEngine.NormalizedRow res = finalRows.get(0);

        // Update staging row
        jdbc.update("UPDATE staging_rows SET raw = ?, normalized = ?, outcome = ? WHERE id = ?",
                Json.write(updatedRaw), Json.write(res), res.outcome(), stagingRowId);

        // Delete old open issues for this staging row
        jdbc.update("DELETE FROM validation_issues WHERE staging_row_id = ? AND status = 'OPEN'", stagingRowId);

        UUID enrolmentId = null;
        if ("ACCEPTED".equals(res.outcome()) || "FLAGGED".equals(res.outcome())) {
            enrolmentId = upsertEnrolment(batch.institutionId(), batchId, res);
        }

        for (ValidationIssue iss : res.issues()) {
            jdbc.update("""
                INSERT INTO validation_issues (id, batch_id, staging_row_id, enrolment_id, institution_id,
                                               rule_code, severity, field, message, hint, context,
                                               apaar_masked, row_no, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', now())
            """, UUID.randomUUID(), batchId, stagingRowId, enrolmentId, batch.institutionId(),
                    iss.ruleCode(), iss.severity(), iss.field(), iss.message(), iss.hint(),
                    Json.write(iss.context()), res.apaarMasked(), rowNo);
        }

        auditService.record("ROW_RESUBMITTED", "STAGING_ROW", stagingRowId.toString(), batch.institutionId(),
                Map.of("outcome", res.outcome(), "rowNo", rowNo));

        return Map.of("stagingRowId", stagingRowId, "outcome", res.outcome(), "issues", res.issues());
    }

    public PageResponse<EnrolmentDto> listEnrolments(UUID institutionId, String programCode, String status,
                                                     String seatClass, String q, int page, int size) {
        UUID inst = TenantScope.resolveInstitutionFilter(institutionId);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT e.id, e.student_id, s.apaar_id, s.full_name, s.dob, s.gender,
                   e.institute_student_id, e.institution_id, i.aicte_id, i.name as inst_name,
                   e.program_id, p.program_code, p.name as prog_name, e.academic_year,
                   e.entry_type, e.residency_status, e.admission_quota, e.social_category,
                   e.nationality, e.status, e.seat_class, e.admission_date, e.version
            FROM enrolments e
            JOIN students s ON s.id = e.student_id
            JOIN institutions i ON i.id = e.institution_id
            JOIN programs p ON p.id = e.program_id
            WHERE 1=1
        """);

        if (inst != null) {
            sql.append(" AND e.institution_id = ? ");
            params.add(inst);
        }
        if (programCode != null && !programCode.isBlank()) {
            sql.append(" AND p.program_code = ? ");
            params.add(programCode.trim().toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND e.status = ? ");
            params.add(status.trim());
        }
        if (seatClass != null && !seatClass.isBlank()) {
            sql.append(" AND e.seat_class = ? ");
            params.add(seatClass.trim());
        }
        if (q != null && !q.isBlank()) {
            String term = "%" + q.trim().toLowerCase() + "%";
            sql.append(" AND (s.apaar_id LIKE ? OR LOWER(s.full_name) LIKE ? OR LOWER(e.institute_student_id) LIKE ?) ");
            params.add(term);
            params.add(term);
            params.add(term);
        }

        Long total = jdbc.queryForObject("SELECT count(*) FROM (" + sql + ") t", Long.class, params.toArray());

        sql.append(" ORDER BY e.created_at DESC LIMIT ? OFFSET ? ");
        params.add(size);
        params.add(page * size);

        UserPrincipal user = TenantScope.currentUser();
        boolean fullPiiPermitted = user != null && user.hasPermission("student:read_full");

        List<EnrolmentDto> list = jdbc.query(sql.toString(), (rs, rowNum) -> {
            String apaar = rs.getString("apaar_id");
            String maskedApaar = Masking.maskApaar(apaar);
            String shownApaar = fullPiiPermitted ? Masking.formatApaar(apaar) : maskedApaar;
            String name = fullPiiPermitted ? rs.getString("full_name") : rs.getString("full_name"); // Name shown, DOB masked if needed
            LocalDate dob = rs.getDate("dob") != null ? rs.getDate("dob").toLocalDate() : null;

            return new EnrolmentDto(
                    (UUID) rs.getObject("id"),
                    (UUID) rs.getObject("student_id"),
                    shownApaar,
                    maskedApaar,
                    name,
                    dob != null ? dob.toString() : null,
                    rs.getString("gender"),
                    rs.getString("institute_student_id"),
                    (UUID) rs.getObject("institution_id"),
                    rs.getString("aicte_id"),
                    rs.getString("inst_name"),
                    (UUID) rs.getObject("program_id"),
                    rs.getString("program_code"),
                    rs.getString("prog_name"),
                    rs.getString("academic_year"),
                    rs.getString("entry_type"),
                    rs.getString("residency_status"),
                    rs.getString("admission_quota"),
                    rs.getString("social_category"),
                    rs.getString("nationality"),
                    rs.getString("status"),
                    rs.getString("seat_class"),
                    rs.getDate("admission_date") != null ? rs.getDate("admission_date").toString() : null,
                    rs.getInt("version")
            );
        }, params.toArray());

        return PageResponse.of(list, page, size, total != null ? total : 0);
    }

    @Transactional
    public void updateEnrolmentStatus(UUID enrolmentId, String status, String note) {
        Map<String, Object> enrol = jdbc.queryForMap("SELECT institution_id, status FROM enrolments WHERE id = ?", enrolmentId);
        UUID instId = (UUID) enrol.get("institution_id");
        TenantScope.assertCanAccessInstitution(instId);

        String oldStatus = (String) enrol.get("status");
        jdbc.update("UPDATE enrolments SET status = ?, updated_at = now() WHERE id = ?", status, enrolmentId);

        // Also if resolving as TRANSFERRED or WITHDRAWN, resolve open issues for this enrolment
        if ("TRANSFERRED".equalsIgnoreCase(status) || "WITHDRAWN".equalsIgnoreCase(status)) {
            String actor = TenantScope.requireUser().getEmail();
            jdbc.update("""
                UPDATE validation_issues SET status = 'RESOLVED', resolved_by = ?, resolution_note = ?, created_at = now()
                WHERE enrolment_id = ? AND status = 'OPEN'
            """, actor, "Resolved via status change to " + status + ": " + (note != null ? note : ""), enrolmentId);
        }

        auditService.record("ENROLMENT_STATUS_CHANGED", "ENROLMENT", enrolmentId.toString(), instId,
                Map.of("oldStatus", oldStatus, "newStatus", status, "note", note != null ? note : ""));
    }
}
