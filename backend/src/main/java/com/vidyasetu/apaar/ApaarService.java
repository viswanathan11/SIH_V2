package com.vidyasetu.apaar;

import com.vidyasetu.common.Masking;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class ApaarService {
    private static final Pattern DIGITS_12 = Pattern.compile("^\\d{12}$");

    private final ApaarRegistryClient registryClient;

    public ApaarService(ApaarRegistryClient registryClient) {
        this.registryClient = registryClient;
    }

    public String normalize(String raw) {
        return Masking.normalizeApaar(raw);
    }

    public boolean isValidLengthAndNumeric(String normalized) {
        return normalized != null && DIGITS_12.matcher(normalized).matches();
    }

    public boolean isSuspiciousPattern(String normalized) {
        if (!isValidLengthAndNumeric(normalized)) return false;
        // All same digit e.g. 111111111111, 000000000000
        boolean allSame = true;
        for (int i = 1; i < 12; i++) {
            if (normalized.charAt(i) != normalized.charAt(0)) {
                allSame = false;
                break;
            }
        }
        if (allSame) return true;

        // Strictly ascending e.g. 012345678901 or 123456789012
        if ("012345678901".equals(normalized) || "123456789012".equals(normalized) || "234567890123".equals(normalized)) {
            return true;
        }

        return false;
    }

    public ApaarRegistryClient.ApaarLookupResult lookup(String apaarId) {
        String norm = normalize(apaarId);
        if (!isValidLengthAndNumeric(norm)) {
            return ApaarRegistryClient.ApaarLookupResult.notFound();
        }
        return registryClient.lookup(norm);
    }
}
