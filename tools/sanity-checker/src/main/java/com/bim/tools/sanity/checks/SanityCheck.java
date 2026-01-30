package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

/**
 * Interface for all sanity checks.
 */
public interface SanityCheck {
    /**
     * Unique identifier for this check.
     */
    String getId();

    /**
     * Human-readable name for this check.
     */
    String getName();

    /**
     * Execute the check and return result.
     */
    CheckResult execute(SanityModel model);
}
