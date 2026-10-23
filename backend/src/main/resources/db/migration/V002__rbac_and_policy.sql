-- Roles, permissions, role-permission matrix (master plan §13) and policy defaults (Appendix C)
INSERT INTO roles(code, description) VALUES
 ('SUPER_ADMIN','Platform super administrator'),
 ('AICTE_ADMIN','AICTE administrator'),
 ('AICTE_ANALYST','AICTE analyst'),
 ('AICTE_AUDITOR','AICTE auditor'),
 ('INSTITUTION_ADMIN','Institution administrator'),
 ('INSTITUTION_OPERATOR','Institution data operator'),
 ('REGULATOR','Regulator (UGC/NBA/NAAC/State)'),
 ('EMPLOYER_VERIFIER','Employer verifier'),
 ('STUDENT','Student'),
 ('API_CLIENT','Machine client (ERP)');

INSERT INTO permissions(code) VALUES
 ('institution:read'),('institution:manage'),('student:read_masked'),('student:read_full'),('student:write'),
 ('upload:create'),('upload:read'),('validation:read'),('validation:resolve'),('validation:waive'),
 ('anomaly:read'),('anomaly:review'),('dashboard:national'),('dashboard:institution'),
 ('report:generate'),('report:export'),('record:issue'),('record:revoke'),('verify:perform'),
 ('consent:manage_own'),('record:read_own'),('audit:read'),('user:manage'),('apiclient:manage'),
 ('policy:manage'),('registry:lookup');

-- SUPER_ADMIN: everything except student-self permissions
INSERT INTO role_permissions SELECT 'SUPER_ADMIN', code FROM permissions WHERE code NOT IN ('consent:manage_own','record:read_own');

INSERT INTO role_permissions(role_code, permission_code) VALUES
 ('AICTE_ADMIN','institution:read'),('AICTE_ADMIN','institution:manage'),('AICTE_ADMIN','student:read_masked'),('AICTE_ADMIN','student:read_full'),
 ('AICTE_ADMIN','upload:read'),('AICTE_ADMIN','validation:read'),('AICTE_ADMIN','validation:resolve'),('AICTE_ADMIN','validation:waive'),
 ('AICTE_ADMIN','anomaly:read'),('AICTE_ADMIN','anomaly:review'),('AICTE_ADMIN','dashboard:national'),('AICTE_ADMIN','dashboard:institution'),
 ('AICTE_ADMIN','report:generate'),('AICTE_ADMIN','report:export'),('AICTE_ADMIN','record:issue'),('AICTE_ADMIN','record:revoke'),
 ('AICTE_ADMIN','verify:perform'),('AICTE_ADMIN','audit:read'),('AICTE_ADMIN','user:manage'),('AICTE_ADMIN','apiclient:manage'),
 ('AICTE_ADMIN','policy:manage'),('AICTE_ADMIN','registry:lookup'),

 ('AICTE_ANALYST','institution:read'),('AICTE_ANALYST','student:read_masked'),('AICTE_ANALYST','upload:read'),('AICTE_ANALYST','validation:read'),
 ('AICTE_ANALYST','anomaly:read'),('AICTE_ANALYST','anomaly:review'),('AICTE_ANALYST','dashboard:national'),('AICTE_ANALYST','dashboard:institution'),
 ('AICTE_ANALYST','report:generate'),('AICTE_ANALYST','report:export'),

 ('AICTE_AUDITOR','institution:read'),('AICTE_AUDITOR','student:read_masked'),('AICTE_AUDITOR','upload:read'),('AICTE_AUDITOR','validation:read'),
 ('AICTE_AUDITOR','anomaly:read'),('AICTE_AUDITOR','dashboard:national'),('AICTE_AUDITOR','dashboard:institution'),
 ('AICTE_AUDITOR','report:generate'),('AICTE_AUDITOR','audit:read'),

 ('INSTITUTION_ADMIN','institution:read'),('INSTITUTION_ADMIN','student:read_masked'),('INSTITUTION_ADMIN','student:read_full'),('INSTITUTION_ADMIN','student:write'),
 ('INSTITUTION_ADMIN','upload:create'),('INSTITUTION_ADMIN','upload:read'),('INSTITUTION_ADMIN','validation:read'),('INSTITUTION_ADMIN','validation:resolve'),
 ('INSTITUTION_ADMIN','anomaly:read'),('INSTITUTION_ADMIN','dashboard:institution'),('INSTITUTION_ADMIN','report:generate'),('INSTITUTION_ADMIN','report:export'),
 ('INSTITUTION_ADMIN','record:issue'),('INSTITUTION_ADMIN','record:revoke'),('INSTITUTION_ADMIN','verify:perform'),('INSTITUTION_ADMIN','audit:read'),
 ('INSTITUTION_ADMIN','user:manage'),('INSTITUTION_ADMIN','apiclient:manage'),('INSTITUTION_ADMIN','registry:lookup'),

 ('INSTITUTION_OPERATOR','institution:read'),('INSTITUTION_OPERATOR','student:read_masked'),('INSTITUTION_OPERATOR','student:write'),
 ('INSTITUTION_OPERATOR','upload:create'),('INSTITUTION_OPERATOR','upload:read'),('INSTITUTION_OPERATOR','validation:read'),
 ('INSTITUTION_OPERATOR','validation:resolve'),('INSTITUTION_OPERATOR','dashboard:institution'),

 ('REGULATOR','institution:read'),('REGULATOR','student:read_masked'),('REGULATOR','anomaly:read'),('REGULATOR','dashboard:national'),
 ('REGULATOR','report:generate'),('REGULATOR','report:export'),('REGULATOR','verify:perform'),

 ('EMPLOYER_VERIFIER','verify:perform'),

 ('STUDENT','consent:manage_own'),('STUDENT','record:read_own'),

 ('API_CLIENT','student:write'),('API_CLIENT','upload:create'),('API_CLIENT','upload:read'),('API_CLIENT','validation:read');

INSERT INTO policy_config(key, value, description, updated_by) VALUES
 ('seatcap.fn_oci_pio_ciwgc_pct','15','FN/OCI/PIO/CIWGC supernumerary cap as % of approved intake','system'),
 ('seatcap.ciwgc_share_of_pool','0.3333','CIWGC share of the 15% supernumerary pool (interchangeable)','system'),
 ('seatcap.tfw_pct','5','Tuition Fee Waiver supernumerary cap as % of approved intake','system'),
 ('seatcap.nri_pct_within_intake','15','NRI quota cap within approved intake (% of intake, WARN)','system'),
 ('seatcap.rounding','HALF_UP','Rounding mode for seat caps (HALF_UP | FLOOR | CEIL)','system'),
 ('admission.cutoff_md','10-31','Admission cut-off (MM-DD) for late-admission warnings','system'),
 ('alerts.intake_warn_pct','90','Early warning threshold for intake fill %','system'),
 ('anomaly.weight.rules','0.45','Risk weight: rule detectors','system'),
 ('anomaly.weight.isolation','0.30','Risk weight: isolation forest','system'),
 ('anomaly.weight.stats','0.25','Risk weight: robust statistics','system'),
 ('anomaly.zscore_threshold','3.5','Robust z-score threshold','system'),
 ('anomaly.chronic_underenrolment_pct','30','Chronic under-enrolment fill % threshold','system'),
 ('anomaly.chronic_underenrolment_years','5','Consecutive years for chronic under-enrolment','system'),
 ('anomaly.burst_share_pct','40','Burst admissions: share of seats admitted on a single day','system'),
 ('identity.fuzzy_name_threshold','0.90','Name similarity for potential duplicate identity','system'),
 ('registry.name_warn_threshold','0.80','Registry name similarity below which a WARN is raised','system'),
 ('registry.name_error_threshold','0.50','Registry name similarity below which an ERROR is raised','system'),
 ('verification.consent_default_days','30','Default consent validity in days','system');
