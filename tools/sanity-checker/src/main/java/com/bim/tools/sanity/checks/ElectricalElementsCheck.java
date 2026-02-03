package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

/**
 * Check 9: Electrical Elements (Phase 42)
 * Verifies the building has necessary electrical elements:
 * - Light fixtures
 * - Power outlets
 * - Switches
 */
public class ElectricalElementsCheck implements SanityCheck {

    @Override
    public String getId() { return "electrical_elements"; }

    @Override
    public String getName() { return "Electrical Elements"; }

    @Override
    public CheckResult execute(SanityModel model) {
        int lightCount = 0;
        int outletCount = 0;
        int switchCount = 0;

        for (Element elem : model.getAllElements()) {
            String ifcClass = elem.ifcClass();
            if (ifcClass == null) continue;

            if (ifcClass.equals("IfcLightFixture")) {
                lightCount++;
            } else if (ifcClass.equals("IfcOutlet")) {
                outletCount++;
            } else if (ifcClass.equals("IfcSwitchingDevice")) {
                switchCount++;
            }
        }

        int totalElectrical = lightCount + outletCount + switchCount;

        if (totalElectrical == 0) {
            return CheckResult.fail(getId(), getName())
                .summary("No electrical elements found")
                .detail("Building has no lights, outlets, or switches")
                .guidance("Add electrical elements to rooms")
                .data("lights", 0)
                .data("outlets", 0)
                .data("switches", 0)
                .build();
        }

        // Check for minimum reasonable counts
        boolean hasLights = lightCount > 0;
        boolean hasOutlets = outletCount > 0;
        boolean hasSwitches = switchCount > 0;

        if (!hasLights) {
            return CheckResult.warning(getId(), getName())
                .summary(String.format("No lights found (%d outlets, %d switches)", outletCount, switchCount))
                .detail("Building has no light fixtures")
                .guidance("Consider adding lights to rooms")
                .data("lights", lightCount)
                .data("outlets", outletCount)
                .data("switches", switchCount)
                .build();
        }

        if (!hasOutlets && !hasSwitches) {
            return CheckResult.warning(getId(), getName())
                .summary(String.format("%d lights but no outlets or switches", lightCount))
                .detail("Building has lights but no power points")
                .guidance("Consider adding outlets and switches")
                .data("lights", lightCount)
                .data("outlets", outletCount)
                .data("switches", switchCount)
                .build();
        }

        return CheckResult.pass(getId(), getName())
            .summary(String.format("%d lights, %d outlets, %d switches", lightCount, outletCount, switchCount))
            .data("lights", lightCount)
            .data("outlets", outletCount)
            .data("switches", switchCount)
            .data("total", totalElectrical)
            .build();
    }
}
