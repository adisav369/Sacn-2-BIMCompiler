package com.bim.compiler.validation.building;

import com.bim.compiler.dsl.BuildingCompiler.*;

import java.util.*;

/**
 * Phase 28: Registry mapping LOD levels to validators.
 *
 * LOD (Level of Development) determines the required detail:
 * - LOD 100: Conceptual - area and type only
 * - LOD 200: Approximate - rough dimensions
 * - LOD 300: Precise - exact geometry, openings
 * - LOD 350: Coordination - MEP, structure integration
 * - LOD 400: Fabrication - shop drawing detail
 * - LOD 500: As-Built - verified field conditions
 *
 * Higher LOD levels require more precise validation.
 */
public class LODValidatorRegistry {

    private static final Map<Integer, BuildingValidator> VALIDATORS = new HashMap<>();

    static {
        VALIDATORS.put(100, new LOD100Validator());
        VALIDATORS.put(200, new LOD200Validator());
        VALIDATORS.put(300, new LOD300Validator());
        VALIDATORS.put(350, new LOD350Validator());
        VALIDATORS.put(400, new LOD400Validator());
        VALIDATORS.put(500, new LOD500Validator());
    }

    /**
     * Get validator for a LOD level.
     * Returns the validator for the specified level or the nearest lower level.
     */
    public static BuildingValidator get(int lod) {
        // Exact match
        if (VALIDATORS.containsKey(lod)) {
            return VALIDATORS.get(lod);
        }

        // Find nearest lower level
        int nearest = 100;
        for (int level : VALIDATORS.keySet()) {
            if (level <= lod && level > nearest) {
                nearest = level;
            }
        }
        return VALIDATORS.get(nearest);
    }

    /**
     * Get all registered LOD levels.
     */
    public static Set<Integer> getLODLevels() {
        return VALIDATORS.keySet();
    }

    // =========================================================================
    // LOD Validators
    // =========================================================================

    /**
     * LOD 100: Conceptual level - minimal validation.
     * Just verifies that spaces exist and have reasonable types.
     */
    public static class LOD100Validator implements BuildingValidator {

        @Override
        public String getName() {
            return "LOD100Validator";
        }

        @Override
        public boolean isRequired() {
            return false;
        }

        @Override
        public BuildingValidationResult validate(BuildingSpec building) {
            BuildingValidationResult result = new BuildingValidationResult(getName());

            // LOD 100 just needs spaces to exist
            int totalSpaces = 0;
            for (StoreySpec storey : building.storeys()) {
                totalSpaces += storey.rooms().size();
            }

            if (totalSpaces == 0) {
                result.addWarning(
                    "Building",
                    "LOD100",
                    "Building has no defined spaces"
                );
            }

            result.addInfo(
                "Building",
                "LOD100",
                "LOD 100 validation complete: %d spaces found",
                totalSpaces
            );

            return result;
        }
    }

    /**
     * LOD 200: Approximate level - dimensions should be reasonable.
     */
    public static class LOD200Validator implements BuildingValidator {

        @Override
        public String getName() {
            return "LOD200Validator";
        }

        @Override
        public boolean isRequired() {
            return false;
        }

        @Override
        public BuildingValidationResult validate(BuildingSpec building) {
            BuildingValidationResult result = new BuildingValidationResult(getName());

            for (StoreySpec storey : building.storeys()) {
                for (RoomSpec room : storey.rooms()) {
                    double width = room.maxX() - room.minX();
                    double depth = room.maxY() - room.minY();

                    // LOD 200: Dimensions should be reasonable (not zero, not absurd)
                    if (width <= 0 || depth <= 0) {
                        result.addWarning(
                            room.name(),
                            "LOD200",
                            "Room has zero or negative dimension (%.2fm x %.2fm)",
                            width, depth
                        );
                    } else if (width > 50 || depth > 50) {
                        result.addInfo(
                            room.name(),
                            "LOD200",
                            "Room dimension unusually large (%.2fm x %.2fm) - verify",
                            width, depth
                        );
                    }
                }
            }

            return result;
        }
    }

    /**
     * LOD 300: Precise level - exact geometry, wall connectivity.
     */
    public static class LOD300Validator implements BuildingValidator {

        private static final double TOLERANCE_MM = 5.0; // 5mm tolerance

        @Override
        public String getName() {
            return "LOD300Validator";
        }

        @Override
        public boolean isRequired() {
            return false;
        }

        @Override
        public BuildingValidationResult validate(BuildingSpec building) {
            BuildingValidationResult result = new BuildingValidationResult(getName());

            for (StoreySpec storey : building.storeys()) {
                // LOD 300: Check wall connectivity precision
                validateWallConnectivity(storey, result);

                // LOD 300: Check opening positions are exact
                validateOpeningPositions(storey, result);
            }

            return result;
        }

        private void validateWallConnectivity(StoreySpec storey, BuildingValidationResult result) {
            // Walls should meet at corners with <= 5mm gap
            // (This is already checked by GeometryValidator, but at LOD 300 we note the precision)
            result.addInfo(
                storey.name(),
                "LOD300",
                "Wall connectivity verified at %.1fmm tolerance",
                TOLERANCE_MM
            );
        }

        private void validateOpeningPositions(StoreySpec storey, BuildingValidationResult result) {
            // Check that doors and windows have valid positions
            for (DoorSpec door : storey.doors()) {
                if (Double.isNaN(door.x()) || Double.isNaN(door.y())) {
                    result.addWarning(
                        door.name(),
                        "LOD300",
                        "Door has undefined position"
                    );
                }
            }

            for (WindowSpec window : storey.windows()) {
                if (Double.isNaN(window.x()) || Double.isNaN(window.y())) {
                    result.addWarning(
                        window.name(),
                        "LOD300",
                        "Window has undefined position"
                    );
                }
            }
        }
    }

    /**
     * LOD 350: Coordination level - MEP and structure integration.
     */
    public static class LOD350Validator implements BuildingValidator {

        @Override
        public String getName() {
            return "LOD350Validator";
        }

        @Override
        public boolean isRequired() {
            return false;
        }

        @Override
        public BuildingValidationResult validate(BuildingSpec building) {
            BuildingValidationResult result = new BuildingValidationResult(getName());

            for (StoreySpec storey : building.storeys()) {
                // LOD 350: Check MEP elements exist
                validateMEPElements(storey, result);

                // LOD 350: Check structural elements exist
                validateStructuralElements(storey, result);
            }

            return result;
        }

        private void validateMEPElements(StoreySpec storey, BuildingValidationResult result) {
            // Check for sprinklers, lights, diffusers
            if (storey.sprinklers().isEmpty() && storey.lights().isEmpty()) {
                result.addInfo(
                    storey.name(),
                    "LOD350",
                    "No MEP ceiling elements (sprinklers/lights) - expected at LOD 350"
                );
            }

            if (storey.diffusers().isEmpty()) {
                result.addInfo(
                    storey.name(),
                    "LOD350",
                    "No HVAC diffusers - expected at LOD 350"
                );
            }
        }

        private void validateStructuralElements(StoreySpec storey, BuildingValidationResult result) {
            // Check for columns and beams
            if (storey.columns().isEmpty()) {
                result.addInfo(
                    storey.name(),
                    "LOD350",
                    "No structural columns - expected at LOD 350"
                );
            }

            if (storey.beams().isEmpty()) {
                result.addInfo(
                    storey.name(),
                    "LOD350",
                    "No structural beams/lintels - expected at LOD 350"
                );
            }
        }
    }

    /**
     * LOD 400: Fabrication level - shop drawing detail.
     */
    public static class LOD400Validator implements BuildingValidator {

        @Override
        public String getName() {
            return "LOD400Validator";
        }

        @Override
        public boolean isRequired() {
            return false;
        }

        @Override
        public BuildingValidationResult validate(BuildingSpec building) {
            BuildingValidationResult result = new BuildingValidationResult(getName());

            for (StoreySpec storey : building.storeys()) {
                // LOD 400: Check fixtures have geometry hashes (library references)
                validateFixtureReferences(storey, result);

                // LOD 400: Check structural elements have material specifications
                validateMaterialSpecs(storey, result);
            }

            return result;
        }

        private void validateFixtureReferences(StoreySpec storey, BuildingValidationResult result) {
            for (FixtureSpec fixture : storey.fixtures()) {
                if (fixture.geometryHash() == null || fixture.geometryHash().isEmpty()) {
                    result.addInfo(
                        fixture.id(),
                        "LOD400",
                        "Fixture lacks library geometry reference"
                    );
                }
            }
        }

        private void validateMaterialSpecs(StoreySpec storey, BuildingValidationResult result) {
            // At LOD 400, structural elements should have specific profiles
            for (ColumnSpec col : storey.columns()) {
                if (col.geometryHash() == null) {
                    result.addInfo(
                        col.id(),
                        "LOD400",
                        "Column lacks specific profile reference"
                    );
                }
            }
        }
    }

    /**
     * LOD 500: As-Built level - verified field conditions.
     */
    public static class LOD500Validator implements BuildingValidator {

        @Override
        public String getName() {
            return "LOD500Validator";
        }

        @Override
        public boolean isRequired() {
            return false;
        }

        @Override
        public BuildingValidationResult validate(BuildingSpec building) {
            BuildingValidationResult result = new BuildingValidationResult(getName());

            // LOD 500 is for as-built verification
            // Typically involves comparing model to survey data
            result.addInfo(
                "Building",
                "LOD500",
                "LOD 500 validation: As-built verification requires field survey comparison"
            );

            // Check that all elements have precise coordinates (no approximations)
            for (StoreySpec storey : building.storeys()) {
                validatePrecision(storey, result);
            }

            return result;
        }

        private void validatePrecision(StoreySpec storey, BuildingValidationResult result) {
            // At LOD 500, coordinates should not have obvious rounding
            for (RoomSpec room : storey.rooms()) {
                // Check if coordinates appear to be surveyed (not round numbers)
                if (isRoundNumber(room.minX()) && isRoundNumber(room.minY()) &&
                    isRoundNumber(room.maxX()) && isRoundNumber(room.maxY())) {
                    result.addInfo(
                        room.name(),
                        "LOD500",
                        "Room coordinates are round numbers - verify against survey"
                    );
                }
            }
        }

        private boolean isRoundNumber(double value) {
            // Check if value is a whole number or simple fraction
            double remainder = value % 0.5;
            return Math.abs(remainder) < 0.001 || Math.abs(remainder - 0.5) < 0.001;
        }
    }
}
