/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

/**
 * Thrown by CompilerValidator when a compilation-level constraint is violated.
 *
 * <p>Every failure is self-documenting: rule_id cites the constraint, provenance
 * cites the research source. No secondary lookup needed to understand a failure.
 *
 * <p>iDempiere analogue: ModelValidator rejection with rollback.
 *
 * @see CompilerValidator
 * @see AD_Events_Spatial_Rules.docx §4.3
 */
public class CompilerValidationException extends Exception {

    private final String ruleId;
    private final String elementRef;
    private final String expected;
    private final String actual;
    private final String provenance;

    public CompilerValidationException(
            String ruleId,
            String elementRef,
            String expected,
            String actual,
            String provenance) {
        super(String.format("[%s] %s — expected: %s, actual: %s [%s]",
            ruleId, elementRef, expected, actual, provenance));
        this.ruleId     = ruleId;
        this.elementRef = elementRef;
        this.expected   = expected;
        this.actual     = actual;
        this.provenance = provenance;
    }

    public String ruleId()      { return ruleId; }
    public String elementRef()  { return elementRef; }
    public String expected()    { return expected; }
    public String actual()      { return actual; }
    public String provenance()  { return provenance; }
}
