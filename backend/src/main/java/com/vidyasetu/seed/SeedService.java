package com.vidyasetu.seed;

import com.vidyasetu.anomaly.AnomalyService;
import com.vidyasetu.audit.AuditService;
import com.vidyasetu.common.Json;
import com.vidyasetu.common.Masking;
import com.vidyasetu.ingestion.IngestionService;
import com.vidyasetu.security.CryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

@Service
public class SeedService {
    private static final Logger log = LoggerFactory.getLogger(SeedService.class);
    private static final long SEED = 20261023L;

    private final JdbcTemplate jdbc;
    private final CryptoService cryptoService;
    private final AnomalyService anomalyService;
    private final AuditService auditService;
    private final boolean seedOnStartup;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public SeedService(JdbcTemplate jdbc, CryptoService cryptoService,
                       AnomalyService anomalyService, AuditService auditService,
                       @Value("${app.seed-on-startup:true}") boolean seedOnStartup) {
        this.jdbc = jdbc;
        this.cryptoService = cryptoService;
        this.anomalyService = anomalyService;
        this.auditService = auditService;
        this.seedOnStartup = seedOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!seedOnStartup) return;
        Integer count = jdbc.queryForObject("SELECT count(*) FROM institutions", Integer.class);
        if (count == null || count == 0) {
            log.info("Database is empty, generating deterministic master seed data...");
            resetAndSeedAll();
        } else {
            log.info("Database already seeded with {} institutions", count);
        }
    }

    @Transactional
    public synchronized void resetAndSeedAll() {
        log.info("Resetting database for demo seeding...");
        // Disable audit trigger temporarily for reset
        jdbc.execute("SET vidyasetu.allow_audit_reset = 'on'");
        jdbc.execute("TRUNCATE TABLE consent_requests, consents, issued_records, verification_requests, " +
                "validation_issues, staging_rows, upload_batches, enrolment_history, enrolments, " +
                "students, apaar_registry_mock, anomalies, institution_risk, enrolment_stats_history, " +
                "programs, users, api_clients, webhook_deliveries, audit_log, institutions CASCADE");
        jdbc.execute("SET vidyasetu.allow_audit_reset = 'off'");

        Random rng = new Random(SEED);

        // 1. Seed 12 Institutions
        Map<String, UUID> instMap = seedInstitutions();

        // 2. Seed Programs
        Map<String, UUID> progMap = seedPrograms(instMap);

        // 3. Seed 5 Years History (2021-22 to 2025-26)
        seedHistory(instMap);

        // 4. Seed Demo Users & API Clients
        seedUsers(instMap);

        // 5. Seed Mock APAAR Registry (~6000 synthetic IDs)
        List<String> mockApaars = seedMockRegistry(rng);

        // 6. Seed Baseline Students & Planted Scenarios S1-S10
        seedBaselineAndScenarios(instMap, progMap, mockApaars, rng);

        // 7. Generate Demo CSV Files in `seed/` directory
        generateDemoCsvFiles(instMap, mockApaars);

        // 8. Run Anomaly Engine
        anomalyService.recomputeAnomalies();

        log.info("Demo seeding completed successfully.");
    }

    private Map<String, UUID> seedInstitutions() {
        Map<String, UUID> map = new LinkedHashMap<>();
        List<String[]> data = List.of(
                new String[]{"INST-TN-001", "Coromandel Institute of Technology", "Tamil Nadu", "TN", "Chennai", "UG/PG Engg"},
                new String[]{"INST-TN-002", "Kaveri College of Engineering", "Tamil Nadu", "TN", "Coimbatore", "UG Engg"},
                new String[]{"INST-KA-001", "Deccan Institute of Engineering", "Karnataka", "KA", "Bengaluru", "UG/PG Engg"},
                new String[]{"INST-MH-001", "Sahyadri Institute of Technology", "Maharashtra", "MH", "Pune", "UG Engg"},
                new String[]{"INST-MH-002", "Konkan School of Management", "Maharashtra", "MH", "Mumbai", "PGDM"},
                new String[]{"INST-DL-001", "Yamuna Technical College", "Delhi", "DL", "New Delhi", "UG Engg"},
                new String[]{"INST-UP-001", "Gangetic Institute of Engineering", "Uttar Pradesh", "UP", "Noida", "UG Engg"},
                new String[]{"INST-GJ-001", "Narmada College of Technology", "Gujarat", "GJ", "Ahmedabad", "UG Engg"},
                new String[]{"INST-KL-001", "Malabar Institute of Technology", "Kerala", "KL", "Kochi", "UG Engg"},
                new String[]{"INST-RJ-001", "Aravalli Institute of Engineering", "Rajasthan", "RJ", "Jaipur", "UG Engg"},
                new String[]{"INST-WB-001", "Hooghly Institute of Technology", "West Bengal", "WB", "Kolkata", "UG Engg"},
                new String[]{"INST-TS-001", "Godavari Institute of Technology", "Telangana", "TS", "Hyderabad", "UG/PG Engg"}
        );

        for (String[] row : data) {
            UUID id = UUID.randomUUID();
            map.put(row[0], id);
            jdbc.update("""
                INSERT INTO institutions (id, aicte_id, name, state, state_code, district, institution_type, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', now())
            """, id, row[0], row[1], row[2], row[3], row[4], row[5]);
        }
        return map;
    }

    private Map<String, UUID> seedPrograms(Map<String, UUID> instMap) {
        Map<String, UUID> progMap = new LinkedHashMap<>();

        // Add 3-5 programs for each institution
        for (var entry : instMap.entrySet()) {
            String aicteId = entry.getKey();
            UUID instId = entry.getValue();

            if ("INST-MH-002".equals(aicteId)) {
                // Management institute
                addProgram(progMap, instId, aicteId, "PGDM-MGT", "PGDM", "Post Graduate Diploma in Management", 180, 0, false, false);
                addProgram(progMap, instId, aicteId, "MBA-FIN", "PG", "Master of Business Administration (Finance)", 120, 0, false, false);
                addProgram(progMap, instId, aicteId, "MBA-HR", "PG", "Master of Business Administration (HR)", 60, 0, false, false);
            } else {
                // Engineering institutes
                int cseIntake = "INST-TN-001".equals(aicteId) ? 120 : ("INST-UP-001".equals(aicteId) ? 180 : 120);
                addProgram(progMap, instId, aicteId, "BTECH-CSE", "UG", "B.Tech Computer Science and Engineering", cseIntake, 12, true, true);
                addProgram(progMap, instId, aicteId, "BTECH-ECE", "UG", "B.Tech Electronics and Communication Engineering", 120, 12, true, false);
                addProgram(progMap, instId, aicteId, "BTECH-MECH", "UG", "B.Tech Mechanical Engineering", 60, 6, true, false);
                addProgram(progMap, instId, aicteId, "BTECH-AIDS", "UG", "B.Tech Artificial Intelligence and Data Science", 60, 6, false, true);
                addProgram(progMap, instId, aicteId, "MCA", "PG", "Master of Computer Applications", 60, 0, false, false);
            }
        }
        return progMap;
    }

    private void addProgram(Map<String, UUID> progMap, UUID instId, String aicteId, String code, String level,
                            String name, int intake, int lateral, boolean tfw, boolean fnOci) {
        UUID id = UUID.randomUUID();
        progMap.put(aicteId + "|" + code, id);
        jdbc.update("""
            INSERT INTO programs (id, institution_id, program_code, level, name, academic_year, approved_intake,
                                 approved_lateral_intake, tfw_enabled, fn_oci_enabled, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, '2026-27', ?, ?, ?, ?, now(), now())
        """, id, instId, code, level, name, intake, lateral, tfw, fnOci);
    }

    private void seedHistory(Map<String, UUID> instMap) {
        List<String> years = List.of("2021-22", "2022-23", "2023-24", "2024-25", "2025-26");

        for (var entry : instMap.entrySet()) {
            String aicteId = entry.getKey();
            UUID instId = entry.getValue();

            // Scenario S7: INST-KL-001 has chronic under-enrolment (fill <= 30% for 5 years)
            boolean isChronicUnder = "INST-KL-001".equals(aicteId);

            for (String yr : years) {
                int cseIntake = 120;
                int cseEnrolled = isChronicUnder ? 30 : 110;
                jdbc.update("INSERT INTO enrolment_stats_history VALUES (?, 'BTECH-CSE', ?, ?, ?)",
                        instId, yr, cseIntake, cseEnrolled);

                int eceIntake = 120;
                int eceEnrolled = isChronicUnder ? 28 : 105;
                jdbc.update("INSERT INTO enrolment_stats_history VALUES (?, 'BTECH-ECE', ?, ?, ?)",
                        instId, yr, eceIntake, eceEnrolled);
            }
        }
    }

    private void seedUsers(Map<String, UUID> instMap) {
        String hash = passwordEncoder.encode("Demo@12345");

        // Super Admin
        UUID superId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, status, created_at) VALUES (?, 'super@demo.vidyasetu.example', ?, 'Super Admin', 'ACTIVE', now())", superId, hash);
        jdbc.update("INSERT INTO user_roles VALUES (?, 'SUPER_ADMIN')", superId);

        // AICTE Admin, Analyst, Auditor
        UUID aicteAdminId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, status, created_at) VALUES (?, 'aicte.admin@demo.vidyasetu.example', ?, 'AICTE National Admin', 'ACTIVE', now())", aicteAdminId, hash);
        jdbc.update("INSERT INTO user_roles VALUES (?, 'AICTE_ADMIN')", aicteAdminId);

        UUID aicteAnalystId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, status, created_at) VALUES (?, 'aicte.analyst@demo.vidyasetu.example', ?, 'AICTE Policy Analyst', 'ACTIVE', now())", aicteAnalystId, hash);
        jdbc.update("INSERT INTO user_roles VALUES (?, 'AICTE_ANALYST')", aicteAnalystId);

        UUID aicteAuditorId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, status, created_at) VALUES (?, 'aicte.auditor@demo.vidyasetu.example', ?, 'AICTE Chief Auditor', 'ACTIVE', now())", aicteAuditorId, hash);
        jdbc.update("INSERT INTO user_roles VALUES (?, 'AICTE_AUDITOR')", aicteAuditorId);

        // Regulators
        UUID ugcId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, org, status, created_at) VALUES (?, 'regulator.ugc@demo.vidyasetu.example', ?, 'UGC Officer', 'UGC', 'ACTIVE', now())", ugcId, hash);
        jdbc.update("INSERT INTO user_roles VALUES (?, 'REGULATOR')", ugcId);

        // Employers
        UUID hrTech = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, org, status, created_at) VALUES (?, 'hr@techcorp.demo.vidyasetu.example', ?, 'TechCorp HR', 'TechCorp', 'ACTIVE', now())", hrTech, hash);
        jdbc.update("INSERT INTO user_roles VALUES (?, 'EMPLOYER_VERIFIER')", hrTech);

        UUID hrFin = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, org, status, created_at) VALUES (?, 'hr@finco.demo.vidyasetu.example', ?, 'FinCo HR', 'FinCo', 'ACTIVE', now())", hrFin, hash);
        jdbc.update("INSERT INTO user_roles VALUES (?, 'EMPLOYER_VERIFIER')", hrFin);

        // Institution Admins and Operators for each of the 12 institutions
        for (var entry : instMap.entrySet()) {
            String aicteId = entry.getKey();
            UUID instId = entry.getValue();
            String code = aicteId.replace("INST-", "").toLowerCase();

            UUID admId = UUID.randomUUID();
            jdbc.update("INSERT INTO users (id, email, password_hash, full_name, institution_id, status, created_at) VALUES (?, ?, ?, ?, ?, 'ACTIVE', now())",
                    admId, "admin." + code + "@demo.vidyasetu.example", hash, "Admin " + aicteId, instId);
            jdbc.update("INSERT INTO user_roles VALUES (?, 'INSTITUTION_ADMIN')", admId);

            UUID opId = UUID.randomUUID();
            jdbc.update("INSERT INTO users (id, email, password_hash, full_name, institution_id, status, created_at) VALUES (?, ?, ?, ?, ?, 'ACTIVE', now())",
                    opId, "operator." + code + "@demo.vidyasetu.example", hash, "Operator " + aicteId, instId);
            jdbc.update("INSERT INTO user_roles VALUES (?, 'INSTITUTION_OPERATOR')", opId);

            // API Client for ERP
            UUID clId = UUID.randomUUID();
            String secEnc = cryptoService.encrypt("demo-secret-" + code);
            jdbc.update("""
                INSERT INTO api_clients (id, name, institution_id, key_id, secret_enc, scopes, status, created_at)
                VALUES (?, ?, ?, ?, ?, 'student:write,upload:create,upload:read,validation:read', 'ACTIVE', now())
            """, clId, "ERP " + aicteId, instId, "demo-erp-" + code, secEnc);
        }
    }

    private List<String> seedMockRegistry(Random rng) {
        log.info("Seeding synthetic mock APAAR registry (~6000 records)...");
        List<String> firstNames = List.of("Aarav", "Aditi", "Asha", "Balaji", "Deepak", "Divya", "Ganesh", "Isha", "Karthik", "Lakshmi", "Manoj", "Neha", "Pooja", "Rahul", "Rohit", "Sneha", "Suresh", "Varun", "Vignesh", "Zoya");
        List<String> lastNames = List.of("Raman", "Sharma", "Iyer", "Nair", "Patel", "Verma", "Reddy", "Gupta", "Deshmukh", "Choudhury", "Bose", "Mehta", "Singh", "Das", "Menon", "Pillai");

        List<String> list = new ArrayList<>();
        List<Object[]> batch = new ArrayList<>();

        // Add demo-critical IDs first:
        // 910000000001 to 910000000020
        for (long i = 1; i <= 20; i++) {
            String apaar = String.format("91%010d", i);
            list.add(apaar);
            String name = (i == 1) ? "Asha Raman" : (i == 2 ? "Rahul Sharma" : (i == 3 ? "Michael John" : "Student " + i));
            LocalDate dob = (i == 1) ? LocalDate.of(2008, 3, 14) : LocalDate.of(2005, 5, 20);
            String gender = (i % 2 == 1) ? "F" : "M";
            batch.add(new Object[]{apaar, name, java.sql.Date.valueOf(dob), gender, "ACTIVE"});
        }

        // Add 5,980 additional synthetic IDs
        for (long i = 21; i <= 6000; i++) {
            String apaar = String.format("91%010d", i);
            list.add(apaar);
            String fn = firstNames.get(rng.nextInt(firstNames.size()));
            String ln = lastNames.get(rng.nextInt(lastNames.size()));
            LocalDate dob = LocalDate.of(2003 + rng.nextInt(6), 1 + rng.nextInt(12), 1 + rng.nextInt(28));
            String gender = (rng.nextBoolean()) ? "M" : "F";
            batch.add(new Object[]{apaar, fn + " " + ln, java.sql.Date.valueOf(dob), gender, "ACTIVE"});

            if (batch.size() >= 1000) {
                jdbc.batchUpdate("INSERT INTO apaar_registry_mock VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING", batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO apaar_registry_mock VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING", batch);
        }

        return list;
    }

    private void seedBaselineAndScenarios(Map<String, UUID> instMap, Map<String, UUID> progMap,
                                         List<String> mockApaars, Random rng) {
        log.info("Seeding baseline enrolments and planted scenarios S1-S10...");

        UUID tn001Id = instMap.get("INST-TN-001");
        UUID tn001CseId = progMap.get("INST-TN-001|BTECH-CSE");

        // INST-TN-001 CSE: approved intake 120, exactly 70 already enrolled!
        // Includes demo student 910000000001 (Asha Raman)
        UUID ashaId = UUID.randomUUID();
        jdbc.update("INSERT INTO students VALUES (?, '910000000001', 'Asha Raman', 'asha raman', '2008-03-14', 'F', 'hash-asha', now())", ashaId);
        UUID ashaEnrolId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO enrolments (id, student_id, institution_id, program_id, academic_year,
                                    institute_student_id, entry_type, residency_status, admission_quota,
                                    social_category, nationality, admission_date, status, seat_class, created_at, updated_at)
            VALUES (?, ?, ?, ?, '2026-27', 'CIT26CS0001', 'FIRST_YEAR', 'RESIDENT_INDIAN', 'STATE_CAP', 'OBC', 'IN', '2026-08-10', 'VALIDATED', 'REGULAR', now(), now())
        """, ashaEnrolId, ashaId, tn001Id, tn001CseId);

        // Active consent for TechCorp at L2
        jdbc.update("""
            INSERT INTO consents (id, student_id, grantee_type, grantee_id, grantee_name, scope, purpose, granted_at, expires_at)
            VALUES (?, ?, 'ORGANIZATION', 'TechCorp', 'TechCorp', 'L2', 'Pre-employment background verification', now(), now() + interval '90 days')
        """, UUID.randomUUID(), ashaId);

        // Pre-enrol remaining 69 regular students for INST-TN-001 CSE (making exactly 70)
        // Indices 1..70 (skipping index 3 which is 910000000004 / Vikram Sen in ECE)
        for (int i = 1; i <= 70; i++) {
            if (i == 3) continue; // Reserved for Demo Student 910000000004 (Vikram Sen)
            String apaar = mockApaars.get(i);
            UUID sId = UUID.randomUUID();
            jdbc.update("INSERT INTO students VALUES (?, ?, 'Student " + i + "', 'student " + i + "', '2005-05-10', 'M', 'hash-" + i + "', now())", sId, apaar);
            jdbc.update("""
                INSERT INTO enrolments (id, student_id, institution_id, program_id, academic_year,
                                        institute_student_id, entry_type, residency_status, admission_quota,
                                        social_category, nationality, admission_date, status, seat_class, created_at, updated_at)
                VALUES (?, ?, ?, ?, '2026-27', 'CIT26CS" + String.format("%04d", i) + "', 'FIRST_YEAR', 'RESIDENT_INDIAN', 'STATE_CAP', 'GEN', 'IN', '2026-08-12', 'VALIDATED', 'REGULAR', now(), now())
            """, UUID.randomUUID(), sId, tn001Id, tn001CseId);
        }

        // Demo Student 910000000004 (mockApaars index 3): Vikram Sen with a pending consent request from finco
        UUID finStuId = UUID.randomUUID();
        jdbc.update("INSERT INTO students VALUES (?, '910000000004', 'Vikram Sen', 'vikram sen', '2005-04-12', 'M', 'hash-vikram', now())", finStuId);
        UUID finEnrolId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO enrolments (id, student_id, institution_id, program_id, academic_year,
                                    institute_student_id, entry_type, residency_status, admission_quota,
                                    social_category, nationality, admission_date, status, seat_class, created_at, updated_at)
            VALUES (?, ?, ?, ?, '2026-27', 'CIT26ECE001', 'FIRST_YEAR', 'RESIDENT_INDIAN', 'STATE_CAP', 'GEN', 'IN', '2026-08-14', 'VALIDATED', 'REGULAR', now(), now())
        """, finEnrolId, finStuId, tn001Id, progMap.get("INST-TN-001|BTECH-ECE"));

        // Pending consent request from FinCo
        UUID hrFinId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'hr@finco.demo.vidyasetu.example'", UUID.class);
        jdbc.update("""
            INSERT INTO consent_requests (id, student_id, requester_user_id, requester_org, requested_scope, purpose, status, created_at)
            VALUES (?, ?, ?, 'FinCo', 'L2', 'Financial sector security clearance and academic verification', 'PENDING', now())
        """, UUID.randomUUID(), finStuId, hrFinId);

        // Scenario S1: INST-UP-001 CSE (intake 180) -> 197 regular enrolments (17 excess)
        UUID up001Id = instMap.get("INST-UP-001");
        UUID up001CseId = progMap.get("INST-UP-001|BTECH-CSE");
        for (int i = 1; i <= 197; i++) {
            String apaar = String.format("9101%08d", i);
            UUID sId = UUID.randomUUID();
            jdbc.update("INSERT INTO students VALUES (?, ?, 'UP Student " + i + "', 'up student " + i + "', '2005-06-15', 'M', 'hash-up-" + i + "', now()) ON CONFLICT DO NOTHING", sId, apaar);
            String seatClass = (i <= 180) ? "REGULAR" : "EXCESS";
            String status = (i <= 180) ? "VALIDATED" : "FLAGGED";
            UUID eId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO enrolments (id, student_id, institution_id, program_id, academic_year,
                                        institute_student_id, entry_type, residency_status, admission_quota,
                                        social_category, nationality, admission_date, status, seat_class, created_at, updated_at)
                VALUES (?, ?, ?, ?, '2026-27', 'GIE26CS" + String.format("%04d", i) + "', 'FIRST_YEAR', 'RESIDENT_INDIAN', 'STATE_CAP', 'GEN', 'IN', '2026-08-15', ?, ?, now(), now())
            """, eId, sId, up001Id, up001CseId, status, seatClass);

            if (i > 180) {
                jdbc.update("""
                    INSERT INTO validation_issues (id, enrolment_id, institution_id, rule_code, severity, field, message, hint, apaar_masked, status, created_at)
                    VALUES (?, ?, ?, 'V-INT-001', 'CRITICAL', 'program_code', 'Approved intake (180) exceeded for BTECH-CSE (Excess seat #' || ?, 'AICTE national alert raised', ?, 'OPEN', now())
                """, UUID.randomUUID(), eId, up001Id, (i - 180), Masking.maskApaar(apaar));
            }
        }

        // Scenario S2: INST-KA-001 AI&DS (intake 60) -> 14 FN/OCI supernumerary (cap 9)
        UUID ka001Id = instMap.get("INST-KA-001");
        UUID ka001AidsId = progMap.get("INST-KA-001|BTECH-AIDS");
        for (int i = 1; i <= 14; i++) {
            String apaar = String.format("9102%08d", i);
            UUID sId = UUID.randomUUID();
            jdbc.update("INSERT INTO students VALUES (?, ?, 'FN Student " + i + "', 'fn student " + i + "', '2005-07-20', 'M', 'hash-ka-" + i + "', now()) ON CONFLICT DO NOTHING", sId, apaar);
            String status = (i <= 9) ? "VALIDATED" : "FLAGGED";
            UUID eId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO enrolments (id, student_id, institution_id, program_id, academic_year,
                                        institute_student_id, entry_type, residency_status, admission_quota,
                                        social_category, nationality, passport_no_enc, admission_date, status, seat_class, created_at, updated_at)
                VALUES (?, ?, ?, ?, '2026-27', 'DIE26AI" + String.format("%04d", i) + "', 'FIRST_YEAR', 'FN', 'FN_OCI_SUPERNUM', 'GEN', 'US', 'enc-pass', '2026-08-18', ?, 'SUPERNUMERARY', now(), now())
            """, eId, sId, ka001Id, ka001AidsId, status);

            if (i > 9) {
                jdbc.update("""
                    INSERT INTO validation_issues (id, enrolment_id, institution_id, rule_code, severity, field, message, hint, apaar_masked, status, created_at)
                    VALUES (?, ?, ?, 'V-INT-002a', 'ERROR', 'admission_quota', 'FN/OCI/CIWGC supernumerary cap (9 seats, 15%) breached', 'Excess seats flagged', ?, 'OPEN', now())
                """, UUID.randomUUID(), eId, ka001Id, Masking.maskApaar(apaar));
            }
        }

        // Scenario S3: TN-002 <-> TN-001 cross-institution duplicates (14 APAAR IDs active in both, 1 legitimate transfer)
        UUID tn002Id = instMap.get("INST-TN-002");
        UUID tn002CseId = progMap.get("INST-TN-002|BTECH-CSE");
        for (int i = 1; i <= 14; i++) {
            String apaar = String.format("9103%08d", i);
            UUID sId = UUID.randomUUID();
            jdbc.update("INSERT INTO students VALUES (?, ?, 'Dual Student " + i + "', 'dual student " + i + "', '2005-08-10', 'M', 'hash-dual-" + i + "', now()) ON CONFLICT DO NOTHING", sId, apaar);

            // One legitimate transfer (student #1): status is TRANSFERRED at TN-002 and VALIDATED at TN-001
            String statusTn002 = (i == 1) ? "TRANSFERRED" : "FLAGGED";
            String statusTn001 = (i == 1) ? "VALIDATED" : "FLAGGED";

            UUID e1 = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO enrolments (id, student_id, institution_id, program_id, academic_year,
                                        institute_student_id, entry_type, residency_status, admission_quota,
                                        social_category, nationality, admission_date, status, seat_class, created_at, updated_at)
                VALUES (?, ?, ?, ?, '2026-27', 'KCE26CS" + String.format("%04d", i) + "', 'FIRST_YEAR', 'RESIDENT_INDIAN', 'STATE_CAP', 'GEN', 'IN', '2026-08-01', ?, 'REGULAR', now(), now())
            """, e1, sId, tn002Id, tn002CseId, statusTn002);

            UUID e2 = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO enrolments (id, student_id, institution_id, program_id, academic_year,
                                        institute_student_id, entry_type, residency_status, admission_quota,
                                        social_category, nationality, admission_date, status, seat_class, created_at, updated_at)
                VALUES (?, ?, ?, ?, '2026-27', 'CIT26ME" + String.format("%04d", i) + "', 'FIRST_YEAR', 'RESIDENT_INDIAN', 'STATE_CAP', 'GEN', 'IN', '2026-08-10', ?, 'REGULAR', now(), now())
            """, e2, sId, tn001Id, progMap.get("INST-TN-001|BTECH-MECH"), statusTn001);

            if (i > 1) {
                // Raise 13 x V-DUP-003 CRITICAL
                jdbc.update("""
                    INSERT INTO validation_issues (id, enrolment_id, institution_id, rule_code, severity, field, message, hint, apaar_masked, status, created_at)
                    VALUES (?, ?, ?, 'V-DUP-003', 'CRITICAL', 'apaar_id', 'Active enrolment for this APAAR already exists at INST-TN-001', 'AICTE alerted for dual enrolment review', ?, 'OPEN', now())
                """, UUID.randomUUID(), e1, tn002Id, Masking.maskApaar(apaar));

                jdbc.update("""
                    INSERT INTO validation_issues (id, enrolment_id, institution_id, rule_code, severity, field, message, hint, apaar_masked, status, created_at)
                    VALUES (?, ?, ?, 'V-DUP-003', 'CRITICAL', 'apaar_id', 'Active enrolment for this APAAR already exists at INST-TN-002', 'AICTE alerted for dual enrolment review', ?, 'OPEN', now())
                """, UUID.randomUUID(), e2, tn001Id, Masking.maskApaar(apaar));
            }
        }

        // Scenario S5: INST-GJ-001 -> 5 pairs of ghost students (V-DUP-004)
        UUID gj001Id = instMap.get("INST-GJ-001");
        UUID gj001CseId = progMap.get("INST-GJ-001|BTECH-CSE");
        for (int i = 1; i <= 5; i++) {
            String apaarA = String.format("9105%07d1", i);
            String apaarB = String.format("9105%07d2", i);

            UUID sA = UUID.randomUUID();
            UUID sB = UUID.randomUUID();
            jdbc.update("INSERT INTO students VALUES (?, ?, 'Ghost Student " + i + "', 'ghost student " + i + "', '2005-09-01', 'M', 'hash-ga-" + i + "', now()) ON CONFLICT DO NOTHING", sA, apaarA);
            jdbc.update("INSERT INTO students VALUES (?, ?, 'Ghost Student " + i + "', 'ghost student " + i + "', '2005-09-01', 'M', 'hash-gb-" + i + "', now()) ON CONFLICT DO NOTHING", sB, apaarB);

            UUID eA = UUID.randomUUID();
            UUID eB = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO enrolments (id, student_id, institution_id, program_id, academic_year,
                                        institute_student_id, entry_type, residency_status, admission_quota,
                                        social_category, nationality, admission_date, status, seat_class, created_at, updated_at)
                VALUES (?, ?, ?, ?, '2026-27', 'NCT26CS" + String.format("%04d", i*2) + "', 'FIRST_YEAR', 'RESIDENT_INDIAN', 'STATE_CAP', 'GEN', 'IN', '2026-08-10', 'FLAGGED', 'REGULAR', now(), now())
            """, eA, sA, gj001Id, gj001CseId);

            jdbc.update("""
                INSERT INTO validation_issues (id, enrolment_id, institution_id, rule_code, severity, field, message, hint, apaar_masked, status, created_at)
                VALUES (?, ?, ?, 'V-DUP-004', 'WARN', 'full_name', 'Potential ghost student or duplicate identity: identical DOB and near-identical name with different APAAR', 'Verify original credentials', ?, 'OPEN', now())
            """, UUID.randomUUID(), eA, gj001Id, Masking.maskApaar(apaarA));
        }

        // Scenario S6: INST-WB-001 -> 60 admissions on one day after cut-off (burst_admissions + late-admission WARNs)
        UUID wb001Id = instMap.get("INST-WB-001");
        UUID wb001CseId = progMap.get("INST-WB-001|BTECH-CSE");
        for (int i = 1; i <= 60; i++) {
            String apaar = String.format("9106%08d", i);
            UUID sId = UUID.randomUUID();
            jdbc.update("INSERT INTO students VALUES (?, ?, 'WB Student " + i + "', 'wb student " + i + "', '2005-10-10', 'M', 'hash-wb-" + i + "', now()) ON CONFLICT DO NOTHING", sId, apaar);
            jdbc.update("""
                INSERT INTO enrolments (id, student_id, institution_id, program_id, academic_year,
                                        institute_student_id, entry_type, residency_status, admission_quota,
                                        social_category, nationality, admission_date, status, seat_class, created_at, updated_at)
                VALUES (?, ?, ?, ?, '2026-27', 'HIT26CS" + String.format("%04d", i) + "', 'FIRST_YEAR', 'RESIDENT_INDIAN', 'STATE_CAP', 'GEN', 'IN', '2026-11-05', 'VALIDATED', 'REGULAR', now(), now())
            """, UUID.randomUUID(), sId, wb001Id, wb001CseId);
        }

        // Scenario S8: INST-DL-001 -> 6 FN rows with Indian nationality; 3 OCI without card number
        UUID dl001Id = instMap.get("INST-DL-001");
        for (int i = 1; i <= 6; i++) {
            jdbc.update("""
                INSERT INTO validation_issues (id, institution_id, rule_code, severity, field, message, hint, apaar_masked, status, created_at)
                VALUES (?, ?, 'V-CAT-001', 'ERROR', 'nationality', 'FN/OCI residency cannot have nationality as Indian (IN)', 'Enter foreign nationality', 'XXXX XXXX 8888', 'OPEN', now())
            """, UUID.randomUUID(), dl001Id);
        }
        for (int i = 1; i <= 3; i++) {
            jdbc.update("""
                INSERT INTO validation_issues (id, institution_id, rule_code, severity, field, message, hint, apaar_masked, status, created_at)
                VALUES (?, ?, 'V-CAT-001', 'ERROR', 'oci_card_no', 'OCI card number is mandatory for OCI students', 'Enter OCI card number', 'XXXX XXXX 8889', 'OPEN', now())
            """, UUID.randomUUID(), dl001Id);
        }

        // Scenario S9: INST-MH-001 -> 12 students whose name similarity to registry < 0.8
        UUID mh001Id = instMap.get("INST-MH-001");
        for (int i = 1; i <= 12; i++) {
            jdbc.update("""
                INSERT INTO validation_issues (id, institution_id, rule_code, severity, field, message, hint, apaar_masked, status, created_at)
                VALUES (?, ?, 'V-APAAR-005', 'WARN', 'full_name', 'Name differs slightly from registry (similarity 75%)', 'Confirm spelling variation', 'XXXX XXXX 9999', 'OPEN', now())
            """, UUID.randomUUID(), mh001Id);
        }

        // Scenario S4: INST-RJ-001 historic bad rows in remediation worklist
        UUID rj001Id = instMap.get("INST-RJ-001");
        UUID rjBatchId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO upload_batches (id, institution_id, uploaded_by, source, file_name, status, total_rows, accepted_rows, flagged_rows, rejected_rows, created_at, completed_at)
            VALUES (?, ?, 'admin.rj001@demo.vidyasetu.example', 'UI', 'initial_upload.csv', 'COMPLETED', 20, 15, 2, 3, now() - interval '2 days', now() - interval '2 days')
        """, rjBatchId, rj001Id);

        for (int i = 1; i <= 3; i++) {
            UUID sRowId = UUID.randomUUID();
            Map<String, String> raw = Map.of(
                    "apaar_id", "91070000000" + i,
                    "institute_student_id", "AIE26CS" + String.format("%04d", i),
                    "full_name", "Student Fix " + i,
                    "dob", "2005-04-10",
                    "gender", "M",
                    "program_code", "BTECH-CSE",
                    "admission_date", "2026-08-10"
            );
            jdbc.update("""
                INSERT INTO staging_rows (id, batch_id, row_no, raw, outcome)
                VALUES (?, ?, ?, ?, 'REJECTED')
            """, sRowId, rjBatchId, i, Json.write(raw));

            jdbc.update("""
                INSERT INTO validation_issues (id, batch_id, staging_row_id, institution_id, rule_code, severity, field, message, hint, apaar_masked, row_no, status, created_at)
                VALUES (?, ?, ?, ?, 'V-APAAR-004', 'ERROR', 'apaar_id', 'APAAR ID not found in registry', 'Verify with student', 'XXXX XXXX 000' || ?, ?, 'OPEN', now())
            """, UUID.randomUUID(), rjBatchId, sRowId, rj001Id, i, i);
        }
    }

    private void generateDemoCsvFiles(Map<String, UUID> instMap, List<String> mockApaars) {
        File seedDir = new File("seed");
        seedDir.mkdirs();

        // 1. `demo_upload_clean.csv` - 50 rows for INST-TN-001 CSE
        // Enrolling 50 more completes 70 + 50 = 120/120 approved intake!
        File cleanFile = new File(seedDir, "demo_upload_clean.csv");
        try (FileWriter fw = new FileWriter(cleanFile)) {
            fw.write("apaar_id,institute_student_id,full_name,dob,gender,nationality,residency_status,admission_quota,social_category,program_code,entry_type,admission_date,academic_year,passport_no,oci_card_no,mobile,email\n");
            for (int i = 71; i <= 120; i++) {
                String apaar = mockApaars.get(i);
                fw.write(String.format("%s,CIT26CS%04d,Clean Student %d,2005-06-12,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-20,2026-27,,,987654%04d,student%d@cit.demo\n",
                        apaar, i, i, i, i));
            }
        } catch (IOException e) {
            log.error("Failed to generate demo_upload_clean.csv: {}", e.getMessage());
        }

        // 2. `demo_upload_dirty.csv` - 60 rows for INST-RJ-001 (§17 counts: 42 accepted, 3 flagged, 15 rejected = 60)
        // 40 fully valid
        // 3 missing APAAR (V-APAAR-001)
        // 3 wrong length (V-APAAR-002)
        // 2 non-numeric (V-APAAR-002)
        // 2 not in registry (V-APAAR-004)
        // 2 duplicates of earlier rows (V-DUP-001)
        // 2 FN quota with Indian nationality & no passport (V-CAT-001)
        // 1 OCI without card number (V-CAT-001) -> Total 15 REJECTED
        // 3 already enrolled at another institution (V-DUP-003) -> 3 FLAGGED (CRITICAL)
        // 2 name mismatch vs registry (V-APAAR-005, WARN) -> ACCEPTED with warnings
        File dirtyFile = new File(seedDir, "demo_upload_dirty.csv");
        try (FileWriter fw = new FileWriter(dirtyFile)) {
            fw.write("apaar_id,institute_student_id,full_name,dob,gender,nationality,residency_status,admission_quota,social_category,program_code,entry_type,admission_date,academic_year,passport_no,oci_card_no,mobile,email\n");

            // 40 fully valid
            for (int i = 200; i < 240; i++) {
                String apaar = mockApaars.get(i);
                fw.write(String.format("%s,AIE26CS%04d,Valid Student %d,2005-04-15,F,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,987654%04d,valid%d@aie.demo\n",
                        apaar, i, i, i, i));
            }

            // 2 name mismatch vs registry (ACCEPTED with warnings) -> total 42 accepted
            fw.write(String.format("%s,AIE26CS0241,Aashaa Raamann,2008-03-14,F,IN,RESIDENT_INDIAN,STATE_CAP,OBC,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540241,warn1@aie.demo\n", mockApaars.get(241)));
            fw.write(String.format("%s,AIE26CS0242,Raahul Sharrrma,2005-05-20,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540242,warn2@aie.demo\n", mockApaars.get(242)));

            // 3 cross-institution duplicates (V-DUP-003) -> 3 FLAGGED
            // We use 910100000001, 910100000002, 910100000003 (enrolled at INST-UP-001)
            fw.write("910100000001,AIE26CS0243,UP Student 1,2005-06-15,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540243,dup1@aie.demo\n");
            fw.write("910100000002,AIE26CS0244,UP Student 2,2005-06-15,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540244,dup2@aie.demo\n");
            fw.write("910100000003,AIE26CS0245,UP Student 3,2005-06-15,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540245,dup3@aie.demo\n");

            // 15 REJECTED:
            // 3 missing APAAR
            fw.write(",AIE26CS0246,No Apaar 1,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540246,err1@aie.demo\n");
            fw.write(",AIE26CS0247,No Apaar 2,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540247,err2@aie.demo\n");
            fw.write(",AIE26CS0248,No Apaar 3,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540248,err3@aie.demo\n");

            // 3 wrong length
            fw.write("12345,AIE26CS0249,Short 1,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540249,err4@aie.demo\n");
            fw.write("12345678901234,AIE26CS0250,Long 1,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540250,err5@aie.demo\n");
            fw.write("9999,AIE26CS0251,Short 2,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540251,err6@aie.demo\n");

            // 2 non-numeric
            fw.write("91000000ABCD,AIE26CS0252,Non Num 1,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540252,err7@aie.demo\n");
            fw.write("91000000-XYZ,AIE26CS0253,Non Num 2,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540253,err8@aie.demo\n");

            // 2 not in registry
            fw.write("999999999991,AIE26CS0254,Not Reg 1,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540254,err9@aie.demo\n");
            fw.write("999999999992,AIE26CS0255,Not Reg 2,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540255,err10@aie.demo\n");

            // 2 duplicates of earlier rows
            String dupRowApaar1 = mockApaars.get(200);
            String dupRowApaar2 = mockApaars.get(201);
            fw.write(String.format("%s,AIE26CS0256,Batch Dup 1,2005-04-15,F,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540256,err11@aie.demo\n", dupRowApaar1));
            fw.write(String.format("%s,AIE26CS0257,Batch Dup 2,2005-04-15,F,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540257,err12@aie.demo\n", dupRowApaar2));

            // 2 FN quota with Indian nationality and no passport
            fw.write(String.format("%s,AIE26CS0258,FN Error 1,2005-05-10,M,IN,FN,FN_OCI_SUPERNUM,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540258,err13@aie.demo\n", mockApaars.get(258)));
            fw.write(String.format("%s,AIE26CS0259,FN Error 2,2005-05-10,M,IN,FN,FN_OCI_SUPERNUM,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540259,err14@aie.demo\n", mockApaars.get(259)));

            // 1 OCI without card number
            fw.write(String.format("%s,AIE26CS0260,OCI Error 1,2005-05-10,M,US,OCI,FN_OCI_SUPERNUM,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,PASS12345,,9876540260,err15@aie.demo\n", mockApaars.get(260)));
        } catch (IOException e) {
            log.error("Failed to generate demo_upload_dirty.csv: {}", e.getMessage());
        }

        // 3. `demo_upload_excess.csv` - 15 valid rows for INST-TN-001 CSE (uploaded after clean file)
        File excessFile = new File(seedDir, "demo_upload_excess.csv");
        try (FileWriter fw = new FileWriter(excessFile)) {
            fw.write("apaar_id,institute_student_id,full_name,dob,gender,nationality,residency_status,admission_quota,social_category,program_code,entry_type,admission_date,academic_year,passport_no,oci_card_no,mobile,email\n");
            for (int i = 121; i <= 135; i++) {
                String apaar = mockApaars.get(i);
                fw.write(String.format("%s,CIT26CS%04d,Excess Student %d,2005-06-15,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-25,2026-27,,,987654%04d,excess%d@cit.demo\n",
                        apaar, i, i, i, i));
            }
        } catch (IOException e) {
            log.error("Failed to generate demo_upload_excess.csv: {}", e.getMessage());
        }
    }
}
