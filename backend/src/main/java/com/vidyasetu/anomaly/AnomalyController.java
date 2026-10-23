package com.vidyasetu.anomaly;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AnomalyController {
    private final AnomalyService anomalyService;

    public AnomalyController(AnomalyService anomalyService) {
        this.anomalyService = anomalyService;
    }

    @GetMapping("/anomalies")
    @PreAuthorize("hasAuthority('anomaly:read')")
    public List<AnomalyService.AnomalyDto> listAnomalies(
            @RequestParam(value = "institutionId", required = false) UUID institutionId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "severity", required = false) String severity) {
        return anomalyService.listAnomalies(institutionId, status, severity);
    }

    @GetMapping("/anomalies/{id}")
    @PreAuthorize("hasAuthority('anomaly:read')")
    public AnomalyService.AnomalyDto getAnomaly(@PathVariable UUID id) {
        return anomalyService.getAnomaly(id);
    }

    @PostMapping("/anomalies/{id}/review")
    @PreAuthorize("hasAuthority('anomaly:review')")
    public AnomalyService.AnomalyDto reviewAnomaly(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        String note = body.get("note");
        return anomalyService.reviewAnomaly(id, status, note);
    }

    @PostMapping("/anomalies/recompute")
    @PreAuthorize("hasAuthority('anomaly:review')")
    public Map<String, Object> recomputeAnomalies() {
        int count = anomalyService.recomputeAnomalies();
        return Map.of("anomalies", count, "status", "SUCCESS");
    }

    @GetMapping("/institutions/{id}/risk")
    @PreAuthorize("hasAuthority('institution:read')")
    public AnomalyService.RiskDto getInstitutionRisk(@PathVariable UUID id) {
        return anomalyService.getInstitutionRisk(id);
    }
}
