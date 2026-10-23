package com.vidyasetu;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class CertificatePdfTest {

    @Test
    void testQrCodeGenerationAndDecoding() throws Exception {
        String originalUrl = "http://localhost:5173/v/test-jti-uuid-123456";

        QRCodeWriter qrWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrWriter.encode(originalUrl, BarcodeFormat.QR_CODE, 200, 200);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);

        BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(baos.toByteArray()));
        assertNotNull(img);

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(img)));
        var result = new MultiFormatReader().decode(bitmap);

        assertNotNull(result);
        assertEquals(originalUrl, result.getText());
    }
}
