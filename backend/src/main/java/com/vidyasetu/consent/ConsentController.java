package com.vidyasetu.consent;

import com.vidyasetu.common.ApiException;
import com.vidyasetu.ingestion.IngestionService;
import com.vidyasetu.security.TenantScope;
import com.vidyasetu.security.UserPrincipal;
import com.vidyasetu.verification.VerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ConsentController {
    private final ConsentService consentService;
    private final IngestionService ingestionService;
    private final VerificationService verificationService;

    public ConsentController(ConsentService consentService, IngestionService ingestionService,
                             VerificationService verificationService) {
        this.consentService = consentService;
        this.ingestionService = ingestionService;
        this.verificationService = verificationService;
    }

    @GetMapping("/consents")
    @PreAuthorize("hasAuthority('consent:manage_own')")
    public List<ConsentService.ConsentDto> listConsents() {
        return consentService.listConsents();
    }

    @PostMapping("/consents")
    @PreAuthorize("hasAuthority('consent:manage_own')")
    public ConsentService.ConsentDto grantConsent(@RequestBody Map<String, Object> body) {
        String granteeOrg = (String) body.get("granteeOrg");
        String scope = (String) body.get("scope");
        String purpose = (String) body.get("purpose");
        int days = body.get("days") != null ? ((Number) body.get("days")).intValue() : 30;
        return consentService.grantConsent(granteeOrg, scope, purpose, days);
    }

    @DeleteMapping("/consents/{id}")
    @PreAuthorize("hasAuthority('consent:manage_own')")
    public ResponseEntity<Void> revokeConsent(@PathVariable UUID id) {
        consentService.revokeConsent(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/consent-requests")
    public List<ConsentService.ConsentRequestDto> listConsentRequests() {
        return consentService.listConsentRequests();
    }

    @PostMapping("/consent-requests/{id}/approve")
    @PreAuthorize("hasAuthority('consent:manage_own')")
    public Map<String, Object> approveConsentRequest(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        int days = body != null && body.get("days") != null ? ((Number) body.get("days")).intValue() : 30;
        consentService.decideConsentRequest(id, true, days);
        return Map.of("id", id.toString(), "status", "APPROVED");
    }

    @PostMapping("/consent-requests/{id}/deny")
    @PreAuthorize("hasAuthority('consent:manage_own')")
    public Map<String, Object> denyConsentRequest(@PathVariable UUID id) {
        consentService.decideConsentRequest(id, false, 0);
        return Map.of("id", id.toString(), "status", "DENIED");
    }

    @GetMapping("/me/record")
    @PreAuthorize("hasAuthority('record:read_own')")
    public Map<String, Object> getMyRecord() {
        UserPrincipal user = TenantScope.requireUser();
        if (user.getStudentId() == null) throw ApiException.notFound("Student record");
        var enrolments = ingestionService.listEnrolments(null, null, null, null, user.getUsername().replace("@student.vidyasetu.local", ""), 0, 10).content();
        List<VerificationService.IssuedRecordDto> recs = enrolments.isEmpty() ? List.of() :
                verificationService.getRecordsByEnrolment(enrolments.get(0).id());
        return Map.of("student", user, "enrolments", enrolments, "records", recs);
    }

    @GetMapping("/me/access-history")
    @PreAuthorize("hasAuthority('record:read_own')")
    public List<Map<String, Object>> getMyAccessHistory() {
        return consentService.getStudentAccessHistory();
    }
}
