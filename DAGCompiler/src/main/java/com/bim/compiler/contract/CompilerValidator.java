/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import com.bim.compiler.dsl.CompilationContext;

/**
 * Compilation-level validator — iDempiere ModelValidator analogue.
 *
 * <p>Validators execute at the pipeline boundary in sequence order.
 * Any thrown CompilerValidationException stops compilation for the affected building.
 * The exception message is self-documenting: rule_id, element_ref, expected,
 * actual, provenance — no secondary lookup required.
 *
 * <p>appliesTo() filters which building types run this validator:
 *   'ALL'            — every building
 *   'RESIDENTIAL'    — RESIDENTIAL building_type only
 *   'INSTITUTIONAL'  — INSTITUTIONAL building_type only
 *
 * <p>Registration: OSGi Declarative Service (current impl: Spring/Guice injection).
 * Sequence number determines execution order (lower = earlier, default = 10).
 *
 * <p>WATCHDOG: Never weaken a validator assertion to make a test pass.
 * Fix the data — the validator must stay strict.
 * If a rule fires a violation — that is a real bug, not a test to weaken.
 *
 * @see CompilerValidationException
 * @see AD_Events_Spatial_Rules.docx §4.3
 */
public interface CompilerValidator {

    /** Stable identifier used in log messages and test assertions. */
    String name();

    /** Execution order — lower fires first; default 10. */
    int sequence();

    /** 'ALL', 'RESIDENTIAL', or 'INSTITUTIONAL'. */
    String appliesTo();

    /**
     * Run validation against the current compilation context.
     * Throw CompilerValidationException if the constraint is violated.
     * Log at DEBUG: "[VALIDATOR] {name} on {building} — OK / FAIL"
     */
    void validate(CompilationContext ctx) throws CompilerValidationException;
}
