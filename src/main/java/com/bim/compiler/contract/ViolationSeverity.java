package com.bim.compiler.contract;

/**
 * Severity level for validation violations.
 *
 * <p>Determines how the compiler/witness system handles violations:
 * <ul>
 *   <li>ERROR: Compilation fails</li>
 *   <li>WARNING: Compilation succeeds but witness notes issue</li>
 *   <li>INFO: Informational only, no action required</li>
 * </ul>
 */
public enum ViolationSeverity {
    /**
     * Must fix - blocks compilation.
     * Used for structural integrity issues, code violations that
     * would result in non-compliant buildings.
     */
    ERROR,

    /**
     * Should fix - generates witness note.
     * Used for best practice violations, suboptimal configurations,
     * potential issues that don't block compliance.
     */
    WARNING,

    /**
     * Informational only.
     * Used for suggestions, optional improvements, or notes
     * for future consideration.
     */
    INFO
}
