package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

/**
 * Check 10: Plumbing Elements (Phase 42)
 * Verifies the building has plumbing elements for wet rooms:
 * - Pipes (waste, supply, vent)
 * - Fixtures (toilets, sinks)
 */
public class PlumbingElementsCheck implements SanityCheck {

    @Override
    public String getId() { return "plumbing_elements"; }

    @Override
    public String getName() { return "Plumbing Elements"; }

    @Override
    public CheckResult execute(SanityModel model) {
        int pipeCount = 0;
        int fixtureCount = 0;
        int toiletCount = 0;
        int sinkCount = 0;

        for (Element elem : model.getAllElements()) {
            String ifcClass = elem.ifcClass();
            String name = elem.name();
            if (ifcClass == null) continue;

            if (ifcClass.equals("IfcPipeSegment") || ifcClass.equals("IfcFlowSegment")) {
                pipeCount++;
            } else if (ifcClass.equals("IfcSanitaryTerminal")) {
                fixtureCount++;
                // Check fixture type from name
                if (name != null) {
                    String lowerName = name.toLowerCase();
                    if (lowerName.contains("toilet") || lowerName.contains("wc")) {
                        toiletCount++;
                    } else if (lowerName.contains("sink") || lowerName.contains("basin")) {
                        sinkCount++;
                    }
                }
            }
        }

        int totalPlumbing = pipeCount + fixtureCount;

        // Check if building has wet rooms (inferred from space names)
        boolean hasWetRoom = false;
        for (Element space : model.getSpaces()) {
            if (space.name() != null) {
                String name = space.name().toLowerCase();
                if (name.contains("bath") || name.contains("toilet") ||
                    name.contains("kitchen") || name.contains("dapur") ||
                    name.contains("bilik_mandi") || name.contains("bilik_air")) {
                    hasWetRoom = true;
                    break;
                }
            }
        }

        // Also check from wall names for wet rooms
        if (!hasWetRoom) {
            for (Element wall : model.getWalls()) {
                if (wall.guid() != null) {
                    String guid = wall.guid().toLowerCase();
                    if (guid.contains("bath") || guid.contains("bilik_mandi")) {
                        hasWetRoom = true;
                        break;
                    }
                }
            }
        }

        if (totalPlumbing == 0) {
            if (hasWetRoom) {
                return CheckResult.warning(getId(), getName())
                    .summary("Wet room detected but no plumbing elements")
                    .detail("Building appears to have bathroom/kitchen but no pipes or fixtures")
                    .guidance("Add plumbing elements to wet rooms")
                    .data("pipes", 0)
                    .data("fixtures", 0)
                    .data("hasWetRoom", true)
                    .build();
            } else {
                return CheckResult.pass(getId(), getName())
                    .summary("No plumbing elements (no wet rooms detected)")
                    .detail("Building has no bathrooms or kitchens requiring plumbing")
                    .data("pipes", 0)
                    .data("fixtures", 0)
                    .data("hasWetRoom", false)
                    .build();
            }
        }

        // Check for minimum plumbing completeness
        if (fixtureCount == 0) {
            return CheckResult.warning(getId(), getName())
                .summary(String.format("%d pipes but no fixtures", pipeCount))
                .detail("Building has pipes but no sanitary fixtures")
                .guidance("Add toilets/sinks to bathrooms")
                .data("pipes", pipeCount)
                .data("fixtures", 0)
                .build();
        }

        if (pipeCount == 0) {
            return CheckResult.warning(getId(), getName())
                .summary(String.format("%d fixtures but no pipes", fixtureCount))
                .detail("Building has fixtures but no pipes to connect them")
                .guidance("Add waste/supply pipes")
                .data("pipes", 0)
                .data("fixtures", fixtureCount)
                .build();
        }

        return CheckResult.pass(getId(), getName())
            .summary(String.format("%d pipes, %d fixtures (%d toilets, %d sinks)",
                pipeCount, fixtureCount, toiletCount, sinkCount))
            .data("pipes", pipeCount)
            .data("fixtures", fixtureCount)
            .data("toilets", toiletCount)
            .data("sinks", sinkCount)
            .build();
    }
}
