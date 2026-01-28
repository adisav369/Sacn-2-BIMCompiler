package com.bim.compiler.validation.building;

import com.bim.compiler.dsl.BuildingCompiler.BuildingSpec;

/**
 * Phase 21A: Building-level validator interface.
 *
 * Unlike IValidator (element-level), BuildingValidator validates
 * the entire compiled building model for habitability, egress,
 * and code compliance.
 */
public interface BuildingValidator {

    /**
     * Get the validator name for reporting.
     */
    String getName();

    /**
     * Is this validator required for a valid building?
     * Required validators cause build failure on critical issues.
     * Advisory validators only log warnings.
     */
    boolean isRequired();

    /**
     * Validate the building model.
     * @param building the compiled building spec
     * @return validation result with issues found
     */
    BuildingValidationResult validate(BuildingSpec building);
}
