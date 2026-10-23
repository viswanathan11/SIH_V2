package com.vidyasetu.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CryptoService {
    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final byte[] fieldKey;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(@Value("${app.field-encryption-key:}") String configuredKey) {
        if (configuredKey != null && !configuredKey.isBlank()) {
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(configuredKey.trim());
            } catch (Exception e) {
                decoded = sha256Bytes(configuredKey.getBytes(StandardCharsets.UTF_8));
            }
            if (decoded.length == 32) {
                this.fieldKey = decoded;
            } else {
                this.fieldKey = sha256Bytes(decoded);
            }
        } else {
            log.warn("app.field-encryption-key not set; generating random 256-bit dev key in memory");
            this.fieldKey = new byte[32];
            random.nextBytes(this.fieldKey);
        }
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(fieldKey, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String cipherBase64) {
        if (cipherBase64 == null || cipherBase64.isBlank()) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(cipherBase64.trim());
            if (combined.length < GCM_IV_LENGTH) return null;
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(fieldKey, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Decryption failed for ciphertext snippet");
            return null;
        }
    }

    public String hmacSha256Hex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(out);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    public String sha256Hex(String input) {
        return bytesToHex(sha256Bytes(input.getBytes(StandardCharsets.UTF_8)));
    }

    public static byte[] sha256Bytes(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
