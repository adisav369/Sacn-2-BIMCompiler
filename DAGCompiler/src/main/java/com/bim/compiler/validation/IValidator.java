package com.bim.compiler.validation;

import com.bim.compiler.model.ISpatialElement;

import java.util.List;

/**
 * Common interface for all validators.
 * Validators check elements against construction rules from SESSION_STATE.md.
 */
public interface IValidator {

    /**
     * Get the validator name for reporting.
     */
    String getName();

    /**
     * Check if this validator applies to the given element.
     * @param element the element to check
     * @return true if this validator should run on this element
     */
    boolean appliesTo(ISpatialElement element);

    /**
     * Validate a single element.
     * @param element the element to validate
     * @param allElements all elements in the model (for spatial queries)
     * @return validation result (never null, may be empty if valid)
     */
    ValidationResult validate(ISpatialElement element, List<ISpatialElement> allElements);

    /**
     * Validate all applicable elements.
     * @param elements all elements in the model
     * @return combined validation result
     */
    default ValidationResult validateAll(List<ISpatialElement> elements) {
        ValidationResult result = new ValidationResult();
        for (ISpatialElement element : elements) {
            if (appliesTo(element)) {
                result.merge(validate(element, elements));
            }
        }
        return result;
    }
}
