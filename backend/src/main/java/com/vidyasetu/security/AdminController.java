package com.vidyasetu.security;

import com.vidyasetu.audit.AuditService;
import com.vidyasetu.common.ApiException;
import com.vidyasetu.common.PageResponse;
import com.vidyasetu.seed.SeedService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AdminController {
    private final AuthService authService;
    private final AuditService auditService;
    private final SeedService seedService;
    private final boolean demoMode;

    public AdminController(AuthService authService, AuditService auditService,
                           SeedService seedService, @Value("${app.demo-mode:true}") boolean demoMode) {
        this.authService = authService;
        this.auditService = auditService;
        this.seedService = seedService;
        this.demoMode = demoMode;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('user:manage')")
    public List<AuthService.UserDto> listUsers() {
        return authService.listUsers();
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('user:manage')")
    public AuthService.UserDto createUser(@RequestBody Map<String, Object> body) {
        String email = (String) body.get("email");
        String fullName = (String) body.get("fullName");
        String password = (String) body.get("password");
        String role = (String) body.get("role");
        String instIdStr = (String) body.get("institutionId");
        UUID instId = (instIdStr != null && !instIdStr.isBlank()) ? UUID.fromString(instIdStr) : null;
        return authService.createUser(email, fullName, password, role, instId);
    }

    @GetMapping("/api-clients")
    @PreAuthorize("hasAuthority('apiclient:manage')")
    public List<Map<String, Object>> listApiClients(@RequestParam(value = "institutionId", required = false) UUID institutionId) {
        return authService.listApiClients(institutionId);
    }

    @PostMapping("/api-clients")
    @PreAuthorize("hasAuthority('apiclient:manage')")
    public Map<String, Object> createApiClient(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String instIdStr = (String) body.get("institutionId");
        UUID instId = (instIdStr != null && !instIdStr.isBlank()) ? UUID.fromString(instIdStr) : null;
        List<String> scopes = (List<String>) body.get("scopes");
        String webhookUrl = (String) body.get("webhookUrl");
        return authService.createApiClient(name, instId, scopes, webhookUrl);
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('audit:read')")
    public PageResponse<AuditService.AuditEntry> listAudit(
            @RequestParam(value = "institutionId", required = false) UUID institutionId,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "actor", required = false) String actor,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        return auditService.findEntries(institutionId, action, actor, page, size);
    }

    @GetMapping("/audit/verify-chain")
    @PreAuthorize("hasAuthority('audit:read')")
    public AuditService.ChainVerificationResult verifyAuditChain() {
        return auditService.verifyChain();
    }

    @PostMapping("/demo/reset")
    public Map<String, Object> resetDemo() {
        if (!demoMode) throw ApiException.forbidden("Demo reset only permitted in demo-mode");
        long start = System.currentTimeMillis();
        seedService.resetAndSeedAll();
        long elapsed = System.currentTimeMillis() - start;
        return Map.of("ok", true, "ms", elapsed, "message", "Database successfully reset and re-seeded in " + elapsed + "ms");
    }
}
