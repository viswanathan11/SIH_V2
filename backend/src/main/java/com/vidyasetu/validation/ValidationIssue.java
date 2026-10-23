package com.vidyasetu.validation;

import java.util.Map;
import java.util.UUID;

public record ValidationIssue(
        UUID id,
        UUID batchId,
        UUID stagingRowId,
        UUID enrolmentId,
        UUID institutionId,
        String institutionCode,
        String ruleCode,
        String severity, // INFO, WARN, ERROR, CRITICAL
        String field,
        String message,
        String hint,
        Map<String, Object> context,
        String apaarMasked,
        Integer rowNo,
        String status, // OPEN, ACKNOWLEDGED, RESOLVED, WAIVED
        String resolvedBy,
        String resolutionNote
) {
    public static ValidationIssue of(String ruleCode, String severity, String field, String message, String hint,
                                     Map<String, Object> context, Integer rowNo, String apaarMasked) {
        return new ValidationIssue(
                UUID.randomUUID(), null, null, null, null, null,
                ruleCode, severity, field, message, hint, context, apaarMasked, rowNo, "OPEN", null, null
        );
    }
}
