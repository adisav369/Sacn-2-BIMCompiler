package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.util.List;

/**
 * Check 8: Storey Count (Phase 42)
 * Verifies expected number of storeys exist.
 * Uses authoritative IfcBuildingStorey records from spatial_structure table.
 */
public class StoreyCountCheck implements SanityCheck {

    @Override
    public String getId() { return "storey_count"; }

    @Override
    public String getName() { return "Storey Count"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        // Get actual storey count from spatial_structure (authoritative)
        int storeyCount = model.getStoreyCount();
        List<String> storeyNames = model.getStoreyNames();

        if (storeyCount == 0) {
            return CheckResult.fail(getId(), getName())
                .summary("No storeys found in spatial_structure")
                .detail("Building has no IfcBuildingStorey records")
                .guidance("Add IfcBuildingStorey to spatial_structure table")
                .data("storeyCount", 0)
                .build();
        }

        if (storeyCount == 1) {
            String storeyName = storeyNames.isEmpty() ? "unknown" : storeyNames.get(0);
            return CheckResult.pass(getId(), getName())
                .summary(String.format("Single-storey building (%s)", storeyName))
                .data("storeyCount", 1)
                .data("storeyNames", storeyNames)
                .build();
        }

        // Multi-storey building
        return CheckResult.pass(getId(), getName())
            .summary(String.format("%d-storey building (%s)", storeyCount,
                String.join(", ", storeyNames)))
            .data("storeyCount", storeyCount)
            .data("storeyNames", storeyNames)
            .build();
    }
}
