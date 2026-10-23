package com.vidyasetu.ingestion;

import com.vidyasetu.common.ApiException;
import com.vidyasetu.common.PageResponse;
import com.vidyasetu.security.TenantScope;
import com.vidyasetu.security.UserPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class IngestionController {
    private final IngestionService ingestionService;
    private final JdbcTemplate jdbc;

    public IngestionController(IngestionService ingestionService, JdbcTemplate jdbc) {
        this.ingestionService = ingestionService;
        this.jdbc = jdbc;
    }

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('upload:create')")
    public IngestionService.BatchDto uploadFile(@RequestParam("file") MultipartFile file,
                                                @RequestParam(value = "institutionId", required = false) UUID institutionId) {
        UserPrincipal user = TenantScope.requireUser();
        UUID instId = institutionId != null ? institutionId : user.getInstitutionId();
        if (instId == null) {
            throw ApiException.badRequest("institutionId is required");
        }
        TenantScope.assertCanAccessInstitution(instId);

        try {
            byte[] bytes = file.getBytes();
            var batch = ingestionService.createUploadBatch(instId, file.getOriginalFilename(), bytes, "UI", user.getEmail());
            var rawRows = ingestionService.parseFile(file.getOriginalFilename(), bytes);
            // Process synchronously so demo and tests immediately have results
            return ingestionService.processBatch(batch.id(), rawRows);
        } catch (Exception e) {
            if (e instanceof ApiException ae) throw ae;
            throw ApiException.badRequest("Upload processing failed: " + e.getMessage());
        }
    }

    @PostMapping("/students/batch")
    @PreAuthorize("hasAuthority('upload:create')")
    public IngestionService.BatchDto uploadBatchJson(@RequestBody Map<String, Object> body,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        UserPrincipal user = TenantScope.requireUser();
        String aicteId = (String) body.get("institutionAicteId");
        UUID instId = null;
        if (aicteId != null) {
            instId = jdbc.query("SELECT id FROM institutions WHERE aicte_id = ?",
                    rs -> rs.next() ? (UUID) rs.getObject(1) : null, aicteId);
        }
        if (instId == null) instId = user.getInstitutionId();
        if (instId == null) throw ApiException.badRequest("institutionAicteId required");

        TenantScope.assertCanAccessInstitution(instId);

        List<Map<String, String>> rawRows = new ArrayList<>();
        List<Map<String, Object>> records = (List<Map<String, Object>>) body.get("records");
        if (records != null) {
            for (Map<String, Object> rec : records) {
                Map<String, String> m = new HashMap<>();
                rec.forEach((k, v) -> m.put(k, v != null ? v.toString() : null));
                rawRows.add(m);
            }
        }

        byte[] fakeBytes = UUID.randomUUID().toString().getBytes();
        var batch = ingestionService.createUploadBatch(instId, "api-batch.json", fakeBytes, "API", user.getEmail());
        return ingestionService.processBatch(batch.id(), rawRows);
    }

    @GetMapping("/uploads")
    @PreAuthorize("hasAuthority('upload:read')")
    public PageResponse<IngestionService.BatchDto> listBatches(
            @RequestParam(value = "institutionId", required = false) UUID institutionId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ingestionService.listBatches(institutionId, page, size);
    }

    @GetMapping("/uploads/{id}")
    @PreAuthorize("hasAuthority('upload:read')")
    public IngestionService.BatchDto getBatch(@PathVariable UUID id) {
        return ingestionService.getBatch(id);
    }

    @GetMapping("/uploads/{id}/rows")
    @PreAuthorize("hasAuthority('upload:read')")
    public List<IngestionService.StagingRowDto> getBatchRows(@PathVariable UUID id,
                                                            @RequestParam(value = "outcome", required = false) String outcome) {
        return ingestionService.getBatchRows(id, outcome);
    }

    @GetMapping("/uploads/{id}/errors.csv")
    @PreAuthorize("hasAuthority('upload:read')")
    public ResponseEntity<byte[]> downloadErrorReport(@PathVariable UUID id) {
        byte[] csv = ingestionService.generateErrorReportCsv(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"error_report_batch_" + id + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping("/uploads/template.csv")
    public ResponseEntity<byte[]> downloadTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"student_upload_template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(IngestionService.TEMPLATE_CSV.getBytes());
    }

    @PostMapping("/uploads/rows/{rowId}/resubmit")
    @PreAuthorize("hasAuthority('upload:create')")
    public Map<String, Object> resubmitRow(@PathVariable UUID rowId, @RequestBody Map<String, Object> body) {
        Map<String, String> raw = (Map<String, String>) body.get("raw");
        if (raw == null) throw ApiException.badRequest("raw field map required");
        return ingestionService.resubmitRow(rowId, raw);
    }

    @GetMapping("/enrolments")
    @PreAuthorize("hasAuthority('student:read_masked')")
    public PageResponse<IngestionService.EnrolmentDto> listEnrolments(
            @RequestParam(value = "institutionId", required = false) UUID institutionId,
            @RequestParam(value = "programCode", required = false) String programCode,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "seatClass", required = false) String seatClass,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ingestionService.listEnrolments(institutionId, programCode, status, seatClass, q, page, size);
    }

    @PostMapping("/enrolments/{id}/status")
    @PreAuthorize("hasAuthority('student:write') or hasAuthority('validation:waive')")
    public Map<String, Object> updateEnrolmentStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        String note = body.get("note");
        if (status == null || status.isBlank()) throw ApiException.badRequest("status required");
        ingestionService.updateEnrolmentStatus(id, status, note);
        return Map.of("id", id.toString(), "status", status, "updated", true);
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('institution:read') or hasAuthority('student:read_masked')")
    public List<Map<String, Object>> globalSearch(@RequestParam("q") String query) {
        if (query == null || query.trim().length() < 2) return List.of();
        String q = "%" + query.trim().toLowerCase() + "%";
        List<Map<String, Object>> results = new ArrayList<>();

        // Search institutions
        List<Map<String, Object>> insts = jdbc.query("""
            SELECT id, aicte_id, name, state FROM institutions
            WHERE LOWER(aicte_id) LIKE ? OR LOWER(name) LIKE ? LIMIT 5
        """, (rs, rowNum) -> Map.of(
                "type", "INSTITUTION",
                "id", rs.getObject("id").toString(),
                "label", rs.getString("name"),
                "sub", rs.getString("aicte_id") + " • " + rs.getString("state"),
                "link", "/aicte/institutions/" + rs.getObject("id")
        ), q, q);
        results.addAll(insts);

        // Search students
        List<Map<String, Object>> stus = jdbc.query("""
            SELECT s.id, s.apaar_id, s.full_name, i.aicte_id
            FROM students s
            LEFT JOIN enrolments e ON e.student_id = s.id
            LEFT JOIN institutions i ON i.id = e.institution_id
            WHERE s.apaar_id LIKE ? OR LOWER(s.full_name) LIKE ? LIMIT 5
        """, (rs, rowNum) -> Map.of(
                "type", "STUDENT",
                "id", rs.getObject("id").toString(),
                "label", rs.getString("full_name"),
                "sub", "APAAR: " + rs.getString("apaar_id") + (rs.getString("aicte_id") != null ? " • " + rs.getString("aicte_id") : ""),
                "link", "/inst/students?q=" + rs.getString("apaar_id")
        ), q, q);
        results.addAll(stus);

        return results;
    }
}
