package com.vidyasetu.verification;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.vidyasetu.common.ApiException;
import com.vidyasetu.common.Masking;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class CertificateService {
    private final JdbcTemplate jdbc;
    private final VerificationService verificationService;
    private final boolean demoMode;
    private final String publicBaseUrl;

    public CertificateService(JdbcTemplate jdbc, VerificationService verificationService,
                              @Value("${app.demo-mode:true}") boolean demoMode,
                              @Value("${app.public-base-url:http://localhost:5173}") String publicBaseUrl) {
        this.jdbc = jdbc;
        this.verificationService = verificationService;
        this.demoMode = demoMode;
        this.publicBaseUrl = publicBaseUrl;
    }

    public byte[] generateCertificatePdf(UUID enrolmentId) {
        Map<String, Object> data = jdbc.queryForMap("""
            SELECT e.id as enrolment_id, e.institute_student_id, e.academic_year, e.admission_date,
                   s.id as student_id, s.apaar_id, s.full_name, s.dob,
                   i.aicte_id, i.name as inst_name, i.state,
                   p.program_code, p.name as prog_name, p.level
            FROM enrolments e
            JOIN students s ON s.id = e.student_id
            JOIN institutions i ON i.id = e.institution_id
            JOIN programs p ON p.id = e.program_id
            WHERE e.id = ?
        """, enrolmentId);

        // Ensure record exists or issue one to generate JTI for QR
        var recs = verificationService.getRecordsByEnrolment(enrolmentId);
        String jti;
        if (recs.isEmpty()) {
            var newRec = verificationService.issueRecord(enrolmentId);
            jti = newRec.jti();
        } else {
            jti = recs.get(0).jti();
        }

        String qrUrl = publicBaseUrl + "/v/" + jti;
        String studentId = (String) data.get("institute_student_id");
        if (studentId == null || studentId.isBlank()) studentId = "STU-" + enrolmentId.toString().substring(0, 8).toUpperCase();
        String apaar = (String) data.get("apaar_id");
        String formattedApaar = Masking.formatApaar(apaar);
        String studentName = (String) data.get("full_name");
        String instName = (String) data.get("inst_name");
        String progName = (String) data.get("prog_name");
        String acadYear = (String) data.get("academic_year");

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);

            if (demoMode) {
                writer.setPageEvent(new PdfPageEventHelper() {
                    @Override
                    public void onEndPage(PdfWriter w, Document d) {
                        PdfContentByte cb = w.getDirectContentUnder();
                        cb.saveState();
                        BaseFont bf;
                        try {
                            bf = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
                        } catch (Exception ex) {
                            cb.restoreState();
                            return;
                        }
                        cb.setFontAndSize(bf, 48);
                        cb.setColorFill(new Color(220, 220, 220, 100));
                        cb.beginText();
                        cb.showTextAligned(Element.ALIGN_CENTER, "SPECIMEN – DEMO", d.getPageSize().getWidth() / 2, d.getPageSize().getHeight() / 2, 45);
                        cb.endText();
                        cb.restoreState();
                    }
                });
            }

            doc.open();

            // Decorative outer border
            PdfContentByte canvas = writer.getDirectContent();
            canvas.setColorStroke(new Color(30, 58, 138)); // Deep Navy
            canvas.setLineWidth(3f);
            canvas.rectangle(20, 20, doc.getPageSize().getWidth() - 40, doc.getPageSize().getHeight() - 40);
            canvas.stroke();

            canvas.setColorStroke(new Color(217, 119, 6)); // Amber gold inner border
            canvas.setLineWidth(1f);
            canvas.rectangle(26, 26, doc.getPageSize().getWidth() - 52, doc.getPageSize().getHeight() - 52);
            canvas.stroke();

            // Header Title
            Font fontGov = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(71, 85, 105));
            Paragraph pGov = new Paragraph("ALL INDIA COUNCIL FOR TECHNICAL EDUCATION (AICTE)\nVIDYASETU DIGITAL ACADEMIC REPOSITORY", fontGov);
            pGov.setAlignment(Element.ALIGN_CENTER);
            pGov.setSpacingAfter(8);
            doc.add(pGov);

            Font fontCert = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, new Color(30, 58, 138));
            Paragraph pCert = new Paragraph("CERTIFICATE OF ENROLMENT & VERIFIED ACADEMIC IDENTITY", fontCert);
            pCert.setAlignment(Element.ALIGN_CENTER);
            pCert.setSpacingAfter(16);
            doc.add(pCert);

            // Institution name
            Font fontInst = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(15, 23, 42));
            Paragraph pInst = new Paragraph(instName, fontInst);
            pInst.setAlignment(Element.ALIGN_CENTER);
            pInst.setSpacingAfter(18);
            doc.add(pInst);

            // Body text
            Font fontBody = FontFactory.getFont(FontFactory.HELVETICA, 12, new Color(51, 65, 85));
            Font fontBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(15, 23, 42));

            Paragraph pBody = new Paragraph();
            pBody.setAlignment(Element.ALIGN_CENTER);
            pBody.setLeading(20);
            pBody.add(new Chunk("This is to certify that ", fontBody));
            pBody.add(new Chunk(studentName.toUpperCase(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(30, 58, 138))));
            pBody.add(new Chunk(" has been duly admitted and enrolled in the program of\n", fontBody));
            pBody.add(new Chunk(progName, fontBold));
            pBody.add(new Chunk(" for the academic session ", fontBody));
            pBody.add(new Chunk(acadYear, fontBold));
            pBody.add(new Chunk(".\nThe enrolment record has been validated against the national APAAR repository and AICTE approved intake.\n", fontBody));
            pBody.setSpacingAfter(24);
            doc.add(pBody);

            // Bottom table: Left = QR code, Right = Verified Credentials & Signatures
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(90);
            table.setWidths(new float[]{1.5f, 3.5f});

            // Generate ZXing QR
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(qrUrl, BarcodeFormat.QR_CODE, 130, 130);
            ByteArrayOutputStream qrBaos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", qrBaos);
            Image qrImage = Image.getInstance(qrBaos.toByteArray());
            qrImage.setAlignment(Element.ALIGN_CENTER);

            PdfPCell qrCell = new PdfPCell();
            qrCell.setBorder(Rectangle.NO_BORDER);
            qrCell.addElement(qrImage);
            Paragraph qrLabel = new Paragraph("Scan to verify digitally\nJTI: " + jti.substring(0, 12) + "...", FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY));
            qrLabel.setAlignment(Element.ALIGN_CENTER);
            qrCell.addElement(qrLabel);
            table.addCell(qrCell);

            // Right Cell: Student ID | APAAR ID and signatures
            PdfPCell infoCell = new PdfPCell();
            infoCell.setBorder(Rectangle.NO_BORDER);

            Font fontPair = FontFactory.getFont(FontFactory.COURIER_BOLD, 12, new Color(30, 58, 138));
            Paragraph pPair = new Paragraph("Student ID: " + studentId + "   |   APAAR ID: " + formattedApaar, fontPair);
            pPair.setSpacingAfter(14);
            infoCell.addElement(pPair);

            Paragraph pDates = new Paragraph("Date of Issue: " + LocalDate.now() + "     Status: VERIFIED & ACTIVE",
                    FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(71, 85, 105)));
            pDates.setSpacingAfter(18);
            infoCell.addElement(pDates);

            Paragraph pSig = new Paragraph("Digitally Signed by VidyaSetu Verification Authority (ES256)\nVerified against National APAAR Registry & AICTE Master Data",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.DARK_GRAY));
            infoCell.addElement(pSig);

            table.addCell(infoCell);
            doc.add(table);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed", e);
        }
    }
}
