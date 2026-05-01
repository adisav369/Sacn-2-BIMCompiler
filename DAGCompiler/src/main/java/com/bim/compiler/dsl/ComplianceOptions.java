/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.dsl;

/**
 * Compile-time options for code compliance enforcement.
 *
 * Controls "build as required" behavior:
 * - AUTO_FP: Automatically add fire protection when AD triggers fire
 * - AUTO_ACCESSIBLE: Automatically add accessibility features
 * - AUTO_EGRESS: Automatically verify egress paths
 *
 * Usage in DSL:
 * <pre>
 * BUILDING "school" compliance:AUTO_FP {
 *   ...
 * }
 * </pre>
 *
 * Usage in code:
 * <pre>
 * ComplianceOptions options = ComplianceOptions.builder()
 *     .autoFireProtection(true)
 *     .jurisdiction("MALAYSIA")
 *     .build();
 *
 * BuildingCompiler compiler = new BuildingCompiler(options);
 * </pre>
 */
public record ComplianceOptions(
    boolean autoFireProtection,     // Auto-add sprinklers when triggered
    boolean autoAccessibility,      // Auto-add accessible features
    boolean autoEgress,             // Auto-verify egress paths
    boolean strictValidation,       // Fail build on violations
    String jurisdiction,            // MALAYSIA, USA, INTERNATIONAL
    String occupancyGroup           // R, A, B, E, F, H, I, M, S
) {
    // =========================================================================
    // Presets
    // =========================================================================

    /** Default: no auto-fulfillment, validation only */
    public static final ComplianceOptions DEFAULT = new ComplianceOptions(
        false, false, false, false, "MALAYSIA", "R"
    );

    /** Full compliance: auto-fulfill all requirements */
    public static final ComplianceOptions FULL_COMPLIANCE = new ComplianceOptions(
        true, true, true, true, "MALAYSIA", "R"
    );

    /** Fire protection only */
    public static final ComplianceOptions AUTO_FP = new ComplianceOptions(
        true, false, false, false, "MALAYSIA", "R"
    );

    // =========================================================================
    // Builder
    // =========================================================================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean autoFireProtection = false;
        private boolean autoAccessibility = false;
        private boolean autoEgress = false;
        private boolean strictValidation = false;
        private String jurisdiction = "MALAYSIA";
        private String occupancyGroup = "R";

        public Builder autoFireProtection(boolean value) {
            this.autoFireProtection = value;
            return this;
        }

        public Builder autoAccessibility(boolean value) {
            this.autoAccessibility = value;
            return this;
        }

        public Builder autoEgress(boolean value) {
            this.autoEgress = value;
            return this;
        }

        public Builder strictValidation(boolean value) {
            this.strictValidation = value;
            return this;
        }

        public Builder jurisdiction(String value) {
            this.jurisdiction = value;
            return this;
        }

        public Builder occupancyGroup(String value) {
            this.occupancyGroup = value;
            return this;
        }

        public ComplianceOptions build() {
            return new ComplianceOptions(
                autoFireProtection, autoAccessibility, autoEgress,
                strictValidation, jurisdiction, occupancyGroup
            );
        }
    }

    // =========================================================================
    // DSL Parsing Helper
    // =========================================================================

    /**
     * Parse compliance options from DSL string.
     *
     * Examples:
     * - "AUTO_FP" → autoFireProtection=true
     * - "AUTO_FP,STRICT" → autoFireProtection=true, strictValidation=true
     * - "FULL" → all options enabled
     */
    public static ComplianceOptions fromDSL(String dslValue) {
        if (dslValue == null || dslValue.isEmpty()) {
            return DEFAULT;
        }

        String upper = dslValue.toUpperCase();

        if ("FULL".equals(upper) || "FULL_COMPLIANCE".equals(upper)) {
            return FULL_COMPLIANCE;
        }

        Builder builder = builder();

        for (String token : upper.split(",")) {
            token = token.trim();
            switch (token) {
                case "AUTO_FP", "FIRE_PROTECTION" -> builder.autoFireProtection(true);
                case "AUTO_ACCESSIBLE", "ACCESSIBILITY" -> builder.autoAccessibility(true);
                case "AUTO_EGRESS", "EGRESS" -> builder.autoEgress(true);
                case "STRICT" -> builder.strictValidation(true);
            }
        }

        return builder.build();
    }

    // =========================================================================
    // Convenience Methods
    // =========================================================================

    public ComplianceOptions withJurisdiction(String jurisdiction) {
        return new ComplianceOptions(
            autoFireProtection, autoAccessibility, autoEgress,
            strictValidation, jurisdiction, occupancyGroup
        );
    }

    public ComplianceOptions withOccupancy(String occupancy) {
        return new ComplianceOptions(
            autoFireProtection, autoAccessibility, autoEgress,
            strictValidation, jurisdiction, occupancy
        );
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ComplianceOptions[");
        if (autoFireProtection) sb.append("AUTO_FP,");
        if (autoAccessibility) sb.append("AUTO_ACCESSIBLE,");
        if (autoEgress) sb.append("AUTO_EGRESS,");
        if (strictValidation) sb.append("STRICT,");
        sb.append("jurisdiction=").append(jurisdiction);
        sb.append(",occupancy=").append(occupancyGroup);
        sb.append("]");
        return sb.toString();
    }
}
