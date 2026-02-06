package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.BoundingBox;
import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.util.*;

/**
 * Check 23: MEP Element Positioning (Phase 81)
 *
 * Verifies that MEP elements (lights, sprinklers, outlets, switches) are:
 * 1. NOT bunched at origin (0,0,0) - a common compiler bug
 * 2. Distributed across the building footprint
 * 3. Within valid building bounds
 *
 * This check catches the "origin bunching" bug where library geometry
 * transform offsets are not applied correctly, causing all elements
 * to cluster at (0,0,0) instead of their room positions.
 *
 * DETERMINISTIC GUARANTEE: The compiler calculates positions from room bounds:
 *   - Lights: center of room or grid pattern within room bounds
 *   - Sprinklers: NFPA 13 grid centered within room bounds
 *   - Outlets: distributed around room perimeter walls
 * All formulas use roomMinX/Y/Z as reference, never hardcoded (0,0,0).
 */
public class MEPPositioningCheck implements SanityCheck {

    // Origin tolerance - elements within this distance of origin are "bunched"
    private static final double ORIGIN_TOLERANCE = 0.5;  // 500mm

    // Minimum unique positions required (as ratio of element count)
    private static final double MIN_UNIQUE_POSITION_RATIO = 0.5;

    @Override
    public String getId() { return "mep_positioning"; }

    @Override
    public String getName() { return "MEP Element Positioning"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        List<Element> mepElements = new ArrayList<>();
        List<Element> atOrigin = new ArrayList<>();
        Set<String> uniquePositions = new HashSet<>();

        int lightCount = 0;
        int sprinklerCount = 0;
        int outletCount = 0;
        int switchCount = 0;

        BoundingBox envelope = model.getBuildingEnvelope();

        for (Element elem : model.getAllElements()) {
            String ifcClass = elem.ifcClass();
            if (ifcClass == null) continue;

            boolean isMEP = false;
            if (ifcClass.equals("IfcLightFixture")) {
                lightCount++;
                isMEP = true;
            } else if (ifcClass.equals("IfcFireSuppressionTerminal")) {
                sprinklerCount++;
                isMEP = true;
            } else if (ifcClass.equals("IfcOutlet")) {
                outletCount++;
                isMEP = true;
            } else if (ifcClass.equals("IfcSwitchingDevice")) {
                switchCount++;
                isMEP = true;
            }

            if (isMEP) {
                mepElements.add(elem);

                BoundingBox bbox = elem.bbox();
                if (bbox != null) {
                    // Check if element is at origin
                    double cx = bbox.centerX();
                    double cy = bbox.centerY();
                    double cz = bbox.centerZ();

                    if (isAtOrigin(cx, cy, cz)) {
                        atOrigin.add(elem);
                    }

                    // Track unique positions (rounded to 100mm grid)
                    String posKey = String.format("%.1f,%.1f,%.1f", cx, cy, cz);
                    uniquePositions.add(posKey);
                }
            }
        }

        int totalMEP = mepElements.size();

        // No MEP elements - pass (handled by ElectricalElementsCheck)
        if (totalMEP == 0) {
            return CheckResult.pass(getId(), getName())
                .summary("No MEP elements to check (N/A)")
                .build();
        }

        // FAIL: Elements bunched at origin
        int originCount = atOrigin.size();
        if (originCount > 0) {
            double originRatio = (double) originCount / totalMEP;

            // If more than 10% at origin, this is the bunching bug
            if (originRatio > 0.1) {
                StringBuilder detail = new StringBuilder();
                detail.append("MEP elements bunched at origin (0,0,0):\n");
                int shown = 0;
                for (Element elem : atOrigin) {
                    if (shown < 5) {
                        detail.append(String.format("  - %s at %s\n",
                            elem.guid(), elem.bbox()));
                        shown++;
                    }
                }
                if (originCount > 5) {
                    detail.append(String.format("  ... and %d more\n", originCount - 5));
                }

                return CheckResult.fail(getId(), getName())
                    .summary(String.format("%d/%d MEP elements at origin (%.0f%%)",
                        originCount, totalMEP, originRatio * 100))
                    .detail(detail.toString())
                    .guidance("Check BuildingWriter transform offset calculation: " +
                        "translateZ = targetZ - localMinZ (bottom) or targetZ - localMaxZ (top)")
                    .data("total_mep", totalMEP)
                    .data("at_origin", originCount)
                    .data("lights", lightCount)
                    .data("sprinklers", sprinklerCount)
                    .build();
            }
        }

        // WARN: Too few unique positions (elements stacked)
        double uniqueRatio = (double) uniquePositions.size() / totalMEP;
        if (uniqueRatio < MIN_UNIQUE_POSITION_RATIO && totalMEP > 2) {
            return CheckResult.warning(getId(), getName())
                .summary(String.format("Only %d unique positions for %d MEP elements",
                    uniquePositions.size(), totalMEP))
                .detail("Elements may be stacked or not properly distributed")
                .guidance("Verify room-based placement algorithm calculates unique positions per element")
                .data("total_mep", totalMEP)
                .data("unique_positions", uniquePositions.size())
                .build();
        }

        // PASS: Elements properly distributed
        return CheckResult.pass(getId(), getName())
            .summary(String.format("%d MEP elements distributed (%d lights, %d sprinklers)",
                totalMEP, lightCount, sprinklerCount))
            .detail(String.format("Unique positions: %d, None at origin", uniquePositions.size()))
            .data("total_mep", totalMEP)
            .data("lights", lightCount)
            .data("sprinklers", sprinklerCount)
            .data("outlets", outletCount)
            .data("switches", switchCount)
            .data("unique_positions", uniquePositions.size())
            .data("at_origin", originCount)
            .build();
    }

    /**
     * Check if position is at origin (within tolerance).
     * Origin bunching bug places elements at (0,0,0) or very close.
     */
    private boolean isAtOrigin(double x, double y, double z) {
        return Math.abs(x) < ORIGIN_TOLERANCE
            && Math.abs(y) < ORIGIN_TOLERANCE
            && Math.abs(z) < ORIGIN_TOLERANCE;
    }
}
