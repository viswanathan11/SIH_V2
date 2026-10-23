package com.vidyasetu.reporting;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.vidyasetu.analytics.AnalyticsService;
import com.vidyasetu.common.ApiException;
import com.vidyasetu.common.Masking;
import com.vidyasetu.security.TenantScope;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.List;

@Service
public class ReportService {
    private final JdbcTemplate jdbc;
    private final AnalyticsService analyticsService;

    public ReportService(JdbcTemplate jdbc, AnalyticsService analyticsService) {
        this.jdbc = jdbc;
        this.analyticsService = analyticsService;
    }

    public record ReportTypeDto(String type, String name, String description) {}

    public record ReportDto(
            UUID id,
            String type,
            String format,
            String status,
            String createdAt,
            String createdBy,
            int rowCount,
            String downloadUrl
    ) {}

    public List<ReportTypeDto> getReportTypes() {
        return List.of(
                new ReportTypeDto("ENROLMENT_SUMMARY", "National Enrolment Summary", "Aggregated enrolment counts by institution and state"),
                new ReportTypeDto("INTAKE_UTILISATION", "Intake Utilisation Report", "Program-level approved intake vs regular and supernumerary admissions"),
                new ReportTypeDto("EXCESS_REGISTER", "Excess Enrolment Register", "Institutions and programs exceeding approved intake"),
                new ReportTypeDto("SUPERNUMERARY", "Supernumerary Utilization Report", "FN/OCI/PIO, CIWGC, and TFW quotas analysis"),
                new ReportTypeDto("DUPLICATE_REGISTER", "Cross-Institution Duplicate Register", "Active duplicate enrolments detected across institutions"),
                new ReportTypeDto("DATA_QUALITY", "Institution Data Quality Scorecard", "Accepted, flagged, rejected submission quality metrics"),
                new ReportTypeDto("RISK_WATCHLIST", "AICTE Risk Watchlist", "Intelligent anomaly detection and institution risk scoring"),
                new ReportTypeDto("AUDIT_EXPORT", "Audit Trail Export", "Tamper-evident audit chain log entries"),
                new ReportTypeDto("VERIFICATION_ACTIVITY", "Verification Activity Log", "Employer and regulator verification lookup transactions")
        );
    }

    @Transactional
    public ReportDto createReport(String type, String format, Map<String, Object> filters) {
        String actor = TenantScope.requireUser().getEmail();
        UUID instId = TenantScope.resolveInstitutionFilter(null);
        String fmt = format != null ? format.toUpperCase() : "CSV";

        List<List<String>> rows = new ArrayList<>();
        List<String> headers = new ArrayList<>();

        switch (type.toUpperCase()) {
            case "EXCESS_REGISTER" -> {
                headers = List.of("Institution Code", "Institution Name", "Program Code", "Approved Intake", "Regular Enrolled", "Excess Count");
                var list = analyticsService.getExcessRegister();
                for (var m : list) {
                    rows.add(List.of(
                            String.valueOf(m.get("institutionCode")),
                            String.valueOf(m.get("institutionName")),
                            String.valueOf(m.get("programCode")),
                            String.valueOf(m.get("approvedIntake")),
                            String.valueOf(m.get("regular")),
                            String.valueOf(m.get("excess"))
                    ));
                }
            }
            case "INTAKE_UTILISATION" -> {
                headers = List.of("Institution Code", "State", "Approved Intake", "Enrolled", "Fill Ratio (%)");
                var list = analyticsService.getByState();
                for (var m : list) {
                    rows.add(List.of(
                            String.valueOf(m.get("stateCode")),
                            String.valueOf(m.get("state")),
                            String.valueOf(m.get("approvedIntake")),
                            String.valueOf(m.get("enrolled")),
                            String.valueOf(m.get("fillRatio"))
                    ));
                }
            }
            case "RISK_WATCHLIST" -> {
                headers = List.of("Institution Code", "Institution Name", "State", "Risk Score", "Risk Level");
                var list = analyticsService.getTopRiskInstitutions();
                for (var m : list) {
                    rows.add(List.of(
                            String.valueOf(m.get("institutionCode")),
                            String.valueOf(m.get("institutionName")),
                            String.valueOf(m.get("state")),
                            String.valueOf(m.get("riskScore")),
                            String.valueOf(m.get("level"))
                    ));
                }
            }
            default -> {
                headers = List.of("Institution Code", "State", "Approved Intake", "Enrolled", "Fill Ratio (%)");
                var list = analyticsService.getByState();
                for (var m : list) {
                    rows.add(List.of(
                            String.valueOf(m.get("stateCode")),
                            String.valueOf(m.get("state")),
                            String.valueOf(m.get("approvedIntake")),
                            String.valueOf(m.get("enrolled")),
                            String.valueOf(m.get("fillRatio"))
                    ));
                }
            }
        }

        byte[] content;
        if ("XLSX".equalsIgnoreCase(fmt)) {
            content = renderXlsx(type, headers, rows);
        } else if ("PDF".equalsIgnoreCase(fmt)) {
            content = renderPdf(type, headers, rows);
        } else {
            content = renderCsv(headers, rows);
        }

        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO reports (id, type, format, filters, status, row_count, content, created_by, institution_id, created_at)
            VALUES (?, ?, ?, ?, 'COMPLETED', ?, ?, ?, ?, now())
        """, id, type, fmt, "", rows.size(), content, actor, instId);

        return new ReportDto(id, type, fmt, "COMPLETED", Instant.now().toString(), actor, rows.size(), "/api/v1/reports/" + id + "/download");
    }

    private byte[] renderCsv(List<String> headers, List<List<String>> rows) {
        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build())) {
            for (List<String> row : rows) {
                List<String> safe = row.stream().map(Masking::csvSafe).toList();
                printer.printRecord(safe);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] renderXlsx(String sheetName, List<String> headers, List<List<String>> rows) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet(sheetName);
            org.apache.poi.ss.usermodel.Row hRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                org.apache.poi.ss.usermodel.Cell c = hRow.createCell(i);
                c.setCellValue(headers.get(i));
            }
            for (int r = 0; r < rows.size(); r++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r + 1);
                List<String> data = rows.get(r);
                for (int c = 0; c < data.size(); c++) {
                    row.createCell(c).setCellValue(data.get(c));
                }
            }
            wb.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] renderPdf(String title, List<String> headers, List<List<String>> rows) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Paragraph p = new Paragraph("VidyaSetu Report: " + title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16));
            p.setSpacingAfter(14);
            doc.add(p);

            PdfPTable table = new PdfPTable(headers.size());
            table.setWidthPercentage(100);

            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
                cell.setBackgroundColor(new Color(30, 58, 138));
                table.addCell(cell);
            }

            for (List<String> row : rows) {
                for (String val : row) {
                    table.addCell(new Phrase(val, FontFactory.getFont(FontFactory.HELVETICA, 9)));
                }
            }

            doc.add(table);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<ReportDto> listReports() {
        return jdbc.query("""
            SELECT id, type, format, status, created_at, created_by, row_count
            FROM reports ORDER BY created_at DESC LIMIT 50
        """, (rs, rowNum) -> new ReportDto(
                (UUID) rs.getObject("id"),
                rs.getString("type"),
                rs.getString("format"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant().toString(),
                rs.getString("created_by"),
                rs.getInt("row_count"),
                "/api/v1/reports/" + rs.getObject("id") + "/download"
        ));
    }

    public byte[] downloadReport(UUID id) {
        List<byte[]> list = jdbc.query("SELECT content FROM reports WHERE id = ?", (rs, rowNum) -> rs.getBytes("content"), id);
        if (list.isEmpty()) throw ApiException.notFound("Report");
        return list.get(0);
    }
}
