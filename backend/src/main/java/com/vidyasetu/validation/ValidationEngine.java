package com.vidyasetu.validation;

import com.vidyasetu.apaar.ApaarRegistryClient;
import com.vidyasetu.apaar.ApaarService;
import com.vidyasetu.common.Masking;
import com.vidyasetu.masterdata.MasterDataService;
import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ValidationEngine {
    private static final Logger log = LoggerFactory.getLogger(ValidationEngine.class);
    private static final JaroWinklerDistance JARO_WINKLER = new JaroWinklerDistance();

    private final ApaarService apaarService;
    private final MasterDataService masterDataService;
    private final JdbcTemplate jdbc;

    public ValidationEngine(ApaarService apaarService, MasterDataService masterDataService, JdbcTemplate jdbc) {
        this.apaarService = apaarService;
        this.masterDataService = masterDataService;
        this.jdbc = jdbc;
    }

    public record NormalizedRow(
            int rowNo,
            Map<String, String> raw,
            String apaarId,
            String apaarMasked,
            String instituteStudentId,
            String fullName,
            String nameNorm,
            LocalDate dob,
            String gender,
            String nationality,
            String residencyStatus,
            String admissionQuota,
            String socialCategory,
            String programCode,
            String entryType,
            LocalDate admissionDate,
            String academicYear,
            String passportNo,
            String ociCardNo,
            String mobile,
            String email,
            List<ValidationIssue> issues,
            String outcome, // ACCEPTED, FLAGGED, REJECTED
            String seatClass, // REGULAR, SUPERNUMERARY, EXCESS
            boolean apaarVerified
    ) {
        public boolean hasError() {
            return issues.stream().anyMatch(i -> "ERROR".equals(i.severity()));
        }
        public boolean hasCritical() {
            return issues.stream().anyMatch(i -> "CRITICAL".equals(i.severity()));
        }
    }

    public static LocalDate parseDate(String val) {
        if (val == null || val.isBlank()) return null;
        String s = val.trim();
        List<DateTimeFormatter> formats = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("d-M-yyyy"),
                DateTimeFormatter.ofPattern("d/M/yyyy")
        );
        for (DateTimeFormatter dtf : formats) {
            try {
                return LocalDate.parse(s, dtf);
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static String normalizeName(String name) {
        if (name == null) return "";
        return name.toLowerCase().replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    /** Pre-parse raw map into preliminary NormalizedRow */
    public NormalizedRow parseRaw(int rowNo, Map<String, String> raw) {
        String rawApaar = raw.getOrDefault("apaar_id", raw.get("apaarId"));
        String apaarNorm = apaarService.normalize(rawApaar);
        String apaarMask = Masking.maskApaar(apaarNorm);

        String instStudentId = raw.getOrDefault("institute_student_id", raw.get("instituteStudentId"));
        String fullName = raw.getOrDefault("full_name", raw.get("fullName"));
        String dobStr = raw.getOrDefault("dob", raw.get("dateOfBirth"));
        LocalDate dob = parseDate(dobStr);
        String gender = raw.getOrDefault("gender", "");
        if (gender != null && !gender.isBlank()) gender = gender.trim().toUpperCase().substring(0, 1);

        String nationality = raw.getOrDefault("nationality", "IN");
        if (nationality != null) nationality = nationality.trim().toUpperCase();

        String residency = raw.getOrDefault("residency_status", raw.get("residencyStatus"));
        if (residency == null || residency.isBlank()) residency = "RESIDENT_INDIAN";
        else residency = residency.trim().toUpperCase();

        String quota = raw.getOrDefault("admission_quota", raw.get("admissionQuota"));
        if (quota == null || quota.isBlank()) quota = "STATE_CAP";
        else quota = quota.trim().toUpperCase();

        String category = raw.getOrDefault("social_category", raw.get("socialCategory"));
        if (category == null || category.isBlank()) category = "GEN";
        else category = category.trim().toUpperCase();

        String progCode = raw.getOrDefault("program_code", raw.get("programCode"));
        if (progCode != null) progCode = progCode.trim().toUpperCase();

        String entryType = raw.getOrDefault("entry_type", raw.get("entryType"));
        if (entryType == null || entryType.isBlank()) entryType = "FIRST_YEAR";
        else entryType = entryType.trim().toUpperCase();

        String admDateStr = raw.getOrDefault("admission_date", raw.get("admissionDate"));
        LocalDate admDate = parseDate(admDateStr);

        String acadYear = raw.getOrDefault("academic_year", raw.get("academicYear"));
        if (acadYear == null || acadYear.isBlank()) acadYear = "2026-27";
        else acadYear = acadYear.trim();

        String passport = raw.getOrDefault("passport_no", raw.get("passportNo"));
        String oci = raw.getOrDefault("oci_card_no", raw.get("ociCardNo"));
        String mobile = raw.getOrDefault("mobile", "");
        String email = raw.getOrDefault("email", "");

        return new NormalizedRow(
                rowNo, raw, apaarNorm, apaarMask, instStudentId, fullName, normalizeName(fullName),
                dob, gender, nationality, residency, quota, category, progCode, entryType, admDate,
                acadYear, passport, oci, mobile, email, new ArrayList<>(), "ACCEPTED", "REGULAR", true
        );
    }

    /**
     * Executes Group A, Group B, Group C checks for a batch of rows.
     */
    public List<NormalizedRow> validateRows(UUID institutionId, List<NormalizedRow> parsedRows) {
        Map<String, MasterDataService.ProgramDto> programsByCode = new HashMap<>();
        masterDataService.getPrograms(institutionId).forEach(p -> programsByCode.put(p.programCode(), p));

        double nameWarnThresh = masterDataService.getPolicyDouble("registry.name_warn_threshold", 0.80);
        double nameErrThresh = masterDataService.getPolicyDouble("registry.name_error_threshold", 0.50);
        double fuzzyDupThresh = masterDataService.getPolicyDouble("identity.fuzzy_name_threshold", 0.90);
        String cutoffMd = masterDataService.getPolicy("admission.cutoff_md", "10-31");

        Set<String> seenBatchApaars = new HashSet<>();
        Set<String> seenBatchStudentIds = new HashSet<>();

        List<NormalizedRow> processed = new ArrayList<>();

        for (NormalizedRow r : parsedRows) {
            List<ValidationIssue> issues = new ArrayList<>();
            boolean apaarVerified = true;

            // GROUP A: APAAR Identity
            if (r.apaarId() == null || r.apaarId().isBlank()) {
                issues.add(ValidationIssue.of("V-APAAR-001", "ERROR", "apaar_id",
                        "APAAR ID missing", "Provide the 12-digit academic ID", null, r.rowNo(), r.apaarMasked()));
            } else if (!apaarService.isValidLengthAndNumeric(r.apaarId())) {
                issues.add(ValidationIssue.of("V-APAAR-002", "ERROR", "apaar_id",
                        "APAAR ID must be exactly 12 numeric digits", "Remove spaces, hyphens, and non-digit characters", null, r.rowNo(), r.apaarMasked()));
            } else if (apaarService.isSuspiciousPattern(r.apaarId())) {
                issues.add(ValidationIssue.of("V-APAAR-003", "ERROR", "apaar_id",
                        "Suspicious APAAR ID pattern (repeated or sequential digits)", "Enter authentic APAAR ID", null, r.rowNo(), r.apaarMasked()));
            } else {
                // Check registry
                ApaarRegistryClient.ApaarLookupResult lookup = apaarService.lookup(r.apaarId());
                if (lookup.status() == ApaarRegistryClient.Status.NOT_FOUND) {
                    issues.add(ValidationIssue.of("V-APAAR-004", "ERROR", "apaar_id",
                            "APAAR ID not found or deactivated in registry", "Verify with student or DigiLocker ABC portal", null, r.rowNo(), r.apaarMasked()));
                } else if (lookup.status() == ApaarRegistryClient.Status.UNAVAILABLE) {
                    issues.add(ValidationIssue.of("V-APAAR-006", "WARN", "apaar_id",
                            "APAAR registry temporarily unavailable; accepted as unverified", "Auto-retry queued", null, r.rowNo(), r.apaarMasked()));
                    apaarVerified = false;
                } else if (lookup.status() == ApaarRegistryClient.Status.FOUND) {
                    // Match DOB
                    if (r.dob() != null && lookup.dob() != null && !r.dob().equals(lookup.dob())) {
                        issues.add(ValidationIssue.of("V-APAAR-005", "ERROR", "dob",
                                "Date of birth does not match APAAR registry (" + lookup.dob() + " vs " + r.dob() + ")",
                                "Correct the DOB to match registry or update APAAR record",
                                Map.of("registryDob", lookup.dob().toString(), "enteredDob", r.dob().toString()),
                                r.rowNo(), r.apaarMasked()));
                    }
                    // Match Name
                    if (r.fullName() != null && lookup.fullName() != null) {
                        double sim = JARO_WINKLER.apply(normalizeName(r.fullName()), normalizeName(lookup.fullName()));
                        if (sim < nameErrThresh) {
                            issues.add(ValidationIssue.of("V-APAAR-005", "ERROR", "full_name",
                                    "Name similarity (" + Math.round(sim * 100) + "%) below threshold against registry ('" + lookup.fullName() + "')",
                                    "Verify student identity and legal name on APAAR",
                                    Map.of("registryName", lookup.fullName(), "enteredName", r.fullName(), "similarity", sim),
                                    r.rowNo(), r.apaarMasked()));
                        } else if (sim < nameWarnThresh) {
                            issues.add(ValidationIssue.of("V-APAAR-005", "WARN", "full_name",
                                    "Name differs slightly from registry ('" + lookup.fullName() + "', similarity " + Math.round(sim * 100) + "%)",
                                    "Confirm spelling variation or expansion of initials",
                                    Map.of("registryName", lookup.fullName(), "enteredName", r.fullName(), "similarity", sim),
                                    r.rowNo(), r.apaarMasked()));
                        }
                    }
                    // Match Gender
                    if (r.gender() != null && !r.gender().isBlank() && lookup.gender() != null && !r.gender().equalsIgnoreCase(lookup.gender())) {
                        issues.add(ValidationIssue.of("V-APAAR-005", "WARN", "gender",
                                "Gender differs from registry (" + lookup.gender() + " vs " + r.gender() + ")",
                                "Verify gender on student profile", null, r.rowNo(), r.apaarMasked()));
                    }
                }
            }

            // GROUP B: Duplicates
            if (r.apaarId() != null && !r.apaarId().isBlank()) {
                if (!seenBatchApaars.add(r.apaarId())) {
                    issues.add(ValidationIssue.of("V-DUP-001", "ERROR", "apaar_id",
                            "Duplicate APAAR ID within this batch", "Remove duplicate record in file", null, r.rowNo(), r.apaarMasked()));
                } else {
                    // Check cross-institution duplicates (V-DUP-003): active enrolment at ANOTHER institution in same academic year
                    List<Map<String, Object>> otherInsts = jdbc.query("""
                        SELECT e.id, e.institution_id, i.aicte_id, i.name as inst_name, e.program_id, e.status
                        FROM enrolments e
                        JOIN students s ON s.id = e.student_id
                        JOIN institutions i ON i.id = e.institution_id
                        WHERE s.apaar_id = ? AND e.academic_year = ? AND e.institution_id != ?
                          AND e.status NOT IN ('WITHDRAWN', 'TRANSFERRED', 'GRADUATED')
                    """, (rs, rowNum) -> Map.of(
                            "enrolmentId", rs.getObject("id").toString(),
                            "institutionId", rs.getObject("institution_id").toString(),
                            "aicteId", rs.getString("aicte_id"),
                            "instName", rs.getString("inst_name"),
                            "status", rs.getString("status")
                    ), r.apaarId(), r.academicYear(), institutionId);

                    if (!otherInsts.isEmpty()) {
                        Map<String, Object> dupInfo = otherInsts.get(0);
                        issues.add(ValidationIssue.of("V-DUP-003", "CRITICAL", "apaar_id",
                                "Active enrolment for this APAAR already exists at " + dupInfo.get("aicteId") + " (" + dupInfo.get("instName") + ")",
                                "AICTE notified. Verify dual enrolment or initiate transfer clearance.",
                                dupInfo, r.rowNo(), r.apaarMasked()));
                    }

                    // Check same institution duplicate / update (V-DUP-002)
                    List<Map<String, Object>> sameInst = jdbc.query("""
                        SELECT e.id, e.status, s.dob, s.full_name
                        FROM enrolments e
                        JOIN students s ON s.id = e.student_id
                        JOIN programs p ON p.id = e.program_id
                        WHERE s.apaar_id = ? AND p.program_code = ? AND e.academic_year = ? AND e.institution_id = ?
                    """, (rs, rowNum) -> Map.of(
                            "enrolmentId", rs.getObject("id").toString(),
                            "dob", rs.getDate("dob") != null ? rs.getDate("dob").toString() : ""
                    ), r.apaarId(), r.programCode(), r.academicYear(), institutionId);

                    if (!sameInst.isEmpty()) {
                        String prevDob = (String) sameInst.get(0).get("dob");
                        if (r.dob() != null && !prevDob.isBlank() && !prevDob.equals(r.dob().toString())) {
                            issues.add(ValidationIssue.of("V-DUP-002", "ERROR", "dob",
                                    "Enrolment update conflicts with previously recorded protected identity field (DOB)",
                                    "Cannot alter DOB on existing record without formal re-verification", null, r.rowNo(), r.apaarMasked()));
                        } else {
                            issues.add(ValidationIssue.of("V-DUP-002", "INFO", "apaar_id",
                                    "Student already enrolled in this program/year; this entry updates existing record",
                                    "Existing enrolment will be updated with version history", null, r.rowNo(), r.apaarMasked()));
                        }
                    }
                }
            }

            // Institute Student ID reuse within institution (V-DUP-005)
            if (r.instituteStudentId() != null && !r.instituteStudentId().isBlank()) {
                if (!seenBatchStudentIds.add(r.instituteStudentId())) {
                    issues.add(ValidationIssue.of("V-DUP-005", "ERROR", "institute_student_id",
                            "Same student ID '" + r.instituteStudentId() + "' reused multiple times in batch",
                            "Assign unique student ID per student", null, r.rowNo(), r.apaarMasked()));
                } else {
                    List<String> diffApaars = jdbc.query("""
                        SELECT s.apaar_id FROM enrolments e
                        JOIN students s ON s.id = e.student_id
                        WHERE e.institution_id = ? AND e.institute_student_id = ? AND s.apaar_id != ?
                    """, (rs, rowNum) -> rs.getString("apaar_id"), institutionId, r.instituteStudentId(), r.apaarId() != null ? r.apaarId() : "");
                    if (!diffApaars.isEmpty()) {
                        issues.add(ValidationIssue.of("V-DUP-005", "ERROR", "institute_student_id",
                                "Student ID '" + r.instituteStudentId() + "' already assigned to a different APAAR in this institution",
                                "Assign unique student ID", null, r.rowNo(), r.apaarMasked()));
                    }
                }
            }

            // Fuzzy duplicate / ghost student (V-DUP-004)
            if (r.dob() != null && r.gender() != null && !r.nameNorm().isBlank() && r.apaarId() != null) {
                List<Map<String, Object>> candidates = jdbc.query("""
                    SELECT s.apaar_id, s.full_name, s.name_norm FROM students s
                    WHERE s.dob = ? AND s.gender = ? AND s.apaar_id != ?
                """, (rs, rowNum) -> Map.of(
                        "apaar", rs.getString("apaar_id"),
                        "name", rs.getString("full_name"),
                        "nameNorm", rs.getString("name_norm")
                ), java.sql.Date.valueOf(r.dob()), r.gender(), r.apaarId());

                for (Map<String, Object> c : candidates) {
                    double sim = JARO_WINKLER.apply(r.nameNorm(), (String) c.get("nameNorm"));
                    if (sim >= fuzzyDupThresh) {
                        issues.add(ValidationIssue.of("V-DUP-004", "WARN", "full_name",
                                "Potential ghost student or duplicate identity: matches " + Masking.maskApaar((String) c.get("apaar")) +
                                        " ('" + c.get("name") + "') with identical DOB and similarity " + Math.round(sim * 100) + "%",
                                "AICTE risk monitor alerted. Verify original identity credentials.",
                                Map.of("matchedApaar", Masking.maskApaar((String) c.get("apaar")), "similarity", sim),
                                r.rowNo(), r.apaarMasked()));
                        break;
                    }
                }
            }

            // GROUP C: Data Consistency
            if (r.fullName() == null || r.fullName().isBlank()) {
                issues.add(ValidationIssue.of("V-DATA-001", "ERROR", "full_name", "Full name is required", "Provide full legal name", null, r.rowNo(), r.apaarMasked()));
            }
            if (r.dob() == null) {
                issues.add(ValidationIssue.of("V-DATA-001", "ERROR", "dob", "Valid Date of Birth is required", "Format YYYY-MM-DD or DD-MM-YYYY", null, r.rowNo(), r.apaarMasked()));
            } else {
                if (r.dob().isAfter(LocalDate.now())) {
                    issues.add(ValidationIssue.of("V-DATA-002", "ERROR", "dob", "DOB cannot be in the future", "Check birth date", null, r.rowNo(), r.apaarMasked()));
                }
            }

            if (r.admissionDate() == null) {
                issues.add(ValidationIssue.of("V-DATA-001", "ERROR", "admission_date", "Valid admission date is required", "Format YYYY-MM-DD", null, r.rowNo(), r.apaarMasked()));
            } else {
                if (r.admissionDate().isAfter(LocalDate.now())) {
                    issues.add(ValidationIssue.of("V-DATA-003", "ERROR", "admission_date", "Admission date cannot be in the future", "Check admission date", null, r.rowNo(), r.apaarMasked()));
                }
                // Check cut-off warning (e.g. 10-31)
                try {
                    String[] parts = cutoffMd.split("-");
                    int year = r.admissionDate().getYear();
                    LocalDate cutoff = LocalDate.of(year, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                    if (r.admissionDate().isAfter(cutoff)) {
                        issues.add(ValidationIssue.of("V-DATA-003", "WARN", "admission_date",
                                "Late admission: admitted on " + r.admissionDate() + " after cut-off date " + cutoff,
                                "Ensure regulatory approval or extension order for late admission", null, r.rowNo(), r.apaarMasked()));
                    }
                } catch (Exception ignored) {}
            }

            MasterDataService.ProgramDto prog = r.programCode() != null ? programsByCode.get(r.programCode()) : null;
            if (r.programCode() == null || r.programCode().isBlank() || prog == null) {
                issues.add(ValidationIssue.of("V-DATA-004", "ERROR", "program_code",
                        "Program code '" + r.programCode() + "' is not approved for this institution",
                        "Select an approved program code from master data", null, r.rowNo(), r.apaarMasked()));
            } else {
                // Age vs level check
                if (r.dob() != null && r.admissionDate() != null) {
                    int age = r.admissionDate().getYear() - r.dob().getYear();
                    if ("DIPLOMA".equalsIgnoreCase(prog.level()) && (age < 14 || age > 30)) {
                        issues.add(ValidationIssue.of("V-DATA-002", "WARN", "dob", "Age at admission (" + age + ") is unusual for Diploma level (expected 14-30)", "Verify age", null, r.rowNo(), r.apaarMasked()));
                    } else if ("UG".equalsIgnoreCase(prog.level()) && (age < 16 || age > 35)) {
                        issues.add(ValidationIssue.of("V-DATA-002", "WARN", "dob", "Age at admission (" + age + ") is unusual for UG level (expected 16-35)", "Verify age", null, r.rowNo(), r.apaarMasked()));
                    } else if (("PG".equalsIgnoreCase(prog.level()) || "PGDM".equalsIgnoreCase(prog.level())) && (age < 20 || age > 55)) {
                        issues.add(ValidationIssue.of("V-DATA-002", "WARN", "dob", "Age at admission (" + age + ") is unusual for PG level (expected 20-55)", "Verify age", null, r.rowNo(), r.apaarMasked()));
                    }
                }

                // Lateral entry check
                if ("LATERAL".equalsIgnoreCase(r.entryType()) && prog.approvedLateralIntake() <= 0) {
                    issues.add(ValidationIssue.of("V-DATA-005", "ERROR", "entry_type",
                            "Lateral entry is not permitted for program '" + r.programCode() + "' (approved lateral intake is 0)",
                            "Check entry type or program approval", null, r.rowNo(), r.apaarMasked()));
                }

                // Supernumerary check vs program enabled flags
                boolean isFnOciSuper = "FN_OCI_SUPERNUM".equalsIgnoreCase(r.admissionQuota()) || "CIWGC_SUPERNUM".equalsIgnoreCase(r.admissionQuota());
                if (isFnOciSuper && !prog.fnOciEnabled()) {
                    issues.add(ValidationIssue.of("V-CAT-002", "ERROR", "admission_quota",
                            "Supernumerary quota '" + r.admissionQuota() + "' used but program is not approved for FN/OCI supernumerary intake",
                            "Program must be enabled for FN/OCI supernumerary seats in AICTE approval", null, r.rowNo(), r.apaarMasked()));
                }
                boolean isTfwSuper = "TFW_SUPERNUM".equalsIgnoreCase(r.admissionQuota());
                if (isTfwSuper && !prog.tfwEnabled()) {
                    issues.add(ValidationIssue.of("V-CAT-002", "ERROR", "admission_quota",
                            "TFW quota used but program is not approved for Tuition Fee Waiver",
                            "Program must be enabled for TFW in AICTE approval", null, r.rowNo(), r.apaarMasked()));
                }
            }

            // V-CAT-001: Residency & documentation consistency
            boolean isFnOrOciRes = "FN".equalsIgnoreCase(r.residencyStatus()) || "OCI".equalsIgnoreCase(r.residencyStatus()) || "PIO".equalsIgnoreCase(r.residencyStatus());
            boolean isFnOrOciQuota = "FN_OCI_SUPERNUM".equalsIgnoreCase(r.admissionQuota()) || "CIWGC_SUPERNUM".equalsIgnoreCase(r.admissionQuota());

            if (isFnOrOciRes || isFnOrOciQuota) {
                if ("IN".equalsIgnoreCase(r.nationality())) {
                    issues.add(ValidationIssue.of("V-CAT-001", "ERROR", "nationality",
                            "FN/OCI residency or quota cannot have nationality as Indian ('IN')",
                            "Provide foreign nationality code", null, r.rowNo(), r.apaarMasked()));
                }
                if (r.passportNo() == null || r.passportNo().isBlank()) {
                    issues.add(ValidationIssue.of("V-CAT-001", "ERROR", "passport_no",
                            "Passport number is mandatory for FN / OCI students",
                            "Enter passport number", null, r.rowNo(), r.apaarMasked()));
                }
                if ("OCI".equalsIgnoreCase(r.residencyStatus()) && (r.ociCardNo() == null || r.ociCardNo().isBlank())) {
                    issues.add(ValidationIssue.of("V-CAT-001", "ERROR", "oci_card_no",
                            "OCI card number is mandatory for OCI students",
                            "Enter OCI card number", null, r.rowNo(), r.apaarMasked()));
                }
            }

            if ("RESIDENT_INDIAN".equalsIgnoreCase(r.residencyStatus()) && isFnOrOciQuota) {
                issues.add(ValidationIssue.of("V-CAT-001", "ERROR", "residency_status",
                        "Resident Indian student cannot be admitted under FN/OCI/CIWGC supernumerary quota",
                        "Check admission quota or residency status", null, r.rowNo(), r.apaarMasked()));
            }

            if ("NRI".equalsIgnoreCase(r.admissionQuota()) && !"NRI".equalsIgnoreCase(r.residencyStatus())) {
                issues.add(ValidationIssue.of("V-CAT-001", "ERROR", "admission_quota",
                        "NRI quota admission requires residency status to be NRI",
                        "Change residency status to NRI or select standard quota", null, r.rowNo(), r.apaarMasked()));
            }

            if (("NRI".equalsIgnoreCase(r.admissionQuota()) || isFnOrOciQuota) && !"GEN".equalsIgnoreCase(r.socialCategory())) {
                issues.add(ValidationIssue.of("V-CAT-003", "WARN", "social_category",
                        "Reservation categories do not apply to NRI / Foreign National quotas",
                        "Social category will be treated as General for seat allocation", null, r.rowNo(), r.apaarMasked()));
            }

            // Decide outcome for this row:
            // If any ERROR -> REJECTED
            // If any CRITICAL -> FLAGGED
            // Else -> ACCEPTED
            String outcome = "ACCEPTED";
            if (issues.stream().anyMatch(i -> "ERROR".equals(i.severity()))) {
                outcome = "REJECTED";
            } else if (issues.stream().anyMatch(i -> "CRITICAL".equals(i.severity()))) {
                outcome = "FLAGGED";
            }

            processed.add(new NormalizedRow(
                    r.rowNo(), r.raw(), r.apaarId(), r.apaarMasked(), r.instituteStudentId(),
                    r.fullName(), r.nameNorm(), r.dob(), r.gender(), r.nationality(), r.residencyStatus(),
                    r.admissionQuota(), r.socialCategory(), r.programCode(), r.entryType(),
                    r.admissionDate(), r.academicYear(), r.passportNo(), r.ociCardNo(), r.mobile(),
                    r.email(), issues, outcome, "REGULAR", apaarVerified
            ));
        }

        return processed;
    }

    /**
     * Executes Group D (batch-level seat and intake cap checks) in order of (admission_date, row_no).
     * Modifies seatClass and adds V-INT issues to rows.
     */
    public List<NormalizedRow> validateIntakeCaps(UUID institutionId, List<NormalizedRow> rows) {
        Map<String, MasterDataService.ProgramDto> programsByCode = new HashMap<>();
        masterDataService.getPrograms(institutionId).forEach(p -> programsByCode.put(p.programCode(), p));

        double fnOciCapPct = masterDataService.getPolicyDouble("seatcap.fn_oci_pio_ciwgc_pct", 15.0);
        double tfwCapPct = masterDataService.getPolicyDouble("seatcap.tfw_pct", 5.0);
        double nriCapPct = masterDataService.getPolicyDouble("seatcap.nri_pct_within_intake", 15.0);

        // Group rows that are ACCEPTED or FLAGGED by program_code
        Map<String, List<NormalizedRow>> byProgram = new LinkedHashMap<>();
        for (NormalizedRow r : rows) {
            if (!"REJECTED".equals(r.outcome()) && r.programCode() != null) {
                byProgram.computeIfAbsent(r.programCode(), k -> new ArrayList<>()).add(r);
            }
        }

        Map<Integer, NormalizedRow> updatedRowsByRowNo = new HashMap<>();

        for (Map.Entry<String, List<NormalizedRow>> entry : byProgram.entrySet()) {
            String progCode = entry.getKey();
            List<NormalizedRow> progRows = entry.getValue();
            MasterDataService.ProgramDto prog = programsByCode.get(progCode);
            if (prog == null) continue;

            // Fetch already committed counts from database for this program in 2026-27
            Map<String, Object> existingCounts = jdbc.query("""
                SELECT
                    COUNT(*) FILTER (WHERE seat_class = 'REGULAR' AND entry_type = 'FIRST_YEAR') as regular_count,
                    COUNT(*) FILTER (WHERE admission_quota = 'FN_OCI_SUPERNUM') as fn_count,
                    COUNT(*) FILTER (WHERE admission_quota = 'CIWGC_SUPERNUM') as ciwgc_count,
                    COUNT(*) FILTER (WHERE admission_quota = 'TFW_SUPERNUM') as tfw_count,
                    COUNT(*) FILTER (WHERE admission_quota = 'NRI') as nri_count,
                    COUNT(*) FILTER (WHERE entry_type = 'LATERAL') as lateral_count
                FROM enrolments
                WHERE program_id = ? AND academic_year = '2026-27' AND status NOT IN ('WITHDRAWN')
            """, rs -> {
                if (rs.next()) {
                    return Map.of(
                            "regular", rs.getInt("regular_count"),
                            "fn", rs.getInt("fn_count"),
                            "ciwgc", rs.getInt("ciwgc_count"),
                            "tfw", rs.getInt("tfw_count"),
                            "nri", rs.getInt("nri_count"),
                            "lateral", rs.getInt("lateral_count")
                    );
                }
                return Map.of("regular", 0, "fn", 0, "ciwgc", 0, "tfw", 0, "nri", 0, "lateral", 0);
            }, prog.id());

            int currRegular = (int) existingCounts.get("regular");
            int currFn = (int) existingCounts.get("fn");
            int currCiwgc = (int) existingCounts.get("ciwgc");
            int currTfw = (int) existingCounts.get("tfw");
            int currNri = (int) existingCounts.get("nri");
            int currLateral = (int) existingCounts.get("lateral");

            int approvedIntake = prog.approvedIntake();
            int approvedLateral = prog.approvedLateralIntake();
            int fnOciPoolCap = (int) Math.round(approvedIntake * (fnOciCapPct / 100.0));
            int tfwCap = (int) Math.round(approvedIntake * (tfwCapPct / 100.0));
            int nriCap = (int) Math.round(approvedIntake * (nriCapPct / 100.0));

            // Sort incoming rows: admission_date ASC, then rowNo ASC
            progRows.sort(Comparator.comparing(NormalizedRow::admissionDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparingInt(NormalizedRow::rowNo));

            for (NormalizedRow r : progRows) {
                List<ValidationIssue> issues = new ArrayList<>(r.issues());
                String outcome = r.outcome();
                String seatClass = "REGULAR";

                if ("LATERAL".equalsIgnoreCase(r.entryType())) {
                    currLateral++;
                    if (approvedLateral > 0 && currLateral > approvedLateral) {
                        issues.add(ValidationIssue.of("V-INT-004", "ERROR", "entry_type",
                                "Lateral intake limit (" + approvedLateral + ") exceeded (admitted count: " + currLateral + ")",
                                "Lateral seats cannot exceed approved lateral intake", null, r.rowNo(), r.apaarMasked()));
                        outcome = "REJECTED";
                    }
                } else if ("FN_OCI_SUPERNUM".equalsIgnoreCase(r.admissionQuota()) || "CIWGC_SUPERNUM".equalsIgnoreCase(r.admissionQuota())) {
                    seatClass = "SUPERNUMERARY";
                    int totalPoolBefore = currFn + currCiwgc;
                    if ("CIWGC_SUPERNUM".equalsIgnoreCase(r.admissionQuota())) currCiwgc++;
                    else currFn++;

                    if (totalPoolBefore + 1 > fnOciPoolCap) {
                        issues.add(ValidationIssue.of("V-INT-002a", "ERROR", "admission_quota",
                                "FN/OCI/CIWGC supernumerary cap (" + fnOciPoolCap + ", 15%) breached for program " + progCode,
                                "Excess supernumerary admissions are flagged for AICTE review",
                                Map.of("poolCap", fnOciPoolCap, "currentCount", totalPoolBefore + 1),
                                r.rowNo(), r.apaarMasked()));
                        outcome = "FLAGGED";
                    }
                } else if ("TFW_SUPERNUM".equalsIgnoreCase(r.admissionQuota())) {
                    seatClass = "SUPERNUMERARY";
                    currTfw++;
                    if (currTfw > tfwCap) {
                        issues.add(ValidationIssue.of("V-INT-002c", "ERROR", "admission_quota",
                                "Tuition Fee Waiver (TFW) cap (" + tfwCap + ", 5%) breached for program " + progCode,
                                "TFW supernumerary seats cannot exceed 5% of approved intake",
                                Map.of("tfwCap", tfwCap, "currentTfw", currTfw),
                                r.rowNo(), r.apaarMasked()));
                        outcome = "FLAGGED";
                    }
                } else {
                    // Regular seat
                    currRegular++;
                    if ("NRI".equalsIgnoreCase(r.admissionQuota())) {
                        currNri++;
                        if (currNri > nriCap) {
                            issues.add(ValidationIssue.of("V-INT-002d", "WARN", "admission_quota",
                                    "NRI admissions (" + currNri + ") exceed policy guideline (" + nriCap + ", 15%)",
                                    "Verify state quota notifications", null, r.rowNo(), r.apaarMasked()));
                        }
                    }

                    if (currRegular > approvedIntake) {
                        seatClass = "EXCESS";
                        outcome = "FLAGGED";
                        int excessCount = currRegular - approvedIntake;
                        issues.add(ValidationIssue.of("V-INT-001", "CRITICAL", "program_code",
                                "Approved intake of " + approvedIntake + " exceeded for " + progCode + " (Regular seat #" + currRegular + ", Excess #" + excessCount + ")",
                                "AICTE national alert raised. Excess admission beyond approved intake.",
                                Map.of("approvedIntake", approvedIntake, "currentRegular", currRegular, "excess", excessCount),
                                r.rowNo(), r.apaarMasked()));
                    } else if (currRegular == approvedIntake) {
                        issues.add(ValidationIssue.of("V-INT-003", "INFO", "program_code",
                                "Approved intake capacity (100% - " + approvedIntake + " seats) reached for " + progCode,
                                "Intake fully utilized", null, r.rowNo(), r.apaarMasked()));
                    } else if (approvedIntake > 0 && currRegular >= Math.round(approvedIntake * 0.90)) {
                        issues.add(ValidationIssue.of("V-INT-003", "WARN", "program_code",
                                "Early warning: " + Math.round((double) currRegular / approvedIntake * 100) + "% of approved intake filled (" + currRegular + "/" + approvedIntake + ")",
                                "Approaching capacity limit", null, r.rowNo(), r.apaarMasked()));
                    }
                }

                updatedRowsByRowNo.put(r.rowNo(), new NormalizedRow(
                        r.rowNo(), r.raw(), r.apaarId(), r.apaarMasked(), r.instituteStudentId(),
                        r.fullName(), r.nameNorm(), r.dob(), r.gender(), r.nationality(), r.residencyStatus(),
                        r.admissionQuota(), r.socialCategory(), r.programCode(), r.entryType(),
                        r.admissionDate(), r.academicYear(), r.passportNo(), r.ociCardNo(), r.mobile(),
                        r.email(), issues, outcome, seatClass, r.apaarVerified()
                ));
            }
        }

        // Return all rows maintaining original order
        List<NormalizedRow> result = new ArrayList<>();
        for (NormalizedRow r : rows) {
            if (updatedRowsByRowNo.containsKey(r.rowNo())) {
                result.add(updatedRowsByRowNo.get(r.rowNo()));
            } else {
                result.add(r);
            }
        }
        return result;
    }
}
