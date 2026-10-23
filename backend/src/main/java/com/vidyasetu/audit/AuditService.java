package com.vidyasetu.audit;

import com.vidyasetu.common.Json;
import com.vidyasetu.common.PageResponse;
import com.vidyasetu.security.CryptoService;
import com.vidyasetu.security.TenantScope;
import com.vidyasetu.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    private final JdbcTemplate jdbc;
    private final CryptoService cryptoService;

    public AuditService(JdbcTemplate jdbc, CryptoService cryptoService) {
        this.jdbc = jdbc;
        this.cryptoService = cryptoService;
    }

    public record AuditEntry(
            Long id,
            Instant ts,
            String actorId,
            String actorEmail,
            String actorRole,
            String action,
            String entityType,
            String entityId,
            UUID institutionId,
            String ip,
            String userAgent,
            String details,
            String prevHash,
            String hash
    ) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized void record(String action, String entityType, String entityId, UUID institutionId,
                                    Map<String, Object> details, String ip, String userAgent) {
        try {
            UserPrincipal user = TenantScope.currentUser();
            String actorId = user != null ? user.getId().toString() : "system";
            String actorEmail = user != null ? user.getEmail() : "system";
            String actorRole = user != null && !user.getRoles().isEmpty() ? user.getRoles().iterator().next() : "SYSTEM";
            UUID instId = institutionId != null ? institutionId : (user != null ? user.getInstitutionId() : null);

            String prevHash = jdbc.query("SELECT hash FROM audit_log ORDER BY id DESC LIMIT 1",
                    rs -> rs.next() ? rs.getString(1) : GENESIS_HASH);

            Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            String detailsJson = details != null && !details.isEmpty() ? Json.write(details) : "{}";

            Map<String, Object> canonicalPayload = new LinkedHashMap<>();
            canonicalPayload.put("action", action);
            canonicalPayload.put("actorEmail", actorEmail);
            canonicalPayload.put("actorId", actorId);
            canonicalPayload.put("actorRole", actorRole);
            canonicalPayload.put("details", detailsJson);
            canonicalPayload.put("entityId", entityId);
            canonicalPayload.put("entityType", entityType);
            canonicalPayload.put("institutionId", instId != null ? instId.toString() : null);
            canonicalPayload.put("ip", ip);
            canonicalPayload.put("ts", now.toString());

            String hashInput = prevHash + "|" + Json.write(canonicalPayload);
            String hash = cryptoService.sha256Hex(hashInput);

            jdbc.update("""
                INSERT INTO audit_log (ts, actor_id, actor_email, actor_role, action, entity_type, entity_id,
                                       institution_id, ip, user_agent, details, prev_hash, hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                    java.sql.Timestamp.from(now), actorId, actorEmail, actorRole, action, entityType, entityId,
                    instId, ip, userAgent, detailsJson, prevHash, hash);
        } catch (Exception e) {
            log.error("Failed to write audit entry: {}", e.getMessage(), e);
        }
    }

    public void record(String action, String entityType, String entityId, UUID institutionId, Map<String, Object> details) {
        record(action, entityType, entityId, institutionId, details, "127.0.0.1", "VidyaSetu-Internal");
    }

    public PageResponse<AuditEntry> findEntries(UUID institutionId, String action, String actor, int page, int size) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id, ts, actor_id, actor_email, actor_role, action, entity_type, entity_id, institution_id, ip, user_agent, details, prev_hash, hash FROM audit_log WHERE 1=1 ");

        if (institutionId != null) {
            sql.append(" AND institution_id = ? ");
            params.add(institutionId);
        }
        if (action != null && !action.isBlank()) {
            sql.append(" AND action ILIKE ? ");
            params.add("%" + action.trim() + "%");
        }
        if (actor != null && !actor.isBlank()) {
            sql.append(" AND (actor_email ILIKE ? OR actor_id ILIKE ?) ");
            params.add("%" + actor.trim() + "%");
            params.add("%" + actor.trim() + "%");
        }

        Long total = jdbc.queryForObject("SELECT count(*) FROM (" + sql + ") t", Long.class, params.toArray());
        long count = total != null ? total : 0;

        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ? ");
        params.add(size);
        params.add(page * size);

        List<AuditEntry> entries = jdbc.query(sql.toString(), (rs, rowNum) -> new AuditEntry(
                rs.getLong("id"),
                rs.getTimestamp("ts").toInstant(),
                rs.getString("actor_id"),
                rs.getString("actor_email"),
                rs.getString("actor_role"),
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getObject("institution_id") != null ? (UUID) rs.getObject("institution_id") : null,
                rs.getString("ip"),
                rs.getString("user_agent"),
                rs.getString("details"),
                rs.getString("prev_hash"),
                rs.getString("hash")
        ), params.toArray());

        return PageResponse.of(entries, page, size, count);
    }

    public record ChainVerificationResult(boolean intact, long checked, Long firstBrokenId) {}

    public ChainVerificationResult verifyChain() {
        List<AuditEntry> rows = jdbc.query("""
            SELECT id, ts, actor_id, actor_email, actor_role, action, entity_type, entity_id,
                   institution_id, ip, user_agent, details, prev_hash, hash
            FROM audit_log ORDER BY id ASC
        """, (rs, rowNum) -> new AuditEntry(
                rs.getLong("id"),
                rs.getTimestamp("ts").toInstant(),
                rs.getString("actor_id"),
                rs.getString("actor_email"),
                rs.getString("actor_role"),
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getObject("institution_id") != null ? (UUID) rs.getObject("institution_id") : null,
                rs.getString("ip"),
                rs.getString("user_agent"),
                rs.getString("details"),
                rs.getString("prev_hash"),
                rs.getString("hash")
        ));

        String expectedPrevHash = GENESIS_HASH;
        for (AuditEntry row : rows) {
            if (!expectedPrevHash.equals(row.prevHash())) {
                log.warn("Audit chain broken at ID {}: expected prevHash {}, found {}", row.id(), expectedPrevHash, row.prevHash());
                return new ChainVerificationResult(false, rows.size(), row.id());
            }

            Map<String, Object> canonicalPayload = new LinkedHashMap<>();
            canonicalPayload.put("action", row.action());
            canonicalPayload.put("actorEmail", row.actorEmail());
            canonicalPayload.put("actorId", row.actorId());
            canonicalPayload.put("actorRole", row.actorRole());
            canonicalPayload.put("details", row.details() != null ? row.details() : "{}");
            canonicalPayload.put("entityId", row.entityId());
            canonicalPayload.put("entityType", row.entityType());
            canonicalPayload.put("institutionId", row.institutionId() != null ? row.institutionId().toString() : null);
            canonicalPayload.put("ip", row.ip());
            canonicalPayload.put("ts", row.ts().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString());

            String hashInput = row.prevHash() + "|" + Json.write(canonicalPayload);
            String computedHash = cryptoService.sha256Hex(hashInput);

            if (!computedHash.equals(row.hash())) {
                log.warn("Audit chain hash mismatch at ID {}: computed {}, stored {}", row.id(), computedHash, row.hash());
                return new ChainVerificationResult(false, rows.size(), row.id());
            }

            expectedPrevHash = row.hash();
        }

        return new ChainVerificationResult(true, rows.size(), null);
    }
}
