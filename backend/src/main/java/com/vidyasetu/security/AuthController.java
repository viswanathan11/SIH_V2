package com.vidyasetu.security;

import com.vidyasetu.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService authService;
    private final JwtService jwtService;
    private final boolean demoMode;
    private final String academicYear;

    public AuthController(AuthService authService,
                          JwtService jwtService,
                          @Value("${app.demo-mode:true}") boolean demoMode,
                          @Value("${app.academic-year:2026-27}") String academicYear) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.demoMode = demoMode;
        this.academicYear = academicYear;
    }

    @GetMapping({"/public/jwks.json", "/.well-known/jwks.json"})
    public Map<String, Object> getJwks() {
        return jwtService.getJwksMap();
    }

    @GetMapping("/public/config")
    public Map<String, Object> getPublicConfig() {
        return Map.of(
                "demoMode", demoMode,
                "academicYear", academicYear,
                "appName", "VidyaSetu"
        );
    }

    @GetMapping("/public/demo-accounts")
    public List<Map<String, Object>> getDemoAccounts() {
        if (!demoMode) return List.of();
        return List.of(
                Map.of("role", "SUPER_ADMIN", "label", "Super Admin (Full Access)", "email", "super@demo.vidyasetu.example", "password", "Demo@12345", "group", "Platform"),
                Map.of("role", "AICTE_ADMIN", "label", "AICTE National Admin", "email", "aicte.admin@demo.vidyasetu.example", "password", "Demo@12345", "group", "AICTE"),
                Map.of("role", "AICTE_ANALYST", "label", "AICTE Analyst (Watchlists & Reports)", "email", "aicte.analyst@demo.vidyasetu.example", "password", "Demo@12345", "group", "AICTE"),
                Map.of("role", "AICTE_AUDITOR", "label", "AICTE Auditor (Audit Chain & Logs)", "email", "aicte.auditor@demo.vidyasetu.example", "password", "Demo@12345", "group", "AICTE"),
                Map.of("role", "INSTITUTION_ADMIN", "label", "Coromandel Inst. Admin (INST-TN-001 - Exemplar)", "email", "admin.tn001@demo.vidyasetu.example", "password", "Demo@12345", "group", "Institutions"),
                Map.of("role", "INSTITUTION_OPERATOR", "label", "Coromandel Data Operator", "email", "operator.tn001@demo.vidyasetu.example", "password", "Demo@12345", "group", "Institutions"),
                Map.of("role", "INSTITUTION_ADMIN", "label", "Aravalli Inst. Admin (INST-RJ-001 - Dirty Batch / Worklist)", "email", "admin.rj001@demo.vidyasetu.example", "password", "Demo@12345", "group", "Institutions"),
                Map.of("role", "REGULATOR", "label", "UGC Regulator", "email", "regulator.ugc@demo.vidyasetu.example", "password", "Demo@12345", "group", "Regulators"),
                Map.of("role", "EMPLOYER_VERIFIER", "label", "TechCorp Employer HR", "email", "hr@techcorp.demo.vidyasetu.example", "password", "Demo@12345", "group", "Verifiers"),
                Map.of("role", "EMPLOYER_VERIFIER", "label", "FinCo Employer HR", "email", "hr@finco.demo.vidyasetu.example", "password", "Demo@12345", "group", "Verifiers"),
                Map.of("role", "STUDENT", "label", "Student Asha Raman (910000000001 - Active Consent)", "apaarId", "910000000001", "otp", "123456", "group", "Students"),
                Map.of("role", "STUDENT", "label", "Student Pending Consent (910000000004 - FinCo Pending)", "apaarId", "910000000004", "otp", "123456", "group", "Students")
        );
    }

    @PostMapping("/auth/login")
    public AuthService.AuthResponse login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        if (email == null || password == null) throw ApiException.badRequest("Email and password required");
        return authService.login(email, password);
    }

    @PostMapping("/auth/student/otp-request")
    public Map<String, Object> requestStudentOtp(@RequestBody Map<String, String> body) {
        String apaar = body.get("apaarId");
        if (apaar == null || apaar.isBlank()) throw ApiException.badRequest("APAAR ID required");
        return Map.of("sent", true, "demoOtp", "123456", "message", "OTP sent (Demo OTP: 123456)");
    }

    @PostMapping("/auth/student/otp-verify")
    public AuthService.AuthResponse verifyStudentOtp(@RequestBody Map<String, String> body) {
        String apaar = body.get("apaarId");
        String otp = body.get("otp");
        if (apaar == null || otp == null) throw ApiException.badRequest("APAAR ID and OTP required");
        return authService.loginStudentOtp(apaar, otp);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthService.UserDto getCurrentUser() {
        UserPrincipal user = TenantScope.requireUser();
        return authService.toDto(user);
    }
}
