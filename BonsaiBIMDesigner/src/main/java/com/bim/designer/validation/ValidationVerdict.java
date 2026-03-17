package com.bim.designer.validation;

/**
 * Result of a placement validation check.
 *
 * <p>Three outcomes:
 * <ul>
 *   <li>{@code PASS} — placement is code-compliant, proceed</li>
 *   <li>{@code BLOCK} — placement violates a rule, reject</li>
 *   <li>{@code ADJUST} — placement can be corrected to nearest compliant value</li>
 * </ul>
 *
 * <p>Follows iDempiere ModelValidator convention: null = PASS,
 * non-null error string = BLOCK. We extend with ADJUST for GUI use
 * (slider snaps to nearest legal value).
 */
public record ValidationVerdict(
        /** PASS, BLOCK, or ADJUST */
        Result result,
        /** Rule that triggered (null if PASS) */
        String ruleName,
        /** Standard reference: "UBBL 2012 s33(1)" */
        String standardRef,
        /** What was measured */
        double actualValue,
        /** What the rule requires */
        double requiredValue,
        /** Human-readable message */
        String message,
        /** For ADJUST: the corrected value to use */
        double adjustedValue
) {
    public enum Result { PASS, BLOCK, ADJUST }

    /** Convenience: placement is compliant. */
    public static ValidationVerdict pass() {
        return new ValidationVerdict(Result.PASS, null, null, 0, 0, null, 0);
    }

    /** Convenience: placement violates a rule. */
    public static ValidationVerdict block(String ruleName, String standardRef,
                                          double actual, double required, String message) {
        return new ValidationVerdict(Result.BLOCK, ruleName, standardRef,
                actual, required, message, 0);
    }

    /** Convenience: placement can be adjusted to comply. */
    public static ValidationVerdict adjust(String ruleName, String standardRef,
                                           double actual, double required,
                                           double adjusted, String message) {
        return new ValidationVerdict(Result.ADJUST, ruleName, standardRef,
                actual, required, message, adjusted);
    }

    /** @return true if placement should be rejected */
    public boolean isBlocked() {
        return result == Result.BLOCK;
    }

    /** @return true if placement needs adjustment */
    public boolean needsAdjustment() {
        return result == Result.ADJUST;
    }
}
