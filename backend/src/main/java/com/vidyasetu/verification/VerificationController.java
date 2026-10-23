package com.vidyasetu.verification;

import com.vidyasetu.common.ApiException;
import com.vidyasetu.security.JwtService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class VerificationController {
    private final VerificationService verificationService;
    private final JwtService jwtService;

    public VerificationController(VerificationService verificationService, JwtService jwtService) {
        this.verificationService = verificationService;
        this.jwtService = jwtService;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> getJwks() {
        return jwtService.getJwksMap();
    }

    @PostMapping("/api/v1/records/issue")
    @PreAuthorize("hasAuthority('record:issue')")
    public VerificationService.IssuedRecordDto issueRecord(@RequestBody Map<String, String> body) {
        String enrolmentId = body.get("enrolmentId");
        if (enrolmentId == null) throw ApiException.badRequest("enrolmentId is required");
        return verificationService.issueRecord(UUID.fromString(enrolmentId));
    }

    @PostMapping("/api/v1/records/{jti}/revoke")
    @PreAuthorize("hasAuthority('record:revoke')")
    public VerificationService.IssuedRecordDto revokeRecord(@PathVariable String jti, @RequestBody Map<String, String> body) {
        return verificationService.revokeRecord(jti, body.get("reason"));
    }

    @GetMapping("/api/v1/records")
    @PreAuthorize("hasAuthority('student:read_masked') or hasAuthority('record:read_own')")
    public List<VerificationService.IssuedRecordDto> getRecordsByEnrolment(@RequestParam("enrolmentId") UUID enrolmentId) {
        return verificationService.getRecordsByEnrolment(enrolmentId);
    }

    @GetMapping("/api/v1/verify/{jti}")
    public Map<String, Object> verifyPublicJti(@PathVariable String jti) {
        return verificationService.verifyPublicJti(jti);
    }

    @PostMapping("/api/v1/verify/jws")
    public Map<String, Object> verifyPublicJws(@RequestBody Map<String, String> body) {
        String jws = body.get("jws");
        if (jws == null) throw ApiException.badRequest("jws is required");
        var claims = jwtService.verifyVerifiableRecord(jws);
        if (claims == null) return Map.of("status", "INVALID_SIGNATURE", "signatureValid", false);
        return verificationService.verifyPublicJti(claims.getJWTID());
    }

    @PostMapping("/api/v1/verify/lookup")
    @PreAuthorize("hasAuthority('verify:perform')")
    public VerificationService.VerificationLookupResult lookupVerify(@RequestBody Map<String, String> body) {
        String apaar = body.get("apaarId");
        String dobStr = body.get("dob");
        LocalDate dob = dobStr != null && !dobStr.isBlank() ? LocalDate.parse(dobStr.trim()) : null;
        String requestedTier = body.get("requestedTier");
        String purpose = body.get("purpose");
        if (apaar == null || apaar.isBlank()) throw ApiException.badRequest("apaarId is required");
        return verificationService.lookupVerify(apaar, dob, requestedTier, purpose);
    }
}
