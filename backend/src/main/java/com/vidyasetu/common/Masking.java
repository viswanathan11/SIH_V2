package com.vidyasetu.common;

import java.time.LocalDate;

/** PII masking helpers (role-based masking is decided by callers). */
public final class Masking {
    private Masking() {}

    public static String normalizeApaar(String raw) {
        if (raw == null) return null;
        return raw.replaceAll("[\\s-]", "").trim();
    }

    public static String formatApaar(String apaar) {
        if (apaar == null || apaar.length() != 12) return apaar;
        return apaar.substring(0, 4) + " " + apaar.substring(4, 8) + " " + apaar.substring(8);
    }

    public static String maskApaar(String apaar) {
        if (apaar == null) return null;
        String a = apaar.trim();
        if (a.length() < 4) return "XXXX";
        return "XXXX XXXX " + a.substring(a.length() - 4);
    }

    public static String maskName(String name) {
        if (name == null || name.isBlank()) return name;
        StringBuilder sb = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(part.charAt(0)).append("*".repeat(Math.max(1, part.length() - 1)));
        }
        return sb.toString();
    }

    public static String maskDob(LocalDate dob) {
        if (dob == null) return null;
        return "****-**-" + String.format("%02d", dob.getDayOfMonth());
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int at = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** CSV formula-injection neutralisation. */
    public static String csvSafe(String v) {
        if (v == null) return "";
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) return "'" + v;
        return v;
    }
}
