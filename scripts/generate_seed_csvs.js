const fs = require('fs');
const path = require('path');

const seedDir = path.resolve(__dirname, '..', 'seed');
if (!fs.existsSync(seedDir)) {
  fs.mkdirSync(seedDir, { recursive: true });
}

function apaar(num) {
  return '91' + String(num).padStart(10, '0');
}

// 1. demo_upload_clean.csv (50 rows for INST-TN-001 CSE)
const cleanRows = [
  'apaar_id,institute_student_id,full_name,dob,gender,nationality,residency_status,admission_quota,social_category,program_code,entry_type,admission_date,academic_year,passport_no,oci_card_no,mobile,email'
];
for (let i = 71; i <= 120; i++) {
  const ap = apaar(i);
  const sid = 'CIT26CS' + String(i).padStart(4, '0');
  cleanRows.push(`${ap},${sid},Clean Student ${i},2005-06-12,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-20,2026-27,,,987654${String(i).padStart(4, '0')},student${i}@cit.demo`);
}
fs.writeFileSync(path.join(seedDir, 'demo_upload_clean.csv'), cleanRows.join('\n') + '\n');
console.log('Generated demo_upload_clean.csv: ' + (cleanRows.length - 1) + ' rows');

// 2. demo_upload_dirty.csv (60 rows for INST-RJ-001)
const dirtyRows = [
  'apaar_id,institute_student_id,full_name,dob,gender,nationality,residency_status,admission_quota,social_category,program_code,entry_type,admission_date,academic_year,passport_no,oci_card_no,mobile,email'
];
// 40 fully valid
for (let i = 200; i < 240; i++) {
  const ap = apaar(i);
  const sid = 'AIE26CS' + String(i).padStart(4, '0');
  dirtyRows.push(`${ap},${sid},Valid Student ${i},2005-04-15,F,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,987654${String(i).padStart(4, '0')},valid${i}@aie.demo`);
}
// 2 name mismatch vs registry (ACCEPTED with warnings) -> 42 accepted
dirtyRows.push(`${apaar(241)},AIE26CS0241,Aashaa Raamann,2008-03-14,F,IN,RESIDENT_INDIAN,STATE_CAP,OBC,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540241,warn1@aie.demo`);
dirtyRows.push(`${apaar(242)},AIE26CS0242,Raahul Sharrrma,2005-05-20,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540242,warn2@aie.demo`);

// 3 cross-institution duplicates (V-DUP-003) -> 3 FLAGGED
dirtyRows.push('910100000001,AIE26CS0243,UP Student 1,2005-06-15,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540243,dup1@aie.demo');
dirtyRows.push('910100000002,AIE26CS0244,UP Student 2,2005-06-15,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540244,dup2@aie.demo');
dirtyRows.push('910100000003,AIE26CS0245,UP Student 3,2005-06-15,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540245,dup3@aie.demo');

// 15 REJECTED:
// 3 missing APAAR
dirtyRows.push(',AIE26CS0246,No Apaar 1,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540246,err1@aie.demo');
dirtyRows.push(',AIE26CS0247,No Apaar 2,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540247,err2@aie.demo');
dirtyRows.push(',AIE26CS0248,No Apaar 3,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540248,err3@aie.demo');
// 3 wrong length
dirtyRows.push('12345,AIE26CS0249,Short 1,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540249,err4@aie.demo');
dirtyRows.push('12345678901234,AIE26CS0250,Long 1,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540250,err5@aie.demo');
dirtyRows.push('9999,AIE26CS0251,Short 2,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540251,err6@aie.demo');
// 2 non-numeric
dirtyRows.push('91000000ABCD,AIE26CS0252,Non Num 1,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540252,err7@aie.demo');
dirtyRows.push('91000000-XYZ,AIE26CS0253,Non Num 2,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540253,err8@aie.demo');
// 2 not in registry
dirtyRows.push('999999999991,AIE26CS0254,Not Reg 1,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540254,err9@aie.demo');
dirtyRows.push('999999999992,AIE26CS0255,Not Reg 2,2005-05-10,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540255,err10@aie.demo');
// 2 duplicates of earlier rows
dirtyRows.push(`${apaar(200)},AIE26CS0256,Batch Dup 1,2005-04-15,F,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540256,err11@aie.demo`);
dirtyRows.push(`${apaar(201)},AIE26CS0257,Batch Dup 2,2005-04-15,F,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540257,err12@aie.demo`);
// 2 FN quota with Indian nationality and no passport
dirtyRows.push(`${apaar(258)},AIE26CS0258,FN Error 1,2005-05-10,M,IN,FN,FN_OCI_SUPERNUM,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540258,err13@aie.demo`);
dirtyRows.push(`${apaar(259)},AIE26CS0259,FN Error 2,2005-05-10,M,IN,FN,FN_OCI_SUPERNUM,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,,,9876540259,err14@aie.demo`);
// 1 OCI without card number
dirtyRows.push(`${apaar(260)},AIE26CS0260,OCI Error 1,2005-05-10,M,US,OCI,FN_OCI_SUPERNUM,GEN,BTECH-CSE,FIRST_YEAR,2026-08-22,2026-27,PASS12345,,9876540260,err15@aie.demo`);

fs.writeFileSync(path.join(seedDir, 'demo_upload_dirty.csv'), dirtyRows.join('\n') + '\n');
console.log('Generated demo_upload_dirty.csv: ' + (dirtyRows.length - 1) + ' rows');

// 3. demo_upload_excess.csv (15 valid rows for INST-TN-001 CSE)
const excessRows = [
  'apaar_id,institute_student_id,full_name,dob,gender,nationality,residency_status,admission_quota,social_category,program_code,entry_type,admission_date,academic_year,passport_no,oci_card_no,mobile,email'
];
for (let i = 121; i <= 135; i++) {
  const ap = apaar(i);
  const sid = 'CIT26CS' + String(i).padStart(4, '0');
  excessRows.push(`${ap},${sid},Excess Student ${i},2005-06-15,M,IN,RESIDENT_INDIAN,STATE_CAP,GEN,BTECH-CSE,FIRST_YEAR,2026-08-25,2026-27,,,987654${String(i).padStart(4, '0')},excess${i}@cit.demo`);
}
fs.writeFileSync(path.join(seedDir, 'demo_upload_excess.csv'), excessRows.join('\n') + '\n');
console.log('Generated demo_upload_excess.csv: ' + (excessRows.length - 1) + ' rows');
