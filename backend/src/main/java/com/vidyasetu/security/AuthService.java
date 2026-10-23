package com.vidyasetu.security;

import com.vidyasetu.audit.AuditService;
import com.vidyasetu.common.ApiException;
import com.vidyasetu.common.Masking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final JdbcTemplate jdbc;
    private final JwtService jwtService;
    private final CryptoService cryptoService;
    private final AuditService auditService;
    private final boolean demoMode;

    public AuthService(JdbcTemplate jdbc, JwtService jwtService,
                       CryptoService cryptoService, AuditService auditService,
                       @Value("${app.demo-mode:true}") boolean demoMode) {
        this.jdbc = jdbc;
        this.jwtService = jwtService;
        this.cryptoService = cryptoService;
        this.auditService = auditService;
        this.demoMode = demoMode;
    }

    public record AuthResponse(
            String accessToken,
            long expiresIn,
            UserDto user
    ) {}

    public record UserDto(
            UUID id,
            String email,
            String fullName,
            Set<String> roles,
            Set<String> permissions,
            UUID institutionId,
            String institutionCode,
            String institutionName,
            UUID studentId,
            String apaarMasked,
            String org,
            String status,
            String lastLoginAt
    ) {}

    public UserPrincipal loadUserByEmail(String email) {
        List<Map<String, Object>> users = jdbc.query("""
            SELECT u.id, u.email, u.password_hash, u.full_name, u.status, u.institution_id,
                   u.mfa_secret, u.org, u.locked_until,
                   i.aicte_id, i.name as inst_name
            FROM users u
            LEFT JOIN institutions i ON i.id = u.institution_id
            WHERE LOWER(u.email) = LOWER(?)
        """, (rs, rowNum) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", rs.getObject("id"));
            map.put("email", rs.getString("email"));
            map.put("password_hash", rs.getString("password_hash"));
            map.put("full_name", rs.getString("full_name"));
            map.put("status", rs.getString("status"));
            map.put("institution_id", rs.getObject("institution_id"));
            map.put("org", rs.getString("org"));
            map.put("aicte_id", rs.getString("aicte_id"));
            map.put("inst_name", rs.getString("inst_name"));
            return map;
        }, email);

        if (users.isEmpty()) return null;
        Map<String, Object> u = users.get(0);
        UUID userId = (UUID) u.get("id");

        Set<String> roles = new HashSet<>(jdbc.query("SELECT role_code FROM user_roles WHERE user_id = ?",
                (rs, rowNum) -> rs.getString("role_code"), userId));

        Set<String> perms = new HashSet<>(jdbc.query("""
            SELECT DISTINCT rp.permission_code
            FROM user_roles ur
            JOIN role_permissions rp ON rp.role_code = ur.role_code
            WHERE ur.user_id = ?
        """, (rs, rowNum) -> rs.getString("permission_code"), userId));

        return new UserPrincipal(
                userId,
                (String) u.get("email"),
                (String) u.get("password_hash"),
                (String) u.get("full_name"),
                roles,
                perms,
                (UUID) u.get("institution_id"),
                (String) u.get("aicte_id"),
                (String) u.get("inst_name"),
                null,
                null,
                (String) u.get("org"),
                "ACTIVE".equals(u.get("status"))
        );
    }

    public AuthResponse login(String email, String password) {
        UserPrincipal user = loadUserByEmail(email);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw ApiException.unauthorized("Invalid email or password");
        }

        jdbc.update("UPDATE users SET last_login_at = now(), failed_attempts = 0 WHERE id = ?", user.getId());
        auditService.record("USER_LOGIN", "USER", user.getId().toString(), user.getInstitutionId(),
                Map.of("email", user.getEmail(), "role", user.getRoles().isEmpty() ? "USER" : user.getRoles().iterator().next()));

        String token = jwtService.issueAccessToken(user);
        return new AuthResponse(token, 3600, toDto(user));
    }

    public AuthResponse loginStudentOtp(String rawApaar, String otp) {
        String apaar = Masking.normalizeApaar(rawApaar);
        // In demo profile, OTP is 123456
        if (!"123456".equals(otp)) {
            throw ApiException.unauthorized("Invalid OTP");
        }

        List<Map<String, Object>> students = jdbc.query("""
            SELECT s.id, s.apaar_id, s.full_name, e.institution_id, i.aicte_id, i.name as inst_name
            FROM students s
            LEFT JOIN enrolments e ON e.student_id = s.id
            LEFT JOIN institutions i ON i.id = e.institution_id
            WHERE s.apaar_id = ? LIMIT 1
        """, (rs, rowNum) -> Map.of(
                "id", rs.getObject("id"),
                "apaarId", rs.getString("apaar_id"),
                "fullName", rs.getString("full_name"),
                "institutionId", rs.getObject("institution_id") != null ? rs.getObject("institution_id") : UUID.randomUUID(),
                "aicteId", rs.getString("aicte_id") != null ? rs.getString("aicte_id") : "",
                "instName", rs.getString("inst_name") != null ? rs.getString("inst_name") : ""
        ), apaar);

        if (students.isEmpty()) {
            throw ApiException.notFound("Student APAAR ID");
        }

        Map<String, Object> st = students.get(0);
        UUID studentId = (UUID) st.get("id");
        String masked = Masking.maskApaar(apaar);

        Set<String> roles = Set.of("STUDENT");
        Set<String> perms = Set.of("consent:manage_own", "record:read_own");

        UserPrincipal principal = new UserPrincipal(
                studentId,
                apaar + "@student.vidyasetu.local",
                "",
                (String) st.get("fullName"),
                roles,
                perms,
                (UUID) st.get("institutionId"),
                (String) st.get("aicteId"),
                (String) st.get("instName"),
                studentId,
                masked,
                "STUDENT",
                true
        );

        auditService.record("STUDENT_LOGIN", "STUDENT", studentId.toString(), principal.getInstitutionId(),
                Map.of("apaarMasked", masked));

        String token = jwtService.issueAccessToken(principal);
        return new AuthResponse(token, 3600, toDto(principal));
    }

    public UserDto toDto(UserPrincipal u) {
        return new UserDto(
                u.getId(),
                u.getEmail(),
                u.getFullName(),
                u.getRoles(),
                u.getPermissions(),
                u.getInstitutionId(),
                u.getInstitutionCode(),
                u.getInstitutionName(),
                u.getStudentId(),
                u.getApaarMasked(),
                u.getOrg(),
                "ACTIVE",
                Instant.now().toString()
        );
    }

    public List<UserDto> listUsers() {
        TenantScope.requireUser();
        UUID inst = TenantScope.resolveInstitutionFilter(null);
        String sql = """
            SELECT u.id, u.email, u.full_name, u.status, u.institution_id, u.last_login_at,
                   i.aicte_id, i.name as inst_name
            FROM users u
            LEFT JOIN institutions i ON i.id = u.institution_id
        """ + (inst != null ? " WHERE u.institution_id = ? " : "") + " ORDER BY u.email ASC";

        Object[] params = inst != null ? new Object[]{inst} : new Object[0];

        return jdbc.query(sql, (rs, rowNum) -> {
            UUID uId = (UUID) rs.getObject("id");
            Set<String> roles = new HashSet<>(jdbc.query("SELECT role_code FROM user_roles WHERE user_id = ?",
                    (rrs, rnum) -> rrs.getString("role_code"), uId));
            return new UserDto(
                    uId,
                    rs.getString("email"),
                    rs.getString("full_name"),
                    roles,
                    Collections.emptySet(),
                    (UUID) rs.getObject("institution_id"),
                    rs.getString("aicte_id"),
                    rs.getString("inst_name"),
                    null,
                    null,
                    null,
                    rs.getString("status"),
                    rs.getTimestamp("last_login_at") != null ? rs.getTimestamp("last_login_at").toInstant().toString() : null
            );
        }, params);
    }

    @Transactional
    public UserDto createUser(String email, String fullName, String password, String role, UUID institutionId) {
        TenantScope.assertCanAccessInstitution(institutionId);
        UUID id = UUID.randomUUID();
        String hash = passwordEncoder.encode(password != null ? password : "Demo@12345");

        jdbc.update("""
            INSERT INTO users (id, email, password_hash, full_name, institution_id, status, created_at)
            VALUES (?, ?, ?, ?, ?, 'ACTIVE', now())
        """, id, email.toLowerCase().trim(), hash, fullName, institutionId);

        jdbc.update("INSERT INTO user_roles (user_id, role_code) VALUES (?, ?)", id, role);

        auditService.record("USER_CREATED", "USER", id.toString(), institutionId, Map.of("email", email, "role", role));
        return loadUserByEmail(email) != null ? toDto(loadUserByEmail(email)) : null;
    }

    public List<Map<String, Object>> listApiClients(UUID institutionId) {
        UUID inst = TenantScope.resolveInstitutionFilter(institutionId);
        String sql = """
            SELECT c.id, c.name, c.institution_id, i.aicte_id, c.key_id, c.scopes, c.status, c.webhook_url
            FROM api_clients c
            LEFT JOIN institutions i ON i.id = c.institution_id
        """ + (inst != null ? " WHERE c.institution_id = ? " : "") + " ORDER BY c.created_at DESC";

        Object[] params = inst != null ? new Object[]{inst} : new Object[0];

        return jdbc.query(sql, (rs, rowNum) -> Map.of(
                "id", rs.getObject("id").toString(),
                "name", rs.getString("name"),
                "institutionId", rs.getObject("institution_id") != null ? rs.getObject("institution_id").toString() : "",
                "institutionCode", rs.getString("aicte_id") != null ? rs.getString("aicte_id") : "",
                "keyId", rs.getString("key_id"),
                "scopes", rs.getString("scopes") != null ? rs.getString("scopes").split(",") : new String[0],
                "status", rs.getString("status"),
                "webhookUrl", rs.getString("webhook_url") != null ? rs.getString("webhook_url") : ""
        ), params);
    }

    @Transactional
    public Map<String, Object> createApiClient(String name, UUID institutionId, List<String> scopes, String webhookUrl) {
        TenantScope.assertCanAccessInstitution(institutionId);
        UUID id = UUID.randomUUID();
        String keyId = "key-" + UUID.randomUUID().toString().substring(0, 8);
        String secret = "sec-" + UUID.randomUUID().toString().replace("-", "");
        String webhookSecret = "wh-" + UUID.randomUUID().toString().replace("-", "");

        String secretEnc = cryptoService.encrypt(secret);
        String whEnc = cryptoService.encrypt(webhookSecret);
        String scopeStr = scopes != null ? String.join(",", scopes) : "upload:create,upload:read";

        jdbc.update("""
            INSERT INTO api_clients (id, name, institution_id, key_id, secret_enc, scopes, status, webhook_url, webhook_secret_enc, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, now())
        """, id, name, institutionId, keyId, secretEnc, scopeStr, webhookUrl, whEnc);

        auditService.record("API_CLIENT_CREATED", "API_CLIENT", id.toString(), institutionId, Map.of("keyId", keyId));

        Map<String, Object> res = new HashMap<>();
        res.put("id", id.toString());
        res.put("name", name);
        res.put("keyId", keyId);
        res.put("secret", secret); // Shown only once upon creation
        res.put("webhookSecret", webhookSecret);
        res.put("webhookUrl", webhookUrl);
        res.put("scopes", scopes);
        return res;
    }
}
