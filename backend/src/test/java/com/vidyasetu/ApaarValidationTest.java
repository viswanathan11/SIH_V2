package com.vidyasetu;

import com.vidyasetu.apaar.ApaarRegistryClient;
import com.vidyasetu.apaar.ApaarService;
import com.vidyasetu.common.Masking;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class ApaarValidationTest {

    private final ApaarService apaarService = new ApaarService(apaarId -> {
        if ("910000000001".equals(apaarId)) {
            return ApaarRegistryClient.ApaarLookupResult.found("Asha Raman", LocalDate.of(2008, 3, 14), "F", "ACTIVE");
        }
        return ApaarRegistryClient.ApaarLookupResult.notFound();
    });

    @Test
    void testApaarFormattingAndNormalization() {
        assertEquals("910000000001", apaarService.normalize(" 9100-0000-0001 "));
        assertEquals("910000000001", apaarService.normalize("9100 0000 0001"));

        assertTrue(apaarService.isValidLengthAndNumeric("910000000001"));
        assertFalse(apaarService.isValidLengthAndNumeric("91000000001")); // 11 digits
        assertFalse(apaarService.isValidLengthAndNumeric("9100000000001")); // 13 digits
        assertFalse(apaarService.isValidLengthAndNumeric("91000000000A")); // Non-digit
    }

    @Test
    void testSuspiciousPatterns() {
        assertTrue(apaarService.isSuspiciousPattern("111111111111"));
        assertTrue(apaarService.isSuspiciousPattern("000000000000"));
        assertTrue(apaarService.isSuspiciousPattern("012345678901"));
        assertTrue(apaarService.isSuspiciousPattern("123456789012"));

        assertFalse(apaarService.isSuspiciousPattern("910000000001"));
        assertFalse(apaarService.isSuspiciousPattern("834920194857"));
    }

    @Test
    void testMaskingHelpers() {
        assertEquals("XXXX XXXX 0001", Masking.maskApaar("910000000001"));
        assertEquals("9100 0000 0001", Masking.formatApaar("910000000001"));
        assertEquals("A*** R****", Masking.maskName("Asha Raman"));
        assertEquals("'=SUM(A1:A10)", Masking.csvSafe("=SUM(A1:A10)"), "CSV formula injection must be escaped");
    }

    @Test
    void testLookupRegistry() {
        var resFound = apaarService.lookup("9100-0000-0001");
        assertEquals(ApaarRegistryClient.Status.FOUND, resFound.status());
        assertEquals("Asha Raman", resFound.fullName());

        var resNotFound = apaarService.lookup("999999999999");
        assertEquals(ApaarRegistryClient.Status.NOT_FOUND, resNotFound.status());
    }
}
