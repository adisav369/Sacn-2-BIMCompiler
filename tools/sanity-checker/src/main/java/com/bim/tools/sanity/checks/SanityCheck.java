package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

/**
 * Interface for all sanity checks.
 *
 * Phase 78: Added AD context support for metadata-driven thresholds.
 */
public interface SanityCheck {
    /**
     * Unique identifier for this check (must match ad_check_applicability.check_id).
     */
    String getId();

    /**
     * Human-readable name for this check.
     */
    String getName();

    /**
     * Execute the check and return result.
     * Default implementation delegates to execute(model, null).
     */
    default CheckResult execute(SanityModel model) {
        return execute(model, null);
    }

    /**
     * Execute the check with AD context for threshold lookups.
     *
     * @param model The building model to check
     * @param context AD context with building parameters and threshold access (may be null)
     * @return Check result
     */
    default CheckResult execute(SanityModel model, ADContext context) {
        // Default: ignore context, use hardcoded thresholds
        // Subclasses can override to use AD thresholds
        return executeWithDefaults(model);
    }

    /**
     * Execute with hardcoded defaults (backward compatibility).
     * Override this for checks that don't need AD context.
     */
    default CheckResult executeWithDefaults(SanityModel model) {
        throw new UnsupportedOperationException(
            "Check " + getId() + " must implement either execute(model) or execute(model, context)");
    }
}
