package com.vidyasetu.analytics;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/national/summary")
    @PreAuthorize("hasAuthority('dashboard:national')")
    public Map<String, Object> getNationalSummary() {
        return analyticsService.getNationalSummary();
    }

    @GetMapping("/national/by-state")
    @PreAuthorize("hasAuthority('dashboard:national')")
    public List<Map<String, Object>> getByState() {
        return analyticsService.getByState();
    }

    @GetMapping("/national/by-level")
    @PreAuthorize("hasAuthority('dashboard:national')")
    public List<Map<String, Object>> getByLevel() {
        return analyticsService.getByLevel();
    }

    @GetMapping("/national/trend")
    @PreAuthorize("hasAuthority('dashboard:national')")
    public List<Map<String, Object>> get5YearTrend() {
        return analyticsService.get5YearTrend();
    }

    @GetMapping("/national/supernumerary")
    @PreAuthorize("hasAuthority('dashboard:national')")
    public List<Map<String, Object>> getSupernumerary() {
        return analyticsService.getSupernumeraryBreakdown();
    }

    @GetMapping("/national/issues-by-rule")
    @PreAuthorize("hasAuthority('dashboard:national')")
    public List<Map<String, Object>> getIssuesByRule() {
        return analyticsService.getIssuesByRule();
    }

    @GetMapping("/national/top-risk")
    @PreAuthorize("hasAuthority('dashboard:national')")
    public List<Map<String, Object>> getTopRiskInstitutions() {
        return analyticsService.getTopRiskInstitutions();
    }

    @GetMapping("/excess-register")
    @PreAuthorize("hasAuthority('dashboard:national')")
    public List<Map<String, Object>> getExcessRegister() {
        return analyticsService.getExcessRegister();
    }

    @GetMapping("/institution/{id}/meters")
    @PreAuthorize("hasAuthority('dashboard:institution')")
    public List<Map<String, Object>> getInstitutionMeters(@PathVariable UUID id) {
        return analyticsService.getInstitutionMeters(id);
    }

    @GetMapping("/institution/{id}/quality")
    @PreAuthorize("hasAuthority('dashboard:institution')")
    public Map<String, Object> getInstitutionQuality(@PathVariable UUID id) {
        return analyticsService.getInstitutionQuality(id);
    }
}
