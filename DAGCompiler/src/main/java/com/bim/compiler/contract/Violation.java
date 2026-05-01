/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

/**
 * Represents a validation violation from contract checking.
 *
 * <p>Violations are the output of IValidatable.validate() and feed into
 * the witness system and compilation error reporting.
 *
 * @param severity How serious the violation is
 * @param code Standard/code reference (e.g., "IRC_R311.7", "MS_1184_5.2", "IFC_RULE_23")
 * @param message Human-readable description of the violation
 * @param elementGuid GUID of the element that violated the rule
 */
public record Violation(
    ViolationSeverity severity,
    String code,
    String message,
    String elementGuid
) {
    /**
     * Creates an ERROR severity violation.
     */
    public static Violation error(String code, String message, String elementGuid) {
        return new Violation(ViolationSeverity.ERROR, code, message, elementGuid);
    }

    /**
     * Creates a WARNING severity violation.
     */
    public static Violation warning(String code, String message, String elementGuid) {
        return new Violation(ViolationSeverity.WARNING, code, message, elementGuid);
    }

    /**
     * Creates an INFO severity violation.
     */
    public static Violation info(String code, String message, String elementGuid) {
        return new Violation(ViolationSeverity.INFO, code, message, elementGuid);
    }

    /**
     * Check if this violation blocks compilation.
     */
    public boolean isBlocking() {
        return severity == ViolationSeverity.ERROR;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s (element: %s)",
            severity, code, message, elementGuid);
    }
}
