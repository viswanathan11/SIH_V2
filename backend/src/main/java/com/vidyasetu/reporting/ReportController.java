package com.vidyasetu.reporting;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/types")
    @PreAuthorize("hasAuthority('report:generate')")
    public List<ReportService.ReportTypeDto> getReportTypes() {
        return reportService.getReportTypes();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('report:generate')")
    public ReportService.ReportDto generateReport(@RequestBody Map<String, Object> body) {
        String type = (String) body.get("type");
        String format = (String) body.get("format");
        Map<String, Object> filters = (Map<String, Object>) body.get("filters");
        return reportService.createReport(type, format, filters);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('report:generate')")
    public List<ReportService.ReportDto> listReports() {
        return reportService.listReports();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable UUID id) {
        byte[] content = reportService.downloadReport(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vidyasetu_report_" + id + ".dat\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }
}
