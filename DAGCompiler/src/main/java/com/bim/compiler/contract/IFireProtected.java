/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import java.util.List;

/**
 * Contract: Fire Protection Requirements.
 *
 * Entities implementing this interface MUST have fire protection elements
 * when building parameters trigger code requirements.
 *
 * ENFORCEMENT FLOW:
 * 1. AD trigger fires (building height/area/occupancy)
 * 2. Contract requires sprinklers() to return non-empty list
 * 3. Validator rejects builds that violate contract
 * 4. Witness proves placement correctness (MATHS)
 *
 * Code references:
 * - UBBL By-Law 225: Buildings >18m require sprinklers
 * - UBBL By-Law 225: Floor area >1000m² requires sprinklers
 * - NFPA 13: Sprinkler spacing rules
 *
 * @see com.bim.compiler.dsl.FireProtectionAD
 */
public interface IFireProtected {

    /**
     * Check if fire protection is required for this entity.
     *
     * MATHS PROOF:
     * - height >= 18.0m OR
     * - floorArea >= 1000.0m² OR
     * - occupancy triggers (assembly, storage, etc.)
     *
     * @return true if FP is mandatory per AD triggers
     */
    boolean isFireProtectionRequired();

    /**
     * Get the fire protection elements for this entity.
     *
     * CONTRACT: If isFireProtectionRequired() returns true,
     * this method MUST return a non-empty list.
     *
     * @return list of fire protection element specifications
     */
    List<FireProtectionElement> getFireProtectionElements();

    /**
     * Get the hazard classification for coverage calculation.
     *
     * @return LIGHT, ORDINARY_1, ORDINARY_2, or EXTRA
     */
    default String getHazardClass() {
        return "LIGHT";  // Default for residential/office
    }

    /**
     * Validate fire protection coverage.
     *
     * MATHS PROOF:
     * - head_count >= ceil(floor_area / coverage_per_head)
     * - min_spacing <= actual_spacing <= max_spacing
     *
     * @return validation result with any violations
     */
    FireProtectionValidation validateCoverage();

    // =========================================================================
    // Supporting Records
    // =========================================================================

    /**
     * Fire protection element specification.
     */
    record FireProtectionElement(
        String id,
        ElementType type,
        double x, double y, double z,
        String roomId,
        String codeRef
    ) {
        public enum ElementType {
            SPRINKLER_HEAD,
            SMOKE_DETECTOR,
            HEAT_DETECTOR,
            FIRE_ALARM_PANEL,
            RISER_CONNECTION,
            HOSE_REEL
        }
    }

    /**
     * Validation result for fire protection coverage.
     */
    record FireProtectionValidation(
        boolean valid,
        double floorArea,
        int requiredHeads,
        int actualHeads,
        double minSpacing,
        double maxSpacing,
        double actualMinSpacing,
        double actualMaxSpacing,
        List<String> violations
    ) {
        public static FireProtectionValidation notRequired() {
            return new FireProtectionValidation(true, 0, 0, 0, 0, 0, 0, 0, List.of());
        }

        public static FireProtectionValidation valid(double area, int required, int actual,
                                                      double minS, double maxS, double actMin, double actMax) {
            return new FireProtectionValidation(true, area, required, actual, minS, maxS, actMin, actMax, List.of());
        }

        public static FireProtectionValidation invalid(double area, int required, int actual,
                                                        List<String> violations) {
            return new FireProtectionValidation(false, area, required, actual, 0, 0, 0, 0, violations);
        }
    }
}
